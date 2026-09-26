package id.kua.batibati.botsurat;

import java.util.concurrent.atomic.AtomicBoolean;

public final class NodeBridge {
    private NodeBridge() {}

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    static {
        System.loadLibrary("node");
        System.loadLibrary("native-lib");
    }

    public static native int startNodeWithArguments(String[] arguments);

    public static boolean markStarted() {
        return STARTED.compareAndSet(false, true);
    }

    public static boolean isStarted() {
        return STARTED.get();
    }
}
