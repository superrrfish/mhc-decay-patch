package superf1cial.mhc_decay_patch.mixin;

import com.kujira.mhc.Ability.Kosei.Rengo.Shigaraki;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import superf1cial.mhc_decay_patch.CheatAreaRunner;
import superf1cial.mhc_decay_patch.Config;

@Mixin(value = Shigaraki.class, remap = false)
public abstract class ShigarakiMixin {
    @Inject(method = "CheatArea", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mhcdp$optimizedCheatArea(LivingEntity player, Level world, CallbackInfo ci) {
        if (world.isClientSide() || !Config.OPTIMIZE_CHEAT_AREA.get()) {
            return;
        }
        CheatAreaRunner.tick(player, world);
        ci.cancel();
    }
}
