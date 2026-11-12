package superf1cial.mhc_decay_patch.mixin;

import com.kujira.mhc.Ability.Kosei.Rengo.Shigaraki;
import com.kujira.mhc.entity.Obj.HokaiEruption;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Shigaraki.class, remap = false)
public interface ShigarakiAccessor {
    @Invoker(value = "spawnOrRefreshHokaiEruption", remap = false)
    static HokaiEruption mhcdp$spawnOrRefreshHokaiEruption(LivingEntity player, Level world, double x, double y, double z,
                                                           float radius, double damage, int lv) {
        throw new AssertionError();
    }
}
