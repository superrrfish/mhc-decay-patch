package superf1cial.mhc_decay_patch.mixin;

import com.kujira.mhc.variable.entity.IEntityData;
import com.kujira.mhc.variable.entity.JJKCap;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import superf1cial.mhc_decay_patch.Config;
import superf1cial.mhc_decay_patch.MarkCleanup;

/** Strip leaked inactive mark entries whenever MHC data is loaded (from disk or a sync), healing bloated saves. */
@Mixin(value = JJKCap.class, remap = false)
public abstract class JJKCapMixin {
    @Inject(method = "deserializeNBTSkillActive", at = @At("RETURN"), remap = false)
    private void mhcdp$pruneOnLoad(CompoundTag nbt, CallbackInfo ci) {
        if (Config.FIX_MARK_LEAK.get()) {
            MarkCleanup.prune((IEntityData) this);
        }
    }
}
