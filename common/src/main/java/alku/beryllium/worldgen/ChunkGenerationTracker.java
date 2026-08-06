package alku.beryllium.worldgen;

import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪区块生成进度，避免重复提交相同区块任务
 *
 * 工作原理：
 * 1. 使用 ConcurrentHashMap 的 Set 视图追踪正在生成的区块
 * 2. 线程安全，支持多线程并发访问
 * 3. 自动清理已完成的区块
 */
public class ChunkGenerationTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("Beryllium/ChunkTracker");

    private static ChunkGenerationTracker INSTANCE;

    private final Set<Long> generatingChunks;

    private ChunkGenerationTracker() {
        this.generatingChunks = ConcurrentHashMap.newKeySet();
        LOGGER.info("区块生成追踪器已初始化");
    }

    public static synchronized ChunkGenerationTracker getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ChunkGenerationTracker();
        }
        return INSTANCE;
    }

    /**
     * 检查区块是否正在生成
     */
    public boolean isGenerating(ChunkPos pos) {
        return generatingChunks.contains(pos.toLong());
    }

    /**
     * 标记区块为正在生成
     *
     * @return true 如果成功标记（之前未在生成），false 如果已经在生成
     */
    public boolean markGenerating(ChunkPos pos) {
        long key = pos.toLong();
        boolean added = generatingChunks.add(key);

        if (added) {
            LOGGER.debug("开始生成区块: {}", pos);
        } else {
            LOGGER.debug("区块已在生成队列中: {}", pos);
        }

        return added;
    }

    /**
     * 标记区块生成完成
     */
    public void markCompleted(ChunkPos pos) {
        long key = pos.toLong();
        boolean removed = generatingChunks.remove(key);

        if (removed) {
            LOGGER.debug("区块生成完成: {}", pos);
        }
    }

    /**
     * 获取当前正在生成的区块数量
     */
    public int getGeneratingCount() {
        return generatingChunks.size();
    }

    /**
     * 清空所有追踪记录
     */
    public void clear() {
        int count = generatingChunks.size();
        generatingChunks.clear();
        LOGGER.info("已清空 {} 个区块生成追踪记录", count);
    }

    /**
     * 获取追踪统计信息
     */
    public TrackerStats getStats() {
        return new TrackerStats(generatingChunks.size());
    }

    /**
     * 追踪统计信息
     */
    public static class TrackerStats {
        private final int generatingCount;

        public TrackerStats(int generatingCount) {
            this.generatingCount = generatingCount;
        }

        public int getGeneratingCount() {
            return generatingCount;
        }

        @Override
        public String toString() {
            return String.format("ChunkTracker[generating=%d]", generatingCount);
        }
    }
}
