package superf1cial.mhc_decay_patch;

import com.kujira.mhc.entity.Obj.HokaiEruption;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * One rebuild of a HokaiEruption's terrain surface, resumable across frames/ticks. Produces exactly what
 * MHC's {@code scanIfNeeded} produces (same flood fill, same order, same tie-breaks, same addFace logic), but
 * into its own buffers so the entity keeps showing the previous result until this one is finished.
 */
public final class EruptionScanJob {
    private static final Direction[] DIRS = Direction.values();
    private static final int CHECK_EVERY = 32;

    private final Level level;
    private final Vec3 c;
    private final double cx, cy, cz, reachSq;
    private final ScanGrid g;

    private int phase; // 0 = air flood, 1 = surface walk, 2 = done
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final Long2IntOpenHashMap visible = new Long2IntOpenHashMap();
    private final LongArrayFIFOQueue blocks = new LongArrayFIFOQueue();
    private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

    public final Map<Direction, Long2ObjectMap<HokaiEruption.Face>> faces = new EnumMap<>(Direction.class);
    public final LongOpenHashSet surface = new LongOpenHashSet();

    public EruptionScanJob(Level level, Vec3 center, float radius) {
        this.level = level;
        this.c = center;
        this.cx = center.x;
        this.cy = center.y;
        this.cz = center.z;
        double reach = radius + 1.0F;
        this.reachSq = reach * reach;
        this.g = new ScanGrid(level);
        for (Direction d : DIRS) {
            faces.put(d, new Long2ObjectOpenHashMap<>());
        }

        // start cell: same iteration order and strict '<' as the original
        BlockPos at = BlockPos.containing(center);
        long start = 0L;
        boolean found = false;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -2, -2), at.offset(2, 2, 2))) {
            int x = p.getX(), y = p.getY(), z = p.getZ();
            double dist = sq(x, y, z);
            if (dist < best && !g.isSolid(x, y, z) && g.nearSolid(x, y, z)) {
                best = dist;
                start = BlockPos.asLong(x, y, z);
                found = true;
            }
        }
        if (found) {
            g.add(ScanGrid.SEEN, BlockPos.getX(start), BlockPos.getY(start), BlockPos.getZ(start));
            queue.enqueue(start);
            phase = 0;
        } else {
            phase = 2;
        }
    }

    private double sq(int x, int y, int z) {
        double dx = x + 0.5D - cx, dy = y + 0.5D - cy, dz = z + 0.5D - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean isDone() {
        return phase == 2;
    }

    /** Works until {@code deadlineNs} (System.nanoTime) or completion. Returns true when finished. */
    public boolean step(long deadlineNs) {
        int n = 0;
        while (phase != 2) {
            if (++n % CHECK_EVERY == 0 && System.nanoTime() >= deadlineNs) {
                return false;
            }
            if (phase == 0) {
                stepFlood();
            } else {
                stepWalk();
            }
        }
        return true;
    }

    private void stepFlood() {
        if (queue.isEmpty()) {
            if (visible.isEmpty()) {
                phase = 2;
                return;
            }
            long first = 0L;
            double best = Double.MAX_VALUE;
            LongIterator it = visible.keySet().iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                double dist = sq(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
                if (dist < best) {
                    best = dist;
                    first = key;
                }
            }
            g.add(ScanGrid.REACHED, BlockPos.getX(first), BlockPos.getY(first), BlockPos.getZ(first));
            blocks.enqueue(first);
            phase = 1;
            return;
        }
        long air = queue.dequeueLong();
        int ax = BlockPos.getX(air), ay = BlockPos.getY(air), az = BlockPos.getZ(air);
        for (Direction d : DIRS) {
            int x = ax + d.getStepX(), y = ay + d.getStepY(), z = az + d.getStepZ();
            if (g.isSolid(x, y, z)) {
                long key = BlockPos.asLong(x, y, z);
                visible.put(key, visible.get(key) | 1 << d.getOpposite().ordinal());
                g.add(ScanGrid.VISIBLE, x, y, z);
            } else if (g.add(ScanGrid.SEEN, x, y, z) && sq(x, y, z) <= reachSq && g.nearSolid(x, y, z)) {
                queue.enqueue(BlockPos.asLong(x, y, z));
            }
        }
    }

    private void stepWalk() {
        if (blocks.isEmpty()) {
            phase = 2;
            return;
        }
        long key = blocks.dequeueLong();
        int bx = BlockPos.getX(key), by = BlockPos.getY(key), bz = BlockPos.getZ(key);
        int mask = visible.get(key);
        if (mask != 0) {
            m.set(bx, by, bz);
            for (Direction d : DIRS) {
                if ((mask & 1 << d.ordinal()) != 0) {
                    addFace(key, m, d);
                }
            }
        }
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dz = -1; dz <= 1; ++dz) {
                    int x = bx + dx, y = by + dy, z = bz + dz;
                    if (g.get(ScanGrid.VISIBLE, x, y, z) && g.add(ScanGrid.REACHED, x, y, z)) {
                        blocks.enqueue(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
    }

    /** Mirror of HokaiEruption#addFace, writing into this job's buffers. */
    private void addFace(long key, BlockPos pos, Direction d) {
        surface.add(key);
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        Direction.Axis a = d.getAxis();
        int step = d.getAxisDirection().getStep();
        double plane = (double) pos.get(a) + (step > 0 ? shape.max(a) : shape.min(a));
        float height = (float) ((plane - a.choose(c.x, c.y, c.z)) * (double) step);
        long cell = HokaiEruption.cell(pos.get(HokaiEruption.uAxis(a)), pos.get(HokaiEruption.vAxis(a)));
        Long2ObjectMap<HokaiEruption.Face> map = faces.get(d);
        HokaiEruption.Face old = map.get(cell);
        if (old == null || Math.abs(height) < Math.abs(old.height())) {
            map.put(cell, new HokaiEruption.Face(height, state));
        }
    }
}
