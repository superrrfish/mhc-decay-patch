package superf1cial.mhc_decay_patch.mixin;

import com.kujira.mhc.Math.SkillMath;
import com.kujira.mhc.variable.CapabilityRegistry;
import com.kujira.mhc.variable.entity.IEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import superf1cial.mhc_decay_patch.Config;
import superf1cial.mhc_decay_patch.MarkCleanup;

/**
 * Clearing a "*_marked_*" flag that isn't set is a no-op (it already reads as false), so don't let MHC append an
 * inactive entry for it and send two sync packets about it. See MarkCleanup for the full story.
 */
@Mixin(value = SkillMath.class, remap = false)
public abstract class SkillMathMixin {
    @Inject(method = "setSkillActive", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mhcdp$skipNoopUnmark(LivingEntity player, String skill, boolean flag, CallbackInfo ci) {
        if (flag || !MarkCleanup.isMarkKey(skill) || !Config.FIX_MARK_LEAK.get()) {
            return;
        }
        IEntityData data = CapabilityRegistry.getData(player).orElse(null);
        if (data == null) {
            ci.cancel();
            return;
        }
        for (IEntityData.SkillActiveEntry e : data.isSkillActiveEntries()) {
            if (e.getSkill().equals(skill)) {
                if (e.isActive()) {
                    return; // a real mark being cleared: let MHC do it (and sync it)
                }
                break;
            }
        }
        ci.cancel();
    }
}
