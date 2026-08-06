package alku.beryllium.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Beryllium 性能配置
 *
 * 支持：
 * 1. 动态阈值调整（根据 TPS 自适应）
 * 2. 配置文件持久化
 * 3. 运行时配置热更新
 */
public class BerylliumConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Beryllium/Config");
    private static final String CONFIG_FILE = "config/beryllium.properties";

    // 异步区块生成配置
    private static boolean asyncChunkGenEnabled = true;
    private static int syncGenerationRadius = 3;
    private static int maxPendingTasks = 256;
    private static int workerThreads = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);

    // 动态阈值配置
    private static boolean adaptiveThresholdEnabled = true;
    private static double targetTPS = 19.5; // 目标 TPS，低于此值会收紧同步半径
    private static double tpsThresholdLow = 18.0; // TPS 低阈值
    private static double tpsThresholdHigh = 19.8; // TPS 高阈值

    // 自适应参数范围
    private static int minSyncRadius = 2;
    private static int maxSyncRadius = 6;
    private static int minWorkerThreads = 1;
    private static int maxWorkerThreads = 16;

    // 性能监控配置
    private static boolean performanceMonitorEnabled = true;
    private static int monitorReportIntervalTicks = 200; // 10 秒报告一次

    static {
        loadConfig();
    }

    public static boolean isAsyncChunkGenEnabled() {
        return asyncChunkGenEnabled;
    }

    public static void setAsyncChunkGenEnabled(boolean enabled) {
        asyncChunkGenEnabled = enabled;
        LOGGER.info("异步区块生成: {}", enabled ? "启用" : "禁用");
    }

    public static int getSyncGenerationRadius() {
        return syncGenerationRadius;
    }

    public static void setSyncGenerationRadius(int radius) {
        syncGenerationRadius = Math.max(minSyncRadius, Math.min(maxSyncRadius, radius));
        LOGGER.debug("同步生成半径设置为: {}", syncGenerationRadius);
    }

    public static int getMaxPendingTasks() {
        return maxPendingTasks;
    }

    public static void setMaxPendingTasks(int tasks) {
        maxPendingTasks = Math.max(64, Math.min(1024, tasks));
        LOGGER.debug("最大待处理任务数设置为: {}", maxPendingTasks);
    }

    public static int getWorkerThreads() {
        return workerThreads;
    }

    public static void setWorkerThreads(int threads) {
        workerThreads = Math.max(minWorkerThreads, Math.min(maxWorkerThreads, threads));
        LOGGER.info("工作线程数设置为: {}", workerThreads);
    }

    public static boolean isAdaptiveThresholdEnabled() {
        return adaptiveThresholdEnabled;
    }

    public static void setAdaptiveThresholdEnabled(boolean enabled) {
        adaptiveThresholdEnabled = enabled;
        LOGGER.info("自适应阈值调整: {}", enabled ? "启用" : "禁用");
    }

    public static double getTargetTPS() {
        return targetTPS;
    }

    public static void setTargetTPS(double tps) {
        targetTPS = Math.max(15.0, Math.min(20.0, tps));
    }

    public static double getTpsThresholdLow() {
        return tpsThresholdLow;
    }

    public static double getTpsThresholdHigh() {
        return tpsThresholdHigh;
    }

    public static int getMinSyncRadius() {
        return minSyncRadius;
    }

    public static int getMaxSyncRadius() {
        return maxSyncRadius;
    }

    public static boolean isPerformanceMonitorEnabled() {
        return performanceMonitorEnabled;
    }

    public static void setPerformanceMonitorEnabled(boolean enabled) {
        performanceMonitorEnabled = enabled;
        LOGGER.info("性能监控: {}", enabled ? "启用" : "禁用");
    }

    public static int getMonitorReportIntervalTicks() {
        return monitorReportIntervalTicks;
    }

    /**
     * 根据当前 TPS 动态调整同步半径
     */
    public static void adjustForTPS(double currentTPS) {
        if (!adaptiveThresholdEnabled) {
            return;
        }

        int currentRadius = syncGenerationRadius;
        int newRadius = currentRadius;

        if (currentTPS < tpsThresholdLow) {
            // TPS 过低，收紧同步半径（更多异步生成）
            newRadius = Math.max(minSyncRadius, currentRadius - 1);
            if (newRadius != currentRadius) {
                LOGGER.info("TPS 过低 ({:.2f})，收紧同步半径: {} -> {}",
                    currentTPS, currentRadius, newRadius);
            }
        } else if (currentTPS > tpsThresholdHigh) {
            // TPS 良好，可以放宽同步半径（减少异步开销）
            newRadius = Math.min(maxSyncRadius, currentRadius + 1);
            if (newRadius != currentRadius) {
                LOGGER.info("TPS 良好 ({:.2f})，放宽同步半径: {} -> {}",
                    currentTPS, currentRadius, newRadius);
            }
        }

        if (newRadius != currentRadius) {
            setSyncGenerationRadius(newRadius);
        }
    }

    /**
     * 从配置文件加载配置
     */
    public static void loadConfig() {
        Path configPath = Paths.get(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            LOGGER.info("配置文件不存在，使用默认配置");
            saveConfig(); // 创建默认配置文件
            return;
        }

        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            props.load(input);

            asyncChunkGenEnabled = Boolean.parseBoolean(
                props.getProperty("asyncChunkGen.enabled", "true"));
            syncGenerationRadius = Integer.parseInt(
                props.getProperty("asyncChunkGen.syncRadius", "3"));
            maxPendingTasks = Integer.parseInt(
                props.getProperty("asyncChunkGen.maxPendingTasks", "256"));
            workerThreads = Integer.parseInt(
                props.getProperty("asyncChunkGen.workerThreads",
                    String.valueOf(Math.max(2, Runtime.getRuntime().availableProcessors() - 2))));

            adaptiveThresholdEnabled = Boolean.parseBoolean(
                props.getProperty("adaptive.enabled", "true"));
            targetTPS = Double.parseDouble(
                props.getProperty("adaptive.targetTPS", "19.5"));
            tpsThresholdLow = Double.parseDouble(
                props.getProperty("adaptive.tpsThresholdLow", "18.0"));
            tpsThresholdHigh = Double.parseDouble(
                props.getProperty("adaptive.tpsThresholdHigh", "19.8"));

            minSyncRadius = Integer.parseInt(
                props.getProperty("adaptive.minSyncRadius", "2"));
            maxSyncRadius = Integer.parseInt(
                props.getProperty("adaptive.maxSyncRadius", "6"));

            performanceMonitorEnabled = Boolean.parseBoolean(
                props.getProperty("monitor.enabled", "true"));
            monitorReportIntervalTicks = Integer.parseInt(
                props.getProperty("monitor.reportIntervalTicks", "200"));

            LOGGER.info("配置已从 {} 加载", CONFIG_FILE);
        } catch (IOException | NumberFormatException e) {
            LOGGER.error("加载配置文件失败，使用默认配置", e);
        }
    }

    /**
     * 保存配置到文件
     */
    public static void saveConfig() {
        Path configPath = Paths.get(CONFIG_FILE);
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            LOGGER.error("创建配置目录失败", e);
            return;
        }

        Properties props = new Properties();
        props.setProperty("asyncChunkGen.enabled", String.valueOf(asyncChunkGenEnabled));
        props.setProperty("asyncChunkGen.syncRadius", String.valueOf(syncGenerationRadius));
        props.setProperty("asyncChunkGen.maxPendingTasks", String.valueOf(maxPendingTasks));
        props.setProperty("asyncChunkGen.workerThreads", String.valueOf(workerThreads));

        props.setProperty("adaptive.enabled", String.valueOf(adaptiveThresholdEnabled));
        props.setProperty("adaptive.targetTPS", String.valueOf(targetTPS));
        props.setProperty("adaptive.tpsThresholdLow", String.valueOf(tpsThresholdLow));
        props.setProperty("adaptive.tpsThresholdHigh", String.valueOf(tpsThresholdHigh));
        props.setProperty("adaptive.minSyncRadius", String.valueOf(minSyncRadius));
        props.setProperty("adaptive.maxSyncRadius", String.valueOf(maxSyncRadius));

        props.setProperty("monitor.enabled", String.valueOf(performanceMonitorEnabled));
        props.setProperty("monitor.reportIntervalTicks", String.valueOf(monitorReportIntervalTicks));

        try (OutputStream output = Files.newOutputStream(configPath)) {
            props.store(output, "Beryllium Performance Configuration");
            LOGGER.info("配置已保存到 {}", CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.error("保存配置文件失败", e);
        }
    }

    /**
     * 重新加载配置
     */
    public static void reload() {
        LOGGER.info("重新加载配置...");
        loadConfig();
    }
}
