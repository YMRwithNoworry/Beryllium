package alku.beryllium.compat;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class BerylliumMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> INPUT_LATENCY_MIXINS = Set.of(
        "alku.beryllium.mixin.KeyboardInputLatencyMixin",
        "alku.beryllium.mixin.MinecraftInputLatencyAccess",
        "alku.beryllium.mixin.MinecraftInputLatencyMixin",
        "alku.beryllium.mixin.MouseButtonInputLatencyMixin",
        "alku.beryllium.mixin.SmoothDoubleAccessor"
    );

    private final boolean vulkanModPresent;
    private final boolean lithiumPresent;

    public BerylliumMixinPlugin() {
        this(isVulkanModPresent(BerylliumMixinPlugin.class.getClassLoader()),
            isLithiumPresent(BerylliumMixinPlugin.class.getClassLoader()));
    }

    BerylliumMixinPlugin(boolean vulkanModPresent, boolean lithiumPresent) {
        this.vulkanModPresent = vulkanModPresent;
        this.lithiumPresent = lithiumPresent;
    }

    static boolean isVulkanModPresent(ClassLoader loader) {
        // Inspect a resource without loading VulkanMod or Minecraft during transformation.
        return loader.getResource("net/vulkanmod/Initializer.class") != null;
    }

    static boolean isLithiumPresent(ClassLoader loader) {
        return loader.getResource("net/caffeinemc/mods/lithium/common/LithiumMod.class") != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
        if (this.vulkanModPresent) {
            LoggerFactory.getLogger("beryllium").info(
                "VulkanMod detected: disabling input latency mixins; common optimizations remain enabled."
            );
        }
        if (this.lithiumPresent) {
            LoggerFactory.getLogger("beryllium").info(
                "Lithium detected: disabling the duplicate frog sensor mixin."
            );
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // A runtime flag is too late: runTick injection and local capture happen at load time.
        // Skip the whole group so callbacks cannot use accessors that were not installed.
        if (this.lithiumPresent && mixinClassName.equals("alku.beryllium.mixin.FrogAttackablesSensorMixin")) {
            return false;
        }
        return !this.vulkanModPresent || !INPUT_LATENCY_MIXINS.contains(mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
