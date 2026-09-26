import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import makeWASocket, {
  Browsers,
  DisconnectReason,
  useMultiFileAuthState
} from '@whiskeysockets/baileys'
import pino from 'pino'

// Jangan biarkan error async Baileys mematikan embedded Node / proses APK.
process.on('uncaughtException', err => {
  console.error('[uncaughtException]', err)
})
process.on('unhandledRejection', err => {
  console.error('[unhandledRejection]', err)
})

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

function arg(name, fallback) {
  const i = process.argv.indexOf(name)
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

const DATA_DIR = arg('--data-dir', path.join(__dirname, 'data'))
const PORT = Number(arg('--port', '8765'))
const AUTH_DIR = path.join(DATA_DIR, 'auth')
const JOBS_FILE = path.join(DATA_DIR, 'jobs.json')
fs.mkdirSync(DATA_DIR, { recursive: true })
fs.mkdirSync(AUTH_DIR, { recursive: true })

let sock = null
let connected = false
let registered = false
let pairingReady = false
let connecting = false
let pairingInProgress = false
let currentCreds = null
const conversations = new Map()
const logger = pino({ level: 'silent' })

function loadJobs() {
  try { return JSON.parse(fs.readFileSync(JOBS_FILE, 'utf8')) } catch { return [] }
}
function saveJobs(jobs) {
  fs.writeFileSync(JOBS_FILE, JSON.stringify(jobs, null, 2))
}
function addJob(data) {
  const jobs = loadJobs()
  const id = `${Date.now()}-${Math.floor(Math.random() * 900 + 100)}`
  const job = {
    id,
    jenis: data.jenis || 'Surat Keterangan',
    nama: data.nama || '-',
    nik: data.nik || '-',
    alamat: data.alamat || '-',
    keperluan: data.keperluan || '-',
    sumber: data.sumber || 'WhatsApp',
    status: 'PENDING',
    createdAt: new Date().toISOString()
  }
  jobs.unshift(job)
  saveJobs(jobs)
  return job
}
function updateJob(id, patch) {
  const jobs = loadJobs()
  const i = jobs.findIndex(j => j.id === id)
  if (i < 0) return null
  jobs[i] = { ...jobs[i], ...patch, updatedAt: new Date().toISOString() }
  saveJobs(jobs)
  return jobs[i]
}

function textOf(m) {
  const msg = m.message || {}
  return (
    msg.conversation ||
    msg.extendedTextMessage?.text ||
    msg.imageMessage?.caption ||
    msg.videoMessage?.caption ||
    msg.buttonsResponseMessage?.selectedDisplayText ||
    msg.listResponseMessage?.title ||
    ''
  ).trim()
}

async function reply(jid, text) {
  if (!sock) return
  await sock.sendMessage(jid, { text })
}

async function handleMessage(jid, rawText) {
  const text = rawText.trim()
  const upper = text.toUpperCase()

  if (['SURAT', '/SURAT', 'BUAT SURAT', 'BUATSURAT'].includes(upper)) {
    conversations.set(jid, { step: 'jenis' })
    return reply(jid,
`📄 *BOT PEMBUATAN SURAT*

Pilih jenis surat:
1. Surat Keterangan

Balas angka *1*.
Ketik *BATAL* kapan saja untuk membatalkan.`)
  }

  if (upper === 'BATAL') {
    conversations.delete(jid)
    return reply(jid, '❌ Pembuatan surat dibatalkan.')
  }

  const state = conversations.get(jid)
  if (!state) return

  if (state.step === 'jenis') {
    if (text !== '1') return reply(jid, 'Balas *1* untuk Surat Keterangan.')
    state.jenis = 'Surat Keterangan'
    state.step = 'nama'
    return reply(jid, 'Masukkan *nama lengkap*:')
  }

  if (state.step === 'nama') {
    state.nama = text
    state.step = 'nik'
    return reply(jid, 'Masukkan *NIK*:')
  }

  if (state.step === 'nik') {
    state.nik = text
    state.step = 'alamat'
    return reply(jid, 'Masukkan *alamat*:')
  }

  if (state.step === 'alamat') {
    state.alamat = text
    state.step = 'keperluan'
    return reply(jid, 'Surat ini untuk *keperluan apa*?')
  }

  if (state.step === 'keperluan') {
    state.keperluan = text
    state.step = 'confirm'
    return reply(jid,
`✅ *PERIKSA DATA*

Jenis: ${state.jenis}
Nama: ${state.nama}
NIK: ${state.nik}
Alamat: ${state.alamat}
Keperluan: ${state.keperluan}

Balas *YA* untuk membuat surat atau *BATAL*.`)
  }

  if (state.step === 'confirm') {
    if (upper !== 'YA') return reply(jid, 'Balas *YA* jika data benar, atau *BATAL*.')
    const job = addJob({ ...state, sumber: jid })
    conversations.delete(jid)
    return reply(jid,
`✅ Data surat masuk ke antrean.
ID: *${job.id}*

Petugas dapat membuka APK Bot Surat untuk preview dan print.`)
  }
}

async function connectWhatsApp() {
  if (connecting) return
  connecting = true
  try {
    const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR)
    currentCreds = state.creds
    registered = !!state.creds.registered

    sock = makeWASocket({
      auth: state,
      logger,
      printQRInTerminal: false,
      browser: Browsers.ubuntu('Chrome'),
      markOnlineOnConnect: false,
      syncFullHistory: false,
      shouldSyncHistoryMessage: () => false
    })

    sock.ev.on('creds.update', async () => {
      await saveCreds()
      currentCreds = state.creds
      registered = !!state.creds.registered
    })

    sock.ev.on('connection.update', update => {
      const { connection, lastDisconnect, qr } = update
      if (connection === 'connecting' || qr) pairingReady = true
      if (connection === 'open') {
        connected = true
        registered = true
        pairingReady = false
      }
      if (connection === 'close') {
        connected = false
        const code = lastDisconnect?.error?.output?.statusCode ?? lastDisconnect?.error?.statusCode
        const loggedOut = code === DisconnectReason.loggedOut
        if (!loggedOut) {
          setTimeout(() => {
            connecting = false
            connectWhatsApp().catch(console.error)
          }, 1500)
        }
      }
    })

    sock.ev.on('messages.upsert', async ({ messages, type }) => {
      if (type !== 'notify') return
      for (const m of messages) {
        if (!m.message || m.key.fromMe) continue
        const jid = m.key.remoteJid || ''
        if (!jid || jid.endsWith('@g.us') || jid === 'status@broadcast') continue
        const text = textOf(m)
        if (text) {
          try { await handleMessage(jid, text) } catch (e) { console.error(e) }
        }
      }
    })
  } catch (e) {
    console.error('connectWhatsApp error', e)
    setTimeout(() => {
      connecting = false
      connectWhatsApp().catch(console.error)
    }, 3000)
  }
}

