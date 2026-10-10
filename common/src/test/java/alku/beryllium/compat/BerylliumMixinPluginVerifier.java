package alku.beryllium.compat;

import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class BerylliumMixinPluginVerifier {
    public static void main(String[] args) throws Exception {
        URL marker = URI.create("file:/vulkanmod/Initializer.class").toURL();
        ClassLoader resourceOnlyLoader = new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                return name.equals("net/vulkanmod/Initializer.class")
                    || name.equals("net/caffeinemc/mods/lithium/common/LithiumMod.class") ? marker : null;
            }

            @Override
            public Class<?> loadClass(String name) {
                throw new AssertionError("Compatibility detection must not load classes: " + name);
            }
        };
        check(BerylliumMixinPlugin.isVulkanModPresent(resourceOnlyLoader), "VulkanMod resource must be detected");
        check(!BerylliumMixinPlugin.isVulkanModPresent(new ClassLoader(null) {}), "absent VulkanMod must stay enabled");
        check(BerylliumMixinPlugin.isLithiumPresent(resourceOnlyLoader), "Lithium resource must be detected");
        check(!BerylliumMixinPlugin.isLithiumPresent(new ClassLoader(null) {}), "absent Lithium must stay enabled");

        BerylliumMixinPlugin compatible = new BerylliumMixinPlugin(true, false);
        BerylliumMixinPlugin vanilla = new BerylliumMixinPlugin(false, false);
        BerylliumMixinPlugin lithium = new BerylliumMixinPlugin(false, true);
        BerylliumMixinPlugin combined = new BerylliumMixinPlugin(true, true);
        try (var stream = BerylliumMixinPluginVerifier.class.getResourceAsStream("/beryllium.mixins.json")) {
            check(stream != null, "Mixin configuration must be packaged");
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            check(config.get("plugin").getAsString().equals(BerylliumMixinPlugin.class.getName()), "plugin must be registered");
            String prefix = config.get("package").getAsString() + ".";
            for (String group : new String[]{"client", "mixins"}) {
                for (var entry : config.getAsJsonArray(group)) {
                    String name = prefix + entry.getAsString();
                    check(vanilla.shouldApplyMixin("unused", name), "vanilla must apply " + name);
                    check(compatible.shouldApplyMixin("unused", name) == group.equals("mixins"),
                        "VulkanMod must skip input mixins and preserve common mixins: " + name);
                    boolean duplicateFrogSensor = entry.getAsString().equals("FrogAttackablesSensorMixin");
                    check(lithium.shouldApplyMixin("unused", name) == !duplicateFrogSensor,
                        "Lithium must only skip the duplicate frog sensor: " + name);
                    check(combined.shouldApplyMixin("unused", name) == (group.equals("mixins") && !duplicateFrogSensor),
                        "combined compatibility must preserve unrelated optimizations: " + name);
                }
            }
        }
        check(compatible.shouldApplyMixin("unused", "other.mod.InputLatencyMixin"), "unrelated mixins must remain enabled");
        System.out.println("VulkanMod and Lithium Mixin compatibility checks passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
