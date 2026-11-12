package superf1cial.mhc_decay_patch.mixin;

import com.kujira.mhc.Ability.Kosei.AClass.OneForAll.OneForAll;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import superf1cial.mhc_decay_patch.Config;
import superf1cial.mhc_decay_patch.GroundHackDebris;

/** Ground Hack's debris burst goes through OneForAll.BreakUp; budget it (see GroundHackDebris). */
@Mixin(value = OneForAll.class, remap = false)
public abstract class OneForAllMixin {
    @Inject(method = "BreakUp(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;ILnet/minecraft/core/BlockPos;Ljava/lang/String;FF)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void mhcdp$budgetGroundHack(LivingEntity player, Level world, int radius, BlockPos center, String skill,
                                               float p, float q, CallbackInfo ci) {
        if (world.isClientSide() || !"decay_ground".equals(skill) || !Config.OPTIMIZE_GROUND_HACK.get()) {
            return;
        }
        GroundHackDebris.breakUp(player, world, radius, center, skill, p, q);
        ci.cancel();
    }
}
