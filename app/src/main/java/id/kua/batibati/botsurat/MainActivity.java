package id.kua.batibati.botsurat;

import android.Manifest;
import android.app.Activity;
import android.print.PrintManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private TextView tvStatus;
    private TextView tvPairing;
    private EditText etPhone;
    private LinearLayout jobsContainer;
    private WebView webPreview;
    private Button btnPrint;
    private Button btnDone;
    private String selectedJobId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvPairing = findViewById(R.id.tvPairing);
        etPhone = findViewById(R.id.etPhone);
        jobsContainer = findViewById(R.id.jobsContainer);
        webPreview = findViewById(R.id.webPreview);
        btnPrint = findViewById(R.id.btnPrint);
        btnDone = findViewById(R.id.btnDone);

        findViewById(R.id.btnStart).setOnClickListener(v -> startBot());
        findViewById(R.id.btnPair).setOnClickListener(v -> requestPairing());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> refreshJobs());
        findViewById(R.id.btnDemo).setOnClickListener(v -> createDemo());
        btnPrint.setOnClickListener(v -> printCurrent());
        btnDone.setOnClickListener(v -> markDone());

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        startBot();
    }

    private void startBot() {
        Intent i = new Intent(this, BotService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        tvStatus.setText("Status: menyalakan runtime Node/Baileys...");
        webPreview.postDelayed(this::refreshStatus, 2500);
    }

    private void refreshStatus() {
        async(() -> LocalApi.get("/status"), result -> {
            try {
                JSONObject j = new JSONObject(result);
                tvStatus.setText("WhatsApp: " + (j.optBoolean("connected") ? "🟢 TERHUBUNG" : "🟠 BELUM TERHUBUNG")
                        + "\nRegistered: " + j.optBoolean("registered")
                        + "\nSurat pending: " + j.optInt("pending"));
            } catch (Exception e) {
                tvStatus.setText("Bot berjalan, tapi status belum terbaca: " + e.getMessage());
            }
        });
    }

    private void requestPairing() {
        String phone = etPhone.getText().toString().replaceAll("[^0-9]", "");
        if (phone.length() < 8) {
            Toast.makeText(this, "Masukkan nomor dengan kode negara, contoh 62812...", Toast.LENGTH_LONG).show();
            return;
        }
        tvPairing.setText("Meminta pairing code...");
        async(() -> LocalApi.get(LocalApi.pairPath(phone)), result -> {
            try {
                JSONObject j = new JSONObject(result);
                if (j.optBoolean("alreadyRegistered")) {
                    tvPairing.setText("WhatsApp sudah terhubung.");
                } else {
                    String code = j.optString("code", "-");
                    tvPairing.setText("KODE: " + formatCode(code)
                            + "\n\nWhatsApp → Perangkat tertaut → Tautkan perangkat → Tautkan dengan nomor telepon");
                }
            } catch (Exception e) {
                tvPairing.setText("Gagal membaca kode: " + e.getMessage());
            }
        });
    }

    private String formatCode(String code) {
        String clean = code.replaceAll("[^A-Za-z0-9]", "");
        if (clean.length() == 8) return clean.substring(0, 4) + "-" + clean.substring(4);
        return code;
    }

    private void createDemo() {
        async(() -> LocalApi.post("/demo", "{}"), r -> refreshJobs());
    }

    private void refreshJobs() {
        async(() -> LocalApi.get("/jobs"), result -> {
            jobsContainer.removeAllViews();
            try {
                JSONArray arr = new JSONArray(result);
                if (arr.length() == 0) {
                    TextView empty = new TextView(this);
                    empty.setText("Belum ada surat. Kirim 'SURAT' ke nomor bot atau tekan BUAT DEMO.");
                    empty.setPadding(8, 16, 8, 16);
                    jobsContainer.addView(empty);
                    return;
                }
                for (int x = 0; x < arr.length(); x++) {
                    JSONObject job = arr.getJSONObject(x);
                    Button b = new Button(this);
                    String id = job.getString("id");
                    b.setText(job.optString("nama") + "\n" + job.optString("jenis") + " • " + job.optString("status"));
                    b.setAllCaps(false);
                    b.setOnClickListener(v -> preview(id));
                    jobsContainer.addView(b, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            } catch (Exception e) {
                Toast.makeText(this, "Gagal membaca antrean: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        refreshStatus();
    }

    private void preview(String id) {
        selectedJobId = id;
        webPreview.getSettings().setJavaScriptEnabled(false);
        webPreview.loadUrl(LocalApi.BASE + "/jobs/" + id + "/html");
        btnPrint.setEnabled(true);
        btnDone.setEnabled(true);
    }

    private void printCurrent() {
        if (selectedJobId == null) return;
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        String jobName = "Surat-" + selectedJobId;
        pm.print(jobName, webPreview.createPrintDocumentAdapter(jobName), null);
        Toast.makeText(this, "Dialog print dibuka. Setelah printer selesai, tekan TANDAI SUDAH SELESAI.", Toast.LENGTH_LONG).show();
    }

    private void markDone() {
        if (selectedJobId == null) return;
        async(() -> LocalApi.post("/jobs/" + selectedJobId + "/done", "{}"), r -> {
            Toast.makeText(this, "Surat ditandai PRINTED.", Toast.LENGTH_SHORT).show();
            refreshJobs();
        });
    }

    private interface Work { String run() throws Exception; }
    private interface Done { void accept(String result); }

    private void async(Work work, Done done) {
        new Thread(() -> {
            try {
                String result = work.run();
                runOnUiThread(() -> done.accept(result));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
