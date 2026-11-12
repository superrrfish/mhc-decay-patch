package superf1cial.mhc_decay_patch;

import com.kujira.mhc.entity.Obj.HokaiEruption;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import superf1cial.mhc_decay_patch.mixin.client.LightTextureAccessor;

/**
 * Lighting for the eruption's floating block debris.
 *
 * MHC draws them with vanilla's position_color_tex_lightmap shader, which in 1.20.1 reads the light coordinates
 * but never applies them, so the cubes are always fullbright (bright white chunks deep in a dark cave). We look
 * up the light at each cube's own position, fetch the matching color from the live lightmap (which already
 * includes night, cave darkness, gamma, darkness effect, night vision...) and bake it into the vertex color.
 * Client / render thread only.
 */
public final class EruptionLight {
    private EruptionLight() {
    }

    private static HokaiEruption entity;
    private static Direction.Axis normal;
    private static float sign;
    private static double cu;
    private static double cv;
    private static float x;
    private static float z;
    private static float height;
    private static boolean valid;
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    public static void beginEntity(HokaiEruption e) {
        entity = e;
        valid = false;
    }

    public static void endEntity() {
        entity = null;
        valid = false;
        if (LIGHT_CACHE.size() > 65536) {
            LIGHT_CACHE.clear();
        }
    }

    public static boolean active() {
        return entity != null;
    }

    public static void beginPiece(Direction.Axis planeNormal, float planeSign, double planeCu, double planeCv,
                                  HokaiEruption.Face face, float px, float pz) {
        valid = entity != null && face != null;
        if (valid) {
            normal = planeNormal;
            sign = planeSign;
            cu = planeCu;
            cv = planeCv;
            x = px;
            z = pz;
            height = face.height();
        }
    }

    /** Packed light at the air block just outside the face the cube sits on, or the fallback if unknown. */
    private static int lightFor(int fallback) {
        if (!valid) {
            return fallback;
        }
        valid = false;
        Level level = entity.level();
        Direction.Axis ua = HokaiEruption.uAxis(normal);
        Direction.Axis va = HokaiEruption.vAxis(normal);
        double n = normal.choose(entity.getX(), entity.getY(), entity.getZ()) + (height + 0.5F) * sign;
        double u = cu + x;
        double v = cv + z;
        POS.set(Mth.floor(pick(Direction.Axis.X, ua, va, u, v, n)),
                Mth.floor(pick(Direction.Axis.Y, ua, va, u, v, n)),
                Mth.floor(pick(Direction.Axis.Z, ua, va, u, v, n)));
        // World light barely changes frame to frame; refresh the cache every few ticks instead of every frame.
        long now = level.getGameTime();
        int levelId = System.identityHashCode(level);
        if (levelId != cacheLevel || now - cacheTick >= 4L || now < cacheTick) {
            LIGHT_CACHE.clear();
            cacheLevel = levelId;
            cacheTick = now;
        }
        long key = POS.asLong();
        int cached = LIGHT_CACHE.get(key);
        if (cached != NO_LIGHT) {
            return cached;
        }
        int light = LevelRenderer.getLightColor(level, POS);
        LIGHT_CACHE.put(key, light);
        return light;
    }

    private static final int NO_LIGHT = Integer.MIN_VALUE;
    private static final Long2IntOpenHashMap LIGHT_CACHE = new Long2IntOpenHashMap();
    private static int cacheLevel;
    private static long cacheTick;

    static {
        LIGHT_CACHE.defaultReturnValue(NO_LIGHT);
    }

    private static double pick(Direction.Axis want, Direction.Axis ua, Direction.Axis va, double u, double v, double n) {
        if (want == ua) {
            return u;
        }
        if (want == va) {
            return v;
        }
        return n;
    }

    /** Lightmap color (0xRRGGBB) for a packed light value. */
    private static int lightmapColor(int packedLight) {
        NativeImage img = ((LightTextureAccessor) Minecraft.getInstance().gameRenderer.lightTexture()).mhcdp$lightPixels();
        if (img == null) {
            return 0xFFFFFF;
        }
        int block = Mth.clamp(LightTexture.block(packedLight), 0, 15);
        int sky = Mth.clamp(LightTexture.sky(packedLight), 0, 15);
        int abgr = img.getPixelRGBA(block, sky);
        return (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
    }

    /** Same geometry as HokaiEruptionRenderer#addTexturedCube, but tinted by the lightmap at the cube's spot. */
    public static void drawLitCube(BufferBuilder buffer, Matrix4f m, float h, TextureAtlasSprite sprite, int fallbackLight, int a) {
        int light = lightFor(fallbackLight);
        int rgb = lightmapColor(light);
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        face(buffer, m, -h, h, -h, -h, h, h, h, h, h, h, h, -h, u0, v0, u1, v1, light, r, g, b, a);
        face(buffer, m, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h, u0, v0, u1, v1, light, r, g, b, a);
        face(buffer, m, -h, -h, h, h, -h, h, h, h, h, -h, h, h, u0, v0, u1, v1, light, r, g, b, a);
        face(buffer, m, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, u0, v0, u1, v1, light, r, g, b, a);
        face(buffer, m, h, -h, h, h, -h, -h, h, h, -h, h, h, h, u0, v0, u1, v1, light, r, g, b, a);
        face(buffer, m, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, u0, v0, u1, v1, light, r, g, b, a);
    }

    private static void face(BufferBuilder buf, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float u0, float v0, float u1, float v1, int light, int r, int g, int b, int a) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, a).uv(u0, v0).uv2(light).endVertex();
        buf.vertex(m, x2, y2, z2).color(r, g, b, a).uv(u1, v0).uv2(light).endVertex();
        buf.vertex(m, x3, y3, z3).color(r, g, b, a).uv(u1, v1).uv2(light).endVertex();
        buf.vertex(m, x4, y4, z4).color(r, g, b, a).uv(u0, v1).uv2(light).endVertex();
    }
}
