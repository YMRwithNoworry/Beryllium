package alku.beryllium.worldgen;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据玩家移动方向预测需要的区块
 *
 * 工作原理：
 * 1. 追踪玩家历史位置，计算移动速度和方向
 * 2. 根据移动向量预测未来需要的区块
 * 3. 返回按优先级排序的预取区块列表
 * 4. 支持多玩家独立预测
 */
public class ChunkGenerationPredictor {
    private static final Logger LOGGER = LoggerFactory.getLogger("Beryllium/ChunkPredictor");

    private static ChunkGenerationPredictor INSTANCE;

    // 预测参数
    private static final int HISTORY_SIZE = 5;
    private static final double MIN_SPEED_THRESHOLD = 0.1; // 每 tick 移动距离
    private static final int PREDICTION_RADIUS = 4; // 预测区块半径
    private static final int MAX_PREDICTIONS = 64; // 最大预测区块数

    private final Map<UUID, PlayerMovementHistory> playerHistories;

    private ChunkGenerationPredictor() {
        this.playerHistories = new ConcurrentHashMap<>();
        LOGGER.info("区块预测器已初始化");
    }

    public static synchronized ChunkGenerationPredictor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ChunkGenerationPredictor();
        }
        return INSTANCE;
    }

    /**
     * 更新玩家位置
     */
    public void updatePlayerPosition(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Vec3 position = player.position();
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());

        PlayerMovementHistory history = playerHistories.computeIfAbsent(
            playerId,
            id -> new PlayerMovementHistory()
        );

        history.addPosition(position, chunkPos);
    }

    /**
     * 预测玩家需要的区块
     */
    public List<PredictedChunk> predictChunks(ServerPlayer player, int renderDistance) {
        UUID playerId = player.getUUID();
        PlayerMovementHistory history = playerHistories.get(playerId);

        if (history == null || !history.hasEnoughData()) {
            // 没有足够的历史数据，返回当前位置周围的区块
            return predictStaticChunks(player, renderDistance);
        }

        Vec3 velocity = history.getVelocity();
        double speed = velocity.length();

        // 如果玩家基本静止，使用静态预测
        if (speed < MIN_SPEED_THRESHOLD) {
            return predictStaticChunks(player, renderDistance);
        }

        // 动态预测：根据移动方向
        return predictDynamicChunks(player, velocity, speed, renderDistance);
    }

    /**
     * 静态预测：玩家静止时，预测周围区块
     */
    private List<PredictedChunk> predictStaticChunks(ServerPlayer player, int renderDistance) {
        ChunkPos playerChunk = new ChunkPos(player.blockPosition());
        List<PredictedChunk> predictions = new ArrayList<>();

        int radius = Math.min(PREDICTION_RADIUS, renderDistance / 2);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue; // 跳过玩家当前区块
                }

                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared <= radius * radius) {
                    ChunkPos predictedPos = new ChunkPos(
                        playerChunk.x + dx,
                        playerChunk.z + dz
                    );

                    int priority = distanceSquared * 100; // 距离越近优先级越高（数值越小）
                    predictions.add(new PredictedChunk(predictedPos, priority, PredictionType.STATIC));
                }
            }
        }

        predictions.sort(Comparator.comparingInt(PredictedChunk::getPriority));
        return predictions.subList(0, Math.min(predictions.size(), MAX_PREDICTIONS));
    }

    /**
     * 动态预测：根据玩家移动方向预测
     */
    private List<PredictedChunk> predictDynamicChunks(
        ServerPlayer player,
        Vec3 velocity,
        double speed,
        int renderDistance
    ) {
        ChunkPos playerChunk = new ChunkPos(player.blockPosition());
        List<PredictedChunk> predictions = new ArrayList<>();

        // 归一化速度向量
        Vec3 direction = velocity.normalize();

        // 预测时间跨度（秒）
        double predictionTimeSeconds = Math.min(speed * 2, 5.0);
        int predictionTicks = (int) (predictionTimeSeconds * 20);

        // 预测未来位置
        Vec3 currentPos = player.position();
        Vec3 futurePos = currentPos.add(
            direction.x * speed * predictionTicks,
            0,
            direction.z * speed * predictionTicks
        );

        ChunkPos futureChunk = new ChunkPos(
            (int) Math.floor(futurePos.x / 16),
            (int) Math.floor(futurePos.z / 16)
        );

        // 在移动路径上生成预测区块
        int radius = Math.min(PREDICTION_RADIUS, renderDistance / 2);

        // 计算从当前位置到预测位置的区块路径
        int deltaX = futureChunk.x - playerChunk.x;
        int deltaZ = futureChunk.z - playerChunk.z;
        int steps = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        Set<ChunkPos> addedChunks = new HashSet<>();

        // 沿着路径预测
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            int midX = playerChunk.x + (int) (deltaX * t);
            int midZ = playerChunk.z + (int) (deltaZ * t);

            // 在路径点周围生成区块
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distanceSquared = dx * dx + dz * dz;
                    if (distanceSquared > radius * radius) {
                        continue;
                    }

                    ChunkPos predictedPos = new ChunkPos(midX + dx, midZ + dz);

                    if (!addedChunks.add(predictedPos)) {
                        continue; // 已添加过
                    }

                    // 优先级：路径上的点优先级更高
                    int priority = (int) (step * 10 + distanceSquared * 5);
                    predictions.add(new PredictedChunk(predictedPos, priority, PredictionType.DYNAMIC));
                }
            }
        }

        predictions.sort(Comparator.comparingInt(PredictedChunk::getPriority));
        return predictions.subList(0, Math.min(predictions.size(), MAX_PREDICTIONS));
    }

    /**
     * 清理玩家历史数据
     */
    public void removePlayer(UUID playerId) {
        playerHistories.remove(playerId);
    }

    /**
     * 清理所有历史数据
     */
    public void clearAll() {
        playerHistories.clear();
        LOGGER.info("区块预测器历史数据已清空");
    }

    /**
     * 玩家移动历史
     */
    private static class PlayerMovementHistory {
        private final Deque<PositionSnapshot> history;

        public PlayerMovementHistory() {
            this.history = new ArrayDeque<>(HISTORY_SIZE);
        }

        public void addPosition(Vec3 position, ChunkPos chunkPos) {
            if (history.size() >= HISTORY_SIZE) {
                history.removeFirst();
            }
            history.addLast(new PositionSnapshot(position, chunkPos, System.currentTimeMillis()));
        }

        public boolean hasEnoughData() {
            return history.size() >= 2;
        }

        public Vec3 getVelocity() {
            if (history.size() < 2) {
                return Vec3.ZERO;
            }

            PositionSnapshot newest = history.getLast();
            PositionSnapshot oldest = history.getFirst();

            long timeDelta = newest.timestamp - oldest.timestamp;
            if (timeDelta <= 0) {
                return Vec3.ZERO;
            }

            // 计算平均速度（单位：方块/tick）
            double deltaX = newest.position.x - oldest.position.x;
            double deltaY = newest.position.y - oldest.position.y;
            double deltaZ = newest.position.z - oldest.position.z;

            double timeInTicks = timeDelta / 50.0; // 毫秒转 tick
            return new Vec3(
                deltaX / timeInTicks,
                deltaY / timeInTicks,
                deltaZ / timeInTicks
            );
        }
    }

    /**
     * 位置快照
     */
    private static class PositionSnapshot {
        private final Vec3 position;
        private final ChunkPos chunkPos;
        private final long timestamp;

        public PositionSnapshot(Vec3 position, ChunkPos chunkPos, long timestamp) {
            this.position = position;
            this.chunkPos = chunkPos;
            this.timestamp = timestamp;
        }
    }

    /**
     * 预测的区块
     */
    public static class PredictedChunk {
        private final ChunkPos pos;
        private final int priority;
        private final PredictionType type;

        public PredictedChunk(ChunkPos pos, int priority, PredictionType type) {
            this.pos = pos;
            this.priority = priority;
            this.type = type;
        }

        public ChunkPos getPos() {
            return pos;
        }

        public int getPriority() {
            return priority;
        }

        public PredictionType getType() {
            return type;
        }
    }

    /**
     * 预测类型
     */
    public enum PredictionType {
        STATIC,  // 静态预测（玩家静止）
        DYNAMIC  // 动态预测（根据移动方向）
    }
}
