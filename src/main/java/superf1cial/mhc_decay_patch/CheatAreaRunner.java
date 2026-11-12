package superf1cial.mhc_decay_patch;

import com.kujira.mhc.Ability.Kosei.Rengo.Shigaraki;
import com.kujira.mhc.Animation.Action.Ani;
import com.kujira.mhc.Math.SkillMath;
import com.kujira.mhc.effect.HokaiEffect;
import com.kujira.mhc.sound.ModSounds;
import com.kujira.mhc.variable.CapabilityRegistry;
import com.kujira.mhc.variable.entity.IEntityData;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import superf1cial.mhc_decay_patch.mixin.ShigarakiAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drop-in replacement for {@code Shigaraki.CheatArea}. Same wave (one block layer per tick, same radius,
 * same blocks destroyed, same crumble VFX packets) but without the waste:
 * <ul>
 *   <li>The original flood fill also crawled through every solid block BELOW the player's feet, even though
 *   it never destroys anything under the ground layer. For a radius-R cast that's a half sphere of stone
 *   (~2/3·π·R³ blocks, e.g. ~260k at R=50) checked for nothing. We keep the fill at/above the ground layer.</li>
 *   <li>{@code isCanDestroy} rebuilt a 19-element list and looked up a capability for every neighbour check
 *   (6 per block). We hoist both out of the loop.</li>
 *   <li>Positions are packed longs in fastutil sets instead of boxed {@code BlockPos} in a {@code HashSet}.</li>
 *   <li>Optional per-tick budget so a monster cast spreads over a few more ticks instead of one giant spike.</li>
 * </ul>
 */
public final class CheatAreaRunner {
    private CheatAreaRunner() {
    }

    private static final Direction[] DIRS = Direction.values();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static Set<BlockState> protectedStates;
    private static long lastPurge;

    private static final class State {
        final LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayList layer = new LongArrayList();
        LongArrayList next = new LongArrayList();
        int index;
        long lastTick;
    }

    private static Set<BlockState> protectedStates() {
        Set<BlockState> s = protectedStates;
        if (s == null) {
            // BlockState has identity equality, so this matches List.contains in the original exactly.
            s = new ReferenceOpenHashSet<>(Shigaraki.DontDstroytList());
            protectedStates = s;
        }
        return s;
    }

    public static void tick(LivingEntity player, Level world) {
        SkillMath.Stop(player);
        IEntityData prop = CapabilityRegistry.get(player);
        int level = SkillMath.getQuirkLv(player) / 4;
        int maxPressCount = prop.getPressCount();
        int pressCount = (int) ((double) level / 8.0D * (double) maxPressCount);
        int radius = 10 + pressCount;
        int cap = Config.MAX_RADIUS.get();
        if (cap > 0) {
            radius = Math.min(radius, cap);
        }

        BlockPos center = player.blockPosition();
        int baseY = center.getY() - 1;
        UUID id = player.getUUID();
        long now = world.getGameTime();
        purgeStale(now);

        if (player.getPersistentData().getDouble("cnt1") == 1.0D) {
            if (!player.onGround()) {
                STATES.remove(id);
                SkillMath.RemoveSkill(player);
                return;
            }
            long start = center.below().asLong();
            State st = new State();
            st.visited.add(start);
            st.layer.add(start);
            STATES.put(id, st);
            ShigarakiAccessor.mhcdp$spawnOrRefreshHokaiEruption(player, world,
                    center.getX() + 0.5D, baseY + 1, center.getZ() + 0.5D, (float) radius, 11.0D, 7);
        }

        if (player instanceof Player) {
            Ani.Animation(player, "animation.mhc.shigaraki_4");
        }

        State st = STATES.get(id);
        if (st == null) {
            SkillMath.RemoveSkill(player);
            return;
        }
        st.lastTick = now;

        boolean destroyAll = prop.getLevel() >= 6;
        Set<BlockState> protectedSet = protectedStates();
        double cx = center.getX() + 0.5D;
        double cy = center.getY() + 0.5D;
        double cz = center.getZ() + 0.5D;
        double r2 = (double) radius * (double) radius;
        int budget = Config.MAX_BLOCKS_PER_TICK.get();
        if (budget <= 0) {
            budget = Integer.MAX_VALUE;
        }

        boolean playedSound = false;
        List<BlockPos> crumbled = new ArrayList<>();
        List<BlockState> crumbledStates = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        LongArrayList layer = st.layer;
        LongOpenHashSet visited = st.visited;
        LongArrayList next = st.next;

        int processed = 0;
        while (st.index < layer.size() && processed < budget) {
            long key = layer.getLong(st.index++);
            processed++;
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            m.set(x, y, z);
            BlockState state = world.getBlockState(m);
            if (y >= baseY && !state.isAir() && state.getDestroySpeed(world, m) >= 0.0F
                    && canDestroy(destroyAll, protectedSet, state)) {
                if (!playedSound) {
                    world.playSound(null, x, y, z, ModSounds.DECAY.get(), SoundSource.PLAYERS, 0.7F,
                            world.random.nextFloat() * 2.0F);
                    playedSound = true;
                }
                if (y > baseY) {
                    BlockPos pos = new BlockPos(x, y, z);
                    world.setBlock(pos, AIR, 2);
                    crumbled.add(pos);
                    crumbledStates.add(state);
                }
            }

            for (Direction dir : DIRS) {
                int nx = x + dir.getStepX();
                int ny = y + dir.getStepY();
                int nz = z + dir.getStepZ();
                // Nothing below the ground layer is ever destroyed; don't flood the bedrock-ward half sphere.
                if (ny < baseY) {
                    continue;
                }
                long nk = BlockPos.asLong(nx, ny, nz);
                if (visited.contains(nk)) {
                    continue;
                }
                double dx = nx + 0.5D - cx;
                double dy = ny + 0.5D - cy;
                double dz = nz + 0.5D - cz;
                if (dx * dx + dy * dy + dz * dz > r2) {
                    continue;
                }
                m.set(nx, ny, nz);
                BlockState ns = world.getBlockState(m);
                if (!ns.isAir() && ns.getDestroySpeed(world, m) >= 0.0F && canDestroy(destroyAll, protectedSet, ns)) {
                    visited.add(nk);
                    next.add(nk);
                }
            }
        }

        HokaiEffect.sendCrumbledBlocks(world, crumbled, crumbledStates);

        if (st.index >= layer.size()) {
            if (next.isEmpty()) {
                STATES.remove(id);
                SkillMath.RemoveSkill(player);
            } else {
                st.layer = next;
                st.next = new LongArrayList();
                st.index = 0;
            }
        }
    }

    private static boolean canDestroy(boolean destroyAll, Set<BlockState> protectedSet, BlockState state) {
        if (destroyAll) {
            return true;
        }
        return !state.liquid() && !protectedSet.contains(state);
    }

    /** Drop state for casts that were interrupted (stun, death, logout) so their visited sets don't linger. */
    private static void purgeStale(long now) {
        if (now - lastPurge < 200L || STATES.isEmpty()) {
            return;
        }
        lastPurge = now;
        Iterator<State> it = STATES.values().iterator();
        while (it.hasNext()) {
            long age = now - it.next().lastTick;
            if (age > 200L || age < 0L) {
                it.remove();
            }
        }
    }
}
