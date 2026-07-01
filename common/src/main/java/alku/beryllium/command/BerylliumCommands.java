package alku.beryllium.command;

import alku.beryllium.bridge.NativeBridge;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class BerylliumCommands {
    private static final int[] SAMPLE_POSITIONS = {
        0, 64, 0,
        3, 68, 4,
        -1, 63, -2,
        128, 70, -128
    };

    private BerylliumCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("beryllium")
            .then(Commands.literal("native")
                .executes(context -> showNativeStatus(context.getSource())))
            .then(Commands.literal("distance")
                .executes(context -> runDistanceKernel(context.getSource()))));
    }

    private static int showNativeStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Beryllium native backend: " + NativeBridge.status()), false);
        return NativeBridge.isLoaded() ? 1 : 0;
    }

    private static int runDistanceKernel(CommandSourceStack source) {
        long[] distances = NativeBridge.computeSquaredDistances(0, 64, 0, SAMPLE_POSITIONS);
        source.sendSuccess(() -> Component.literal("Beryllium squared distances: " + format(distances)), false);
        return distances.length;
    }

    private static String format(long[] values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(values[index]);
        }
        return builder.toString();
    }
}
