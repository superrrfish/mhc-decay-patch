package superf1cial.mhc_decay_patch;

import com.kujira.mhc.Ability.Kosei.AClass.OneForAll.OneForAll;
import com.kujira.mhc.Math.SkillMath;
import com.kujira.mhc.entity.Obj.FallingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Ground Hack ("decay_ground") calls OneForAll.BreakUp, which spawns one flying FallingBlock entity for EVERY
 * surface block in its radius in a single tick. Radius = (3 + QuirkLv/8) * (1 + charge/10), so a strong cast
 * spawns thousands to tens of thousands of full mob-type entities (GeckoLib, AI, per-tick area damage scans) that
 * each linger ~10-12 s, and the client renders each one as an AO block model every frame.
 *
 * Same as the original up to a budget. Above it: full density near the caster, and further out one bigger
 * chunk per s*s cell (scale grows with s), so the same ground is still covered by flying rubble and, since MHC's
 * hit radius scales with the block's size, by damage too.
 */
public final class GroundHackDebris {
    private GroundHackDebris() {
    }

    private record Column(int dx, int dz, BlockPos surface, BlockState state) {
    }

    public static void breakUp(LivingEntity player, Level world, int radius, BlockPos center, String skill, float p, float q) {
        int verticalSearch = 4;
        List<Column> inner = new ArrayList<>();
        List<Column> outer = new ArrayList<>();
        int innerR = Config.GROUND_HACK_FULL_DENSITY_RADIUS.get();
        int innerR2 = innerR * innerR;

        // Same candidate selection as the original BreakUp.
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                int d2 = dx * dx + dz * dz;
                if (d2 > radius * radius) {
                    continue;
                }
                int sx = center.getX() + dx;
                int sz = center.getZ() + dz;
                BlockPos surface = OneForAll.findNearbySurface(world, sx, center.getY(), sz, verticalSearch);
                if (surface == null) {
                    continue;
                }
                BlockState state = world.getBlockState(surface);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    (d2 <= innerR2 ? inner : outer).add(new Column(dx, dz, surface, state));
                }
            }
        }

        float damage = SkillMath.Damage(player, p, q);
        int max = Config.GROUND_HACK_MAX_DEBRIS.get();
        if (max <= 0 || inner.size() + outer.size() <= max) {
            for (Column c : inner) {
                spawn(player, world, center, c, 0.0D, 1.1F, damage);
            }
            for (Column c : outer) {
                spawn(player, world, center, c, 0.0D, 1.1F, damage);
            }
            return;
        }

        // Near the caster: unchanged.
        for (Column c : inner) {
            spawn(player, world, center, c, 0.0D, 1.1F, damage);
        }
        // Further out: one bigger chunk per s*s cell, random grid offset per cast so it never looks gridded twice.
        int budget = Math.max(64, max - inner.size());
        int s = Math.max(2, Mth.ceil(Math.sqrt((double) outer.size() / (double) budget)));
        int ox = world.random.nextInt(s);
        int oz = world.random.nextInt(s);
        float maxScale = Config.GROUND_HACK_MAX_CHUNK_SCALE.get().floatValue();
        float scale = Math.min(1.1F * s, maxScale);
        double centerShift = (s - 1) * 0.5D; // put the chunk in the middle of the cell it stands for
        for (Column c : outer) {
            if (Math.floorMod(c.dx + ox, s) == 0 && Math.floorMod(c.dz + oz, s) == 0) {
                spawn(player, world, center, c, centerShift, scale, damage);
            }
        }
    }

    /** Mirror of the original per-column spawn (decay_ground uses the default velocity branch). */
    private static void spawn(LivingEntity player, Level world, BlockPos center, Column c, double shift, float scale, float damage) {
        int sx = center.getX() + c.dx;
        int sz = center.getZ() + c.dz;
        FallingBlock obj = new FallingBlock(world, sx + 0.5D + shift, c.surface.getY() + 1, sz + 0.5D + shift, c.state);
        obj.noPhysics = true;
        obj.setOwner(player);
        obj.setDamage(damage);
        double velocity = 4.0D;
        double vx = world.random.nextDouble() - 0.5D;
        double vy = world.random.nextDouble() * 0.5D;
        double vz = world.random.nextDouble() - 0.5D;
        obj.setDeltaMovement(vx * velocity, vy * velocity, vz * velocity);
        obj.setScale(scale);
        world.addFreshEntity(obj);
    }
}
