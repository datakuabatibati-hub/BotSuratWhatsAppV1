package id.kua.batibati.botsurat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.File;

public class BotService extends Service {
    private static final String CHANNEL_ID = "bot_surat_service";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1001,
                new android.app.Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("Bot Surat WhatsApp aktif")
                        .setContentText("Baileys berjalan di dalam APK")
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setOngoing(true)
                        .build());

        if (NodeBridge.markStarted()) {
            new Thread(() -> {
                try {
                    File project = AssetInstaller.ensureInstalled(getApplicationContext());
                    File entry = new File(project, "index.mjs");
                    File data = new File(getFilesDir(), "bot-data");
                    if (!data.exists()) data.mkdirs();
                    NodeBridge.startNodeWithArguments(new String[]{
                            "node",
                            entry.getAbsolutePath(),
                            "--data-dir",
                            data.getAbsolutePath(),
                            "--port",
                            "8765"
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                    updateNotification("Bot gagal: " + t.getClass().getSimpleName());
                }
            }, "EmbeddedNode").start();
        }
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bot Surat WhatsApp",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Menjaga koneksi Baileys tetap berjalan");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(1001,
                new android.app.Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("Bot Surat WhatsApp")
                        .setContentText(text)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setOngoing(true)
                        .build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
