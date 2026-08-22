package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;

import java.lang.ref.Cleaner;

public final class NativeNoiseCleaner {
    private static final Cleaner CLEANER = Cleaner.create(runnable -> {
        Thread thread = new Thread(runnable, "Beryllium native noise cleanup");
        thread.setDaemon(true);
        return thread;
    });

    private NativeNoiseCleaner() {
    }

    public static Cleaner.Cleanable register(Object owner, int generator) {
        return CLEANER.register(owner, new Cleanup(generator));
    }

    private record Cleanup(int generator) implements Runnable {
        @Override
        public void run() {
            NativeBridge.destroyNoiseGenerator(this.generator);
        }
    }
}
