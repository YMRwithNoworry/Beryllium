package alku.beryllium.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 区块生成性能监控器
 *
 * 追踪：
 * 1. 区块生成耗时（同步/异步）
 * 2. 主线程阻塞时间
 * 3. 队列等待时间
 * 4. 生成速率和吞吐量
 */
public class ChunkGenPerformanceMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger("Beryllium/Monitor");

    private static ChunkGenPerformanceMonitor INSTANCE;

    // 同步生成统计
    private final LongAdder syncGenerationCount = new LongAdder();
    private final LongAdder syncGenerationTimeNanos = new LongAdder();

    // 异步生成统计
    private final LongAdder asyncGenerationCount = new LongAdder();
    private final LongAdder asyncGenerationTimeNanos = new LongAdder();

    // 主线程阻塞统计
    private final LongAdder mainThreadBlockCount = new LongAdder();
    private final LongAdder mainThreadBlockTimeNanos = new LongAdder();

    // 队列等待统计
    private final LongAdder queueWaitCount = new LongAdder();
    private final LongAdder queueWaitTimeNanos = new LongAdder();

    // 峰值统计
    private final AtomicLong maxSyncGenerationNanos = new AtomicLong(0);
    private final AtomicLong maxAsyncGenerationNanos = new AtomicLong(0);
    private final AtomicLong maxMainThreadBlockNanos = new AtomicLong(0);

    // 每 tick 统计（用于 TPS 监控）
    private final AtomicInteger lastTickSyncCount = new AtomicInteger(0);
    private final AtomicInteger lastTickAsyncCount = new AtomicInteger(0);
    private final AtomicLong lastTickMainThreadBlockNanos = new AtomicLong(0);

    // 时间戳
    private volatile long monitorStartTime = System.nanoTime();
    private volatile long lastResetTime = System.nanoTime();

    private ChunkGenPerformanceMonitor() {
    }

    public static synchronized ChunkGenPerformanceMonitor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ChunkGenPerformanceMonitor();
        }
        return INSTANCE;
    }

    /**
     * 记录同步区块生成
     */
    public void recordSyncGeneration(long durationNanos) {
        syncGenerationCount.increment();
        syncGenerationTimeNanos.add(durationNanos);
        lastTickSyncCount.incrementAndGet();

        updateMax(maxSyncGenerationNanos, durationNanos);
    }

    /**
     * 记录异步区块生成
     */
    public void recordAsyncGeneration(long durationNanos) {
        asyncGenerationCount.increment();
        asyncGenerationTimeNanos.add(durationNanos);
        lastTickAsyncCount.incrementAndGet();

        updateMax(maxAsyncGenerationNanos, durationNanos);
    }

    /**
     * 记录主线程阻塞
     */
    public void recordMainThreadBlock(long durationNanos) {
        mainThreadBlockCount.increment();
        mainThreadBlockTimeNanos.add(durationNanos);
        lastTickMainThreadBlockNanos.addAndGet(durationNanos);

        updateMax(maxMainThreadBlockNanos, durationNanos);
    }

    /**
     * 记录队列等待时间
     */
    public void recordQueueWait(long durationNanos) {
        queueWaitCount.increment();
        queueWaitTimeNanos.add(durationNanos);
    }

    /**
     * Tick 结束时重置每 tick 计数器
     */
    public void onTickEnd() {
        lastTickSyncCount.set(0);
        lastTickAsyncCount.set(0);
        lastTickMainThreadBlockNanos.set(0);
    }

    /**
     * 获取性能统计快照
     */
    public PerformanceStats getStats() {
        long now = System.nanoTime();
        long uptimeNanos = now - monitorStartTime;
        long timeSinceResetNanos = now - lastResetTime;

        return new PerformanceStats(
            syncGenerationCount.sum(),
            syncGenerationTimeNanos.sum(),
            asyncGenerationCount.sum(),
            asyncGenerationTimeNanos.sum(),
            mainThreadBlockCount.sum(),
            mainThreadBlockTimeNanos.sum(),
            queueWaitCount.sum(),
            queueWaitTimeNanos.sum(),
            maxSyncGenerationNanos.get(),
            maxAsyncGenerationNanos.get(),
            maxMainThreadBlockNanos.get(),
            lastTickSyncCount.get(),
            lastTickAsyncCount.get(),
            lastTickMainThreadBlockNanos.get(),
            uptimeNanos,
            timeSinceResetNanos
        );
    }

    /**
     * 重置所有统计
     */
    public void reset() {
        syncGenerationCount.reset();
        syncGenerationTimeNanos.reset();
        asyncGenerationCount.reset();
        asyncGenerationTimeNanos.reset();
        mainThreadBlockCount.reset();
        mainThreadBlockTimeNanos.reset();
        queueWaitCount.reset();
        queueWaitTimeNanos.reset();

        maxSyncGenerationNanos.set(0);
        maxAsyncGenerationNanos.set(0);
        maxMainThreadBlockNanos.set(0);

        lastTickSyncCount.set(0);
        lastTickAsyncCount.set(0);
        lastTickMainThreadBlockNanos.set(0);

        lastResetTime = System.nanoTime();

        LOGGER.info("性能监控器已重置");
    }

    private void updateMax(AtomicLong maxValue, long newValue) {
        long current = maxValue.get();
        while (newValue > current) {
            if (maxValue.compareAndSet(current, newValue)) {
                break;
            }
            current = maxValue.get();
        }
    }

    /**
     * 性能统计数据快照
     */
    public static class PerformanceStats {
        private final long syncGenerationCount;
        private final long syncGenerationTimeNanos;
        private final long asyncGenerationCount;
        private final long asyncGenerationTimeNanos;
        private final long mainThreadBlockCount;
        private final long mainThreadBlockTimeNanos;
        private final long queueWaitCount;
        private final long queueWaitTimeNanos;
        private final long maxSyncGenerationNanos;
        private final long maxAsyncGenerationNanos;
        private final long maxMainThreadBlockNanos;
        private final int lastTickSyncCount;
        private final int lastTickAsyncCount;
        private final long lastTickMainThreadBlockNanos;
        private final long uptimeNanos;
        private final long timeSinceResetNanos;

        public PerformanceStats(
            long syncGenerationCount, long syncGenerationTimeNanos,
            long asyncGenerationCount, long asyncGenerationTimeNanos,
            long mainThreadBlockCount, long mainThreadBlockTimeNanos,
            long queueWaitCount, long queueWaitTimeNanos,
            long maxSyncGenerationNanos, long maxAsyncGenerationNanos,
            long maxMainThreadBlockNanos,
            int lastTickSyncCount, int lastTickAsyncCount,
            long lastTickMainThreadBlockNanos,
            long uptimeNanos, long timeSinceResetNanos
        ) {
            this.syncGenerationCount = syncGenerationCount;
            this.syncGenerationTimeNanos = syncGenerationTimeNanos;
            this.asyncGenerationCount = asyncGenerationCount;
            this.asyncGenerationTimeNanos = asyncGenerationTimeNanos;
            this.mainThreadBlockCount = mainThreadBlockCount;
            this.mainThreadBlockTimeNanos = mainThreadBlockTimeNanos;
            this.queueWaitCount = queueWaitCount;
            this.queueWaitTimeNanos = queueWaitTimeNanos;
            this.maxSyncGenerationNanos = maxSyncGenerationNanos;
            this.maxAsyncGenerationNanos = maxAsyncGenerationNanos;
            this.maxMainThreadBlockNanos = maxMainThreadBlockNanos;
            this.lastTickSyncCount = lastTickSyncCount;
            this.lastTickAsyncCount = lastTickAsyncCount;
            this.lastTickMainThreadBlockNanos = lastTickMainThreadBlockNanos;
            this.uptimeNanos = uptimeNanos;
            this.timeSinceResetNanos = timeSinceResetNanos;
        }

        public long getTotalGenerationCount() {
            return syncGenerationCount + asyncGenerationCount;
        }

        public long getSyncGenerationCount() {
            return syncGenerationCount;
        }

        public long getAsyncGenerationCount() {
            return asyncGenerationCount;
        }

        public double getAvgSyncGenerationMs() {
            return syncGenerationCount > 0
                ? (syncGenerationTimeNanos / (double) syncGenerationCount) / 1_000_000.0
                : 0.0;
        }

        public double getAvgAsyncGenerationMs() {
            return asyncGenerationCount > 0
                ? (asyncGenerationTimeNanos / (double) asyncGenerationCount) / 1_000_000.0
                : 0.0;
        }

        public double getMaxSyncGenerationMs() {
            return maxSyncGenerationNanos / 1_000_000.0;
        }

        public double getMaxAsyncGenerationMs() {
            return maxAsyncGenerationNanos / 1_000_000.0;
        }

        public long getMainThreadBlockCount() {
            return mainThreadBlockCount;
        }

        public double getTotalMainThreadBlockMs() {
            return mainThreadBlockTimeNanos / 1_000_000.0;
        }

        public double getAvgMainThreadBlockMs() {
            return mainThreadBlockCount > 0
                ? (mainThreadBlockTimeNanos / (double) mainThreadBlockCount) / 1_000_000.0
                : 0.0;
        }

        public double getMaxMainThreadBlockMs() {
            return maxMainThreadBlockNanos / 1_000_000.0;
        }

        public double getAvgQueueWaitMs() {
            return queueWaitCount > 0
                ? (queueWaitTimeNanos / (double) queueWaitCount) / 1_000_000.0
                : 0.0;
        }

        public int getLastTickSyncCount() {
            return lastTickSyncCount;
        }

        public int getLastTickAsyncCount() {
            return lastTickAsyncCount;
        }

        public double getLastTickMainThreadBlockMs() {
            return lastTickMainThreadBlockNanos / 1_000_000.0;
        }

        public double getGenerationRatePerSecond() {
            double seconds = timeSinceResetNanos / 1_000_000_000.0;
            return seconds > 0 ? getTotalGenerationCount() / seconds : 0.0;
        }

        public double getAsyncRatio() {
            long total = getTotalGenerationCount();
            return total > 0 ? asyncGenerationCount / (double) total : 0.0;
        }

        public double getUptimeSeconds() {
            return uptimeNanos / 1_000_000_000.0;
        }
    }
}
