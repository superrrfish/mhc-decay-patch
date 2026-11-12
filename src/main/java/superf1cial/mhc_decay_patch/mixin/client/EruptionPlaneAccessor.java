package superf1cial.mhc_decay_patch.mixin.client;

import com.kujira.mhc.entity.Obj.HokaiEruption;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.kujira.mhc.entity.Obj.client.HokaiEruptionRenderer$Plane", remap = false)
public interface EruptionPlaneAccessor {
    @Accessor(value = "faces", remap = false)
    Long2ObjectMap<HokaiEruption.Face> mhcdp$faces();

    @Accessor(value = "normal", remap = false)
    Direction.Axis mhcdp$normal();

    @Accessor(value = "sign", remap = false)
    float mhcdp$sign();

    @Accessor(value = "seed", remap = false)
    long mhcdp$seed();

    @Accessor(value = "cu", remap = false)
    double mhcdp$cu();

    @Accessor(value = "cv", remap = false)
    double mhcdp$cv();
}
