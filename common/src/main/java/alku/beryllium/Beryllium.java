package alku.beryllium;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.command.BerylliumCommands;
import dev.architectury.event.events.common.CommandRegistrationEvent;

public final class Beryllium {
    public static final String MOD_ID = "beryllium";

    public static void init() {
        NativeBridge.initialize();
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> BerylliumCommands.register(dispatcher));
    }
}
