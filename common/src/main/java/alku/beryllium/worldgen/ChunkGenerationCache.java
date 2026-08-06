package alku.beryllium.worldgen;

import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU 缓存已生成的噪声区块数据
 *
 * 工作原理：
 * 1. 使用 LinkedHashMap 实现 LRU 淘汰策略
 * 2. 缓存 NoiseChunk 的密度插值数据
 * 3. 读写锁保证线程安全
 * 4. 自动淘汰最久未使用的缓存项
 */
public class ChunkGenerationCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("Beryllium/ChunkCache");

    private static final int DEFAULT_CACHE_SIZE = 256;
    private static final int MAX_CACHE_SIZE = 2048;

    private static ChunkGenerationCache INSTANCE;

    private final int maxCacheSize;
    private final Map<Long, CachedNoiseData> cache;
    private final ReadWriteLock lock;

    private long hits;
    private long misses;

    private ChunkGenerationCache(int maxSize) {
        this.maxCacheSize = Math.min(maxSize, MAX_CACHE_SIZE);
        this.lock = new ReentrantReadWriteLock();

        // LinkedHashMap 的 accessOrder = true 实现 LRU
        this.cache = new LinkedHashMap<Long, CachedNoiseData>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, CachedNoiseData> eldest) {
                return size() > ChunkGenerationCache.this.maxCacheSize;
            }
        };

        LOGGER.info("区块生成缓存已初始化，最大容量: {}", maxCacheSize);
    }

    public static synchronized ChunkGenerationCache getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ChunkGenerationCache(DEFAULT_CACHE_SIZE);
        }
        return INSTANCE;
    }

    public static synchronized ChunkGenerationCache getInstance(int maxSize) {
        if (INSTANCE == null) {
            INSTANCE = new ChunkGenerationCache(maxSize);
        }
        return INSTANCE;
    }

    /**
     * 获取缓存的噪声数据
     */
    public CachedNoiseData get(ChunkPos pos) {
        long key = pos.toLong();

        lock.readLock().lock();
        try {
            CachedNoiseData data = cache.get(key);
            if (data != null) {
                hits++;
                return data;
            }
            misses++;
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 放入缓存
     */
    public void put(ChunkPos pos, CachedNoiseData data) {
        long key = pos.toLong();

        lock.writeLock().lock();
        try {
            cache.put(key, data);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 检查是否存在缓存
     */
    public boolean containsKey(ChunkPos pos) {
        lock.readLock().lock();
        try {
            return cache.containsKey(pos.toLong());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 清空缓存
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            hits = 0;
            misses = 0;
            LOGGER.info("区块生成缓存已清空");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取当前缓存大小
     */
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取缓存命中率
     */
    public double getHitRate() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        lock.readLock().lock();
        try {
            return new CacheStats(cache.size(), maxCacheSize, hits, misses);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 缓存的噪声数据
     */
    public static class CachedNoiseData {
        private final double[] densityValues;
        private final int interpolatorCount;
        private final int cellWidth;
        private final int cellHeight;
        private final int cellCountY;
        private final int cellCountXZ;
        private final long timestamp;

        public CachedNoiseData(
            double[] densityValues,
            int interpolatorCount,
            int cellWidth,
            int cellHeight,
            int cellCountY,
            int cellCountXZ
        ) {
            this.densityValues = densityValues.clone();
            this.interpolatorCount = interpolatorCount;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.cellCountY = cellCountY;
            this.cellCountXZ = cellCountXZ;
            this.timestamp = System.currentTimeMillis();
        }

        public double[] getDensityValues() {
            return densityValues;
        }

        public int getInterpolatorCount() {
            return interpolatorCount;
        }

        public int getCellWidth() {
            return cellWidth;
        }

        public int getCellHeight() {
            return cellHeight;
        }

        public int getCellCountY() {
            return cellCountY;
        }

        public int getCellCountXZ() {
            return cellCountXZ;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isCompatible(int interpolators, int width, int height, int countY, int countXZ) {
            return this.interpolatorCount == interpolators
                && this.cellWidth == width
                && this.cellHeight == height
                && this.cellCountY == countY
                && this.cellCountXZ == countXZ;
        }
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final int currentSize;
        private final int maxSize;
        private final long hits;
        private final long misses;

        public CacheStats(int currentSize, int maxSize, long hits, long misses) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.hits = hits;
            this.misses = misses;
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public long getHits() {
            return hits;
        }

        public long getMisses() {
            return misses;
        }

        public double getHitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }

        @Override
        public String toString() {
            return String.format(
                "ChunkCache[size=%d/%d, hits=%d, misses=%d, hitRate=%.2f%%]",
                currentSize, maxSize, hits, misses, getHitRate() * 100
            );
        }
    }
}
