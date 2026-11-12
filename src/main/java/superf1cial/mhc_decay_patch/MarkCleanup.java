package superf1cial.mhc_decay_patch;

import com.kujira.mhc.variable.CapabilityRegistry;
import com.kujira.mhc.variable.entity.IEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * MHC bug: whenever any mob dies, Magune/TwinImpact.clearMarksTargeting call
 * {@code setSkillActive(e, "<x>_marked_<deadUuid>", false)} on EVERY living entity within 900 blocks, and
 * setSkillActive ADDS a new (inactive) entry when one doesn't exist. Those dead entries are saved forever and the
 * whole list is re-synced to the client every tick. Mass kills (Cheat Area) grow it until the sync packet
 * exceeds the 2 MB NBT limit and the client gets kicked ("Tried to read NBT tag that was too big").
 *
 * An inactive mark and a missing mark read identically (isSkillActive -> false), so dropping them is lossless.
 */
@Mod.EventBusSubscriber(modid = Mhc_decay_patch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MarkCleanup {
    private MarkCleanup() {
    }

    public static boolean isMarkKey(String skill) {
        return skill != null && skill.contains("_marked_");
    }

    /** Removes inactive "*_marked_*" entries. Returns how many were removed. */
    public static int prune(IEntityData data) {
        List<IEntityData.SkillActiveEntry> entries = data.isSkillActiveEntries();
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int dead = 0;
        for (IEntityData.SkillActiveEntry e : entries) {
            if (!e.isActive() && isMarkKey(e.getSkill())) {
                dead++;
            }
        }
        if (dead == 0) {
            return 0;
        }
        List<IEntityData.SkillActiveEntry> kept = new ArrayList<>(entries.size() - dead);
        for (IEntityData.SkillActiveEntry e : entries) {
            if (e.isActive() || !isMarkKey(e.getSkill())) {
                kept.add(e);
            }
        }
        data.setSkillActiveEntries(kept);
        return dead;
    }

    /** Housekeeping for marks that were active and later cleared (those still leave an inactive entry). */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide() || (e.tickCount + e.getId()) % 200 != 0 || !Config.FIX_MARK_LEAK.get()) {
            return;
        }
        CapabilityRegistry.getData(e).ifPresent(MarkCleanup::prune);
    }
}