async function requestPairing(phone) {
  phone = String(phone || '').replace(/\D/g, '')
  if (phone.length < 8) throw new Error('Nomor tidak valid. Gunakan format 62812...')
  if (registered || currentCreds?.registered) return { alreadyRegistered: true }
  if (!sock) throw new Error('Socket WhatsApp belum siap. Tekan NYALAKAN BOT lalu coba lagi.')
  if (pairingInProgress) throw new Error('Permintaan pairing sedang diproses. Tunggu beberapa detik.')

  pairingInProgress = true
  try {
    // Beri WebSocket waktu untuk benar-benar siap.
    const deadline = Date.now() + 20000
    while (!pairingReady && Date.now() < deadline) {
      await new Promise(r => setTimeout(r, 250))
    }
    await new Promise(r => setTimeout(r, 1200))

    const code = await sock.requestPairingCode(phone)
    return { code, alreadyRegistered: false }
  } finally {
    // Cegah klik ganda/overlapping pairing state.
    setTimeout(() => { pairingInProgress = false }, 3000)
  }
}

function esc(v) {
  return String(v ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function indoDate(date = new Date()) {
  const months = ['Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember']
  return `${date.getDate()} ${months[date.getMonth()]} ${date.getFullYear()}`
}

function renderLetter(job) {
  const tpl = fs.readFileSync(path.join(__dirname, 'templates', 'surat-keterangan.html'), 'utf8')
  const values = {
    nomor: job.nomor || `B-${String(job.id).slice(-6)}/Kua.17.01/IX/${new Date().getFullYear()}`,
    nama: job.nama,
    nik: job.nik,
    alamat: job.alamat,
    keperluan: job.keperluan,
    tanggal: indoDate()
  }
  let html = tpl
  for (const [k, v] of Object.entries(values)) {
    html = html.replaceAll(`{{${k}}}`, esc(v))
  }
  return html
}

function json(res, code, body) {
  const data = JSON.stringify(body)
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(data)
  })
  res.end(data)
}

const server = http.createServer(async (req, res) => {
  try {
    const u = new URL(req.url, `http://127.0.0.1:${PORT}`)

    if (req.method === 'GET' && u.pathname === '/status') {
      const jobs = loadJobs()
      return json(res, 200, {
        ok: true,
        connected,
        registered: !!currentCreds?.registered || registered,
        pairingReady,
        pending: jobs.filter(j => j.status === 'PENDING').length,
        node: process.version
      })
    }

    if (req.method === 'GET' && u.pathname === '/pair') {
      const result = await requestPairing(u.searchParams.get('phone'))
      return json(res, 200, result)
    }

    if (req.method === 'GET' && u.pathname === '/jobs') {
      return json(res, 200, loadJobs())
    }

    if (req.method === 'POST' && u.pathname === '/demo') {
      const job = addJob({
        jenis: 'Surat Keterangan',
        nama: 'AHMAD FAUZI',
        nik: '6301000000000000',
        alamat: 'Bati-Bati, Tanah Laut',
        keperluan: 'Contoh pengujian print dari APK',
        sumber: 'DEMO'
      })
      return json(res, 201, job)
    }

    const htmlMatch = u.pathname.match(/^\/jobs\/([^/]+)\/html$/)
    if (req.method === 'GET' && htmlMatch) {
      const job = loadJobs().find(j => j.id === htmlMatch[1])
      if (!job) return json(res, 404, { error: 'Surat tidak ditemukan' })
      const html = renderLetter(job)
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
      return res.end(html)
    }

    const doneMatch = u.pathname.match(/^\/jobs\/([^/]+)\/done$/)
    if (req.method === 'POST' && doneMatch) {
      const job = updateJob(doneMatch[1], { status: 'PRINTED' })
      if (!job) return json(res, 404, { error: 'Surat tidak ditemukan' })
      return json(res, 200, job)
    }

    return json(res, 404, { error: 'Not found' })
  } catch (e) {
    console.error(e)
    return json(res, 500, { error: e?.message || String(e) })
  }
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Bot Surat local API: http://127.0.0.1:${PORT}`)
})

connectWhatsApp().catch(console.error)
