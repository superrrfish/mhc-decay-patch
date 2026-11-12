package superf1cial.mhc_decay_patch;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Scorch-crack geometry for one eruption plane, built once per terrain rescan and kept on the GPU.
 * Client / render thread only.
 *
 * <p>Keyed by the identity of the plane's face map (weak keys in a Guava cache compare by identity, and every
 * rescan swaps in a new map), so a rescan naturally invalidates it and dead entries free their GPU buffer.
 */
public final class ScorchCache {
    private static final Cache<Object, ScorchCache> CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .maximumSize(64)
            .removalListener(n -> {
                ScorchCache c = (ScorchCache) n.getValue();
                if (c != null) {
                    c.close();
                }
            })
            .build();

    private static FloatArrayList recording;
    private static Object recKey;
    private static float recRadius;
    private static float recReveal;
    private static long recSeed;

    private final float radius;
    private final float reveal;
    private final long seed;
    private float[] xyz;          // dropped once uploaded
    private VertexBuffer vbo;     // created lazily on the first frame the geometry is reused
    private boolean empty;

    private ScorchCache(float radius, float reveal, long seed, float[] xyz) {
        this.radius = radius;
        this.reveal = reveal;
        this.seed = seed;
        this.xyz = xyz;
        this.empty = xyz.length < 9;
    }

    /**
     * Draws the cached scorch if still valid and returns true. Otherwise starts recording and returns false;
     * the caller then lets MHC build (and draw) it the normal way this frame.
     * Must be called with MHC's scorch render state already set (it is, at the head of addScorch).
     */
    public static boolean tryDraw(Matrix4f matrix, Object facesKey, long seed, float radius, float reveal, float alphaMul) {
        recording = null;
        ScorchCache c = CACHE.getIfPresent(facesKey);
        if (c != null && c.radius == radius && c.reveal == reveal && c.seed == seed) {
            c.draw(matrix, alphaMul);
            return true;
        }
        recording = new FloatArrayList();
        recKey = facesKey;
        recRadius = radius;
        recReveal = reveal;
        recSeed = seed;
        return false;
    }

    public static void finishRecording() {
        if (recording != null) {
            CACHE.put(recKey, new ScorchCache(recRadius, recReveal, recSeed, recording.toFloatArray()));
            recording = null;
            recKey = null;
        }
    }

    public static boolean isRecording() {
        return recording != null;
    }

    /** Same axis mapping as HokaiEruptionRenderer#vtx. */
    public static void record(Direction.Axis normal, float sign, float u, float h, float v) {
        FloatArrayList rec = recording;
        float a = h * sign;
        if (normal == Direction.Axis.X) {
            rec.add(a);
            rec.add(v);
            rec.add(u);
        } else if (normal == Direction.Axis.Y) {
            rec.add(u);
            rec.add(a);
            rec.add(v);
        } else {
            rec.add(u);
            rec.add(v);
            rec.add(a);
        }
    }

    private void draw(Matrix4f matrix, float alphaMul) {
        if (empty || alphaMul <= 0.0F) {
            return;
        }
        if (vbo == null) {
            upload();
        }
        ShaderInstance shader = GameRenderer.getPositionColorShader();
        if (shader == null) {
            return;
        }
        // Original: color (8,8,8,255*alpha), positions pre-multiplied by `matrix`, drawn with the global model-view.
        float[] old = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alphaMul);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrix);
        vbo.bind();
        vbo.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);
        VertexBuffer.unbind();
        BufferUploader.invalidate();
        RenderSystem.setShaderColor(old[0], old[1], old[2], old[3]);
    }

    private void upload() {
        float[] v = xyz;
        BufferBuilder b = new BufferBuilder(Math.max(256, v.length / 3 * DefaultVertexFormat.POSITION_COLOR.getVertexSize()));
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < v.length; i += 3) {
            b.vertex(v[i], v[i + 1], v[i + 2]).color(8, 8, 8, 255).endVertex();
        }
        vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(b.end());
        VertexBuffer.unbind();
        BufferUploader.invalidate();
        xyz = null;
    }

    private void close() {
        if (vbo != null) {
            VertexBuffer v = vbo;
            vbo = null;
            if (RenderSystem.isOnRenderThread()) {
                v.close();
            } else {
                RenderSystem.recordRenderCall(v::close);
            }
        }
    }
}
