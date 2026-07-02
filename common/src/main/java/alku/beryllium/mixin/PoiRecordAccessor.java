package alku.beryllium.mixin;

import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PoiRecord.class)
public interface PoiRecordAccessor {
    @Invoker("acquireTicket")
    boolean beryllium$acquireTicket();
}
