package id.kua.batibati.botsurat;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class AssetInstaller {
    private AssetInstaller() {}

    public static File ensureInstalled(Context context) throws Exception {
        File root = new File(context.getFilesDir(), "nodejs-project");
        File marker = new File(root, ".installed-v1");
        if (marker.exists()) return root;

        deleteRecursive(root);
        if (!root.mkdirs() && !root.exists()) {
            throw new IllegalStateException("Tidak bisa membuat folder Node.js");
        }

        try (InputStream raw = context.getAssets().open("nodejs-project.zip");
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zis.getNextEntry()) != null) {
                File out = safeFile(root, entry.getName());
                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.exists()) {
                        throw new IllegalStateException("Gagal membuat " + out);
                    }
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IllegalStateException("Gagal membuat " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buffer)) > 0) fos.write(buffer, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }

        if (!marker.createNewFile() && !marker.exists()) {
            throw new IllegalStateException("Gagal membuat marker instalasi");
        }
        return root;
    }

    private static File safeFile(File root, String name) throws Exception {
        File target = new File(root, name);
        String rootPath = root.getCanonicalPath() + File.separator;
        if (!target.getCanonicalPath().startsWith(rootPath)) {
            throw new SecurityException("ZIP path tidak aman: " + name);
        }
        return target;
    }

    private static void deleteRecursive(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
