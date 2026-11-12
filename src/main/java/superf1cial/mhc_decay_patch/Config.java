package superf1cial.mhc_decay_patch;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private Config() {
    }

    // ---------------------------------------------------------------- common (server-side logic)
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.BooleanValue OPTIMIZE_CHEAT_AREA;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_RADIUS;
    public static final ForgeConfigSpec.BooleanValue OPTIMIZE_ERUPTION_SCAN;
    public static final ForgeConfigSpec.DoubleValue SERVER_SCAN_BUDGET_MS;
    public static final ForgeConfigSpec.BooleanValue FIX_MARK_LEAK;
    public static final ForgeConfigSpec.BooleanValue OPTIMIZE_GROUND_HACK;
    public static final ForgeConfigSpec.IntValue GROUND_HACK_MAX_DEBRIS;
    public static final ForgeConfigSpec.IntValue GROUND_HACK_FULL_DENSITY_RADIUS;
    public static final ForgeConfigSpec.DoubleValue GROUND_HACK_MAX_CHUNK_SCALE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("cheat_area");
        OPTIMIZE_CHEAT_AREA = b
                .comment("Replace MHC's Cheat Area block spreading with the optimized version.",
                        "Set to false to run the original (laggy) code.")
                .define("optimize", true);
        MAX_BLOCKS_PER_TICK = b
                .comment("Max blocks the decay wave may check per tick. Normal-sized casts never hit this;",
                        "huge high-level casts get spread over a few extra ticks instead of freezing the server.",
                        "0 = unlimited (original behaviour).")
                .defineInRange("maxBlocksPerTick", 2048, 0, 1_000_000);
        MAX_RADIUS = b
                .comment("Hard cap on the Cheat Area radius. The original has no cap (radius can exceed 100 at high",
                        "Quirk Lv + full charge). 0 = no cap (original behaviour).")
                .defineInRange("maxRadius", 0, 0, 1024);
        b.pop();
        b.push("decay_eruption");
        OPTIMIZE_ERUPTION_SCAN = b
                .comment("Use the fast (same-result) terrain scan for the purple decay eruption effect.",
                        "This scan was the #1 lag source for big Cheat Areas, on both server and client.")
                .define("optimizeScan", true);
        SERVER_SCAN_BUDGET_MS = b
                .comment("Server: max milliseconds per tick spent rebuilding an eruption's surface. A big rebuild is",
                        "spread over several ticks (the previous surface keeps working meanwhile). 0 = no limit.")
                .defineInRange("serverScanBudgetMs", 4.0D, 0.0D, 50.0D);
        b.pop();
        b.push("ground_hack");
        OPTIMIZE_GROUND_HACK = b
                .comment("Budget Ground Hack's flying debris. MHC spawns one flying block entity per surface block in the",
                        "radius (thousands at high Quirk Lv + charge). Small casts are unchanged.")
                .define("optimize", true);
        GROUND_HACK_MAX_DEBRIS = b
                .comment("Max flying blocks per Ground Hack. Above this, blocks past the full-density radius are spawned",
                        "on a sparser grid as bigger chunks covering the same ground. 0 = unlimited (original).")
                .defineInRange("maxDebris", 500, 0, 100_000);
        GROUND_HACK_FULL_DENSITY_RADIUS = b
                .comment("Within this many blocks of the caster, every block still gets its own flying chunk.")
                .defineInRange("fullDensityRadius", 8, 0, 256);
        GROUND_HACK_MAX_CHUNK_SCALE = b
                .comment("Largest size a merged chunk can be scaled to (original chunks are 1.1).")
                .defineInRange("maxChunkScale", 3.3D, 1.1D, 16.0D);
        b.pop();
        b.push("mhc_bugfixes");
        FIX_MARK_LEAK = b
                .comment("Fix MHC's Twin Impact / Magnetism mark leak: every mob death added junk entries to every",
                        "entity within 900 blocks, forever. Bloats saves, re-syncs every tick, and eventually kicks you",
                        "with 'Tried to read NBT tag that was too big'. Also cleans up already-bloated saves.")
                .define("fixMarkLeak", true);
        b.pop();
        COMMON_SPEC = b.build();
    }

    // ---------------------------------------------------------------- client (VFX)
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.IntValue MAX_WHOLE_DEBRIS_PER_PACKET;
    public static final ForgeConfigSpec.IntValue MAX_TOTAL_DEBRIS;
    public static final ForgeConfigSpec.IntValue WHOLE_DEBRIS_MAX_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue CLIENT_SCAN_BUDGET_MS;
    public static final ForgeConfigSpec.BooleanValue CACHE_SCORCH;
    public static final ForgeConfigSpec.BooleanValue FIX_ERUPTION_LIGHTING;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("decay_crumble");
        MAX_WHOLE_DEBRIS_PER_PACKET = b
                .comment("The 256 nearest blocks always get the full shatter effect. Past that, MHC spawns a falling",
                        "cube for EVERY extra block. This caps how many of those extra cubes one tick of the wave can add",
                        "(nearest first). 0 = unlimited (original).")
                .defineInRange("maxExtraDebrisPerTick", 160, 0, 100_000);
        MAX_TOTAL_DEBRIS = b
                .comment("Don't add extra falling cubes while this many debris pieces are already alive. 0 = unlimited.")
                .defineInRange("maxTotalDebris", 2500, 0, 1_000_000);
        WHOLE_DEBRIS_MAX_DISTANCE = b
                .comment("Extra falling cubes farther than this from the camera are skipped (they're tiny on screen",
                        "anyway). 0 = no limit.")
                .defineInRange("extraDebrisMaxDistance", 64, 0, 1024);
        b.pop();
        b.push("decay_eruption");
        CLIENT_SCAN_BUDGET_MS = b
                .comment("Client: max milliseconds per frame spent rebuilding an eruption's surface. A big rebuild is",
                        "spread over several frames (the previous one stays on screen meanwhile). 0 = no limit.")
                .defineInRange("clientScanBudgetMs", 2.0D, 0.0D, 50.0D);
        CACHE_SCORCH = b
                .comment("Cache the eruption's dark scorch-crack geometry instead of rebuilding it every frame.")
                .define("cacheScorch", true);
        FIX_ERUPTION_LIGHTING = b
                .comment("Light the eruption's floating block debris by where each piece actually is (dark in caves/at",
                        "night) instead of one value for the whole effect, and bind the lightmap for it.")
                .define("fixDebrisLighting", true);
        b.pop();
        CLIENT_SPEC = b.build();
    }
}
