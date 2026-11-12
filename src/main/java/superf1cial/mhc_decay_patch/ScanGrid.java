package superf1cial.mhc_decay_patch;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Sparse per-16³-section bit flags for one terrain scan. Replaces several Long2*OpenHashMaps (one hash
 * lookup per flag per block) with one lookup per section, cached for the last section touched. Flood fills
 * walk neighbours, so nearly every access hits the cached section.
 */
public final class ScanGrid {
    public static final int KNOWN = 0;
    public static final int SOLID = 1;
    public static final int SEEN = 2;
    public static final int VISIBLE = 3;
    public static final int REACHED = 4;
    private static final int PLANES = 5;
    private static final int WORDS = 64; // 4096 bits per plane

    private final Long2ObjectOpenHashMap<long[]> sections = new Long2ObjectOpenHashMap<>();
    private long lastKey = Long.MIN_VALUE;
    private long[] last;

    private final Level level;
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private LevelChunk lastChunk;

    public ScanGrid(Level level) {
        this.level = level;
    }

    private long[] section(int x, int y, int z) {
        long key = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
        if (key == lastKey) {
            return last;
        }
        long[] s = sections.get(key);
        if (s == null) {
            s = new long[PLANES * WORDS];
            sections.put(key, s);
        }
        lastKey = key;
        last = s;
        return s;
    }

    private static int bit(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    public boolean get(int plane, int x, int y, int z) {
        int b = bit(x, y, z);
        return (section(x, y, z)[plane * WORDS + (b >>> 6)] & (1L << b)) != 0L;
    }

    /** Sets the flag; returns true if it was not set before (like Set.add). */
    public boolean add(int plane, int x, int y, int z) {
        int b = bit(x, y, z);
        long[] s = section(x, y, z);
        int w = plane * WORDS + (b >>> 6);
        long mask = 1L << b;
        if ((s[w] & mask) != 0L) {
            return false;
        }
        s[w] |= mask;
        return true;
    }

    /** Same answer as MHC's {@code isLoaded(pos) && !state.getCollisionShape(level, pos).isEmpty()}, memoized. */
    public boolean isSolid(int x, int y, int z) {
        int b = bit(x, y, z);
        long[] s = section(x, y, z);
        int w = b >>> 6;
        long mask = 1L << b;
        if ((s[KNOWN * WORDS + w] & mask) != 0L) {
            return (s[SOLID * WORDS + w] & mask) != 0L;
        }
        boolean solid = computeSolid(x, y, z);
        s[KNOWN * WORDS + w] |= mask;
        if (solid) {
            s[SOLID * WORDS + w] |= mask;
        }
        return solid;
    }

    private boolean computeSolid(int x, int y, int z) {
        if (level.isOutsideBuildHeight(y)) {
            return false;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        LevelChunk chunk;
        if (cx == lastChunkX && cz == lastChunkZ) {
            chunk = lastChunk;
        } else {
            chunk = level.getChunkSource().getChunkNow(cx, cz);
            lastChunkX = cx;
            lastChunkZ = cz;
            lastChunk = chunk;
        }
        if (chunk == null) {
            return false;
        }
        mpos.set(x, y, z);
        BlockState state = chunk.getBlockState(mpos);
        return !state.getCollisionShape(level, mpos).isEmpty();
    }

    public boolean nearSolid(int x, int y, int z) {
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dz = -1; dz <= 1; ++dz) {
                    if (isSolid(x + dx, y + dy, z + dz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
