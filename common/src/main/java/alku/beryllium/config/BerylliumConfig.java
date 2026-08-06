package alku.beryllium.config;

/**
 * Beryllium 性能配置
 */
public class BerylliumConfig {
    private static boolean asyncChunkGenEnabled = true;
    private static int syncGenerationRadius = 3;
    private static int maxPendingTasks = 256;
    private static int workerThreads = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
    
    public static boolean isAsyncChunkGenEnabled() {
        return asyncChunkGenEnabled;
    }
    
    public static void setAsyncChunkGenEnabled(boolean enabled) {
        asyncChunkGenEnabled = enabled;
    }
    
    public static int getSyncGenerationRadius() {
        return syncGenerationRadius;
    }
    
    public static void setSyncGenerationRadius(int radius) {
        syncGenerationRadius = Math.max(1, Math.min(10, radius));
    }
    
    public static int getMaxPendingTasks() {
        return maxPendingTasks;
    }
    
    public static void setMaxPendingTasks(int tasks) {
        maxPendingTasks = Math.max(64, Math.min(1024, tasks));
    }
    
    public static int getWorkerThreads() {
        return workerThreads;
    }
    
    public static void setWorkerThreads(int threads) {
        workerThreads = Math.max(1, Math.min(16, threads));
    }
}
