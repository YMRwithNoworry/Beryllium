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
                    || name.equals("net/caffeinemc/mods/lithium/common/LithiumMod.class")
                    || name.equals("org/violetmoon/quark/base/Quark.class") ? marker : null;
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
        check(BerylliumMixinPlugin.isQuarkPresent(resourceOnlyLoader), "Quark resource must be detected");
        check(!BerylliumMixinPlugin.isQuarkPresent(new ClassLoader(null) {}), "absent Quark must stay enabled");

        try (var stream = BerylliumMixinPluginVerifier.class.getResourceAsStream("/beryllium.mixins.json")) {
            check(stream != null, "Mixin configuration must be packaged");
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            check(config.get("plugin").getAsString().equals(BerylliumMixinPlugin.class.getName()), "plugin must be registered");
            String prefix = config.get("package").getAsString() + ".";
            for (String group : new String[]{"client", "mixins"}) {
                for (var entry : config.getAsJsonArray(group)) {
                    String name = prefix + entry.getAsString();
                    boolean duplicateFrogSensor = entry.getAsString().equals("FrogAttackablesSensorMixin");
                    boolean temptingSensor = entry.getAsString().equals("TemptingSensorMixin");
                    for (int mask = 0; mask < 8; mask++) {
                        boolean vulkan = (mask & 1) != 0;
                        boolean lithium = (mask & 2) != 0;
                        boolean quark = (mask & 4) != 0;
                        var plugin = new BerylliumMixinPlugin(vulkan, lithium, quark);
                        boolean expected = !(vulkan && group.equals("client"))
                            && !(lithium && duplicateFrogSensor) && !(quark && temptingSensor);
                        check(plugin.shouldApplyMixin("unused", name) == expected,
                            "compatibility combination " + mask + " must preserve unrelated optimizations: " + name);
                        check(plugin.shouldApplyMixin("unused", "other.mod.InputLatencyMixin"),
                            "unrelated mixins must remain enabled for combination " + mask);
                    }
                }
            }
        }
        System.out.println("VulkanMod, Lithium and Quark Mixin compatibility checks passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
