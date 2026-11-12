package superf1cial.mhc_decay_patch.mixin.client;

import com.kujira.mhc.entity.Obj.HokaiEruption;
import com.kujira.mhc.entity.Obj.client.HokaiEruptionRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import superf1cial.mhc_decay_patch.Config;
import superf1cial.mhc_decay_patch.EruptionLight;
import superf1cial.mhc_decay_patch.ScorchCache;

/**
 * 1) Scorch cracks: MHC rebuilds hundreds of crack shapes every frame (terrain lookup every 0.45 blocks) and
 *    re-uploads them. They only change when the surface is rescanned, so we record them once and keep them on
 *    the GPU (see ScorchCache).
 * 2) Floating debris cubes: vanilla's position_color_tex_lightmap shader ignores light entirely, so they were
 *    always fullbright. We tint each cube by the lightmap color at its own position (see EruptionLight).
 */
@Mixin(value = HokaiEruptionRenderer.class, remap = false)
public abstract class HokaiEruptionRendererMixin {
    private static final String RENDER =
            "render(Lcom/kujira/mhc/entity/Obj/HokaiEruption;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Shadow(remap = false)
    private void addTexturedCube(BufferBuilder buffer, Matrix4f matrix, float h, TextureAtlasSprite sprite, int light, int a) {
    }

    // ------------------------------------------------------------------ scorch cache

    @Inject(method = "addScorch", at = @At("HEAD"), cancellable = true, remap = false)
    private void mhcdp$scorchHead(BufferBuilder buffer, Matrix4f matrix, @Coerce Object plane, float radius,
                                  float revealRadius, float alphaMul, CallbackInfo ci) {
        // Needs the optimized scan: it gives every rescan a fresh face map, which is what invalidates the cache.
        if (!Config.CACHE_SCORCH.get() || !Config.OPTIMIZE_ERUPTION_SCAN.get()) {
            return;
        }
        EruptionPlaneAccessor p = (EruptionPlaneAccessor) plane;
        if (ScorchCache.tryDraw(matrix, p.mhcdp$faces(), p.mhcdp$seed(), radius, revealRadius, alphaMul)) {
            ci.cancel();
        }
    }

    @Inject(method = "addScorch", at = @At("RETURN"), remap = false)
    private void mhcdp$scorchReturn(BufferBuilder buffer, Matrix4f matrix, @Coerce Object plane, float radius,
                                    float revealRadius, float alphaMul, CallbackInfo ci) {
        ScorchCache.finishRecording();
    }

    @Inject(method = "addFlatTri", at = @At("HEAD"), remap = false)
    private void mhcdp$recordTri(BufferBuilder buffer, Matrix4f matrix, @Coerce Object plane,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, int r, int g, int b, int a, CallbackInfo ci) {
        if (!ScorchCache.isRecording()) {
            return;
        }
        EruptionPlaneAccessor p = (EruptionPlaneAccessor) plane;
        ScorchCache.record(p.mhcdp$normal(), p.mhcdp$sign(), x0, y0, z0);
        ScorchCache.record(p.mhcdp$normal(), p.mhcdp$sign(), x1, y1, z1);
        ScorchCache.record(p.mhcdp$normal(), p.mhcdp$sign(), x2, y2, z2);
    }

    // ------------------------------------------------------------------ debris lighting

    @Inject(method = RENDER, at = @At("HEAD"), remap = false)
    private void mhcdp$renderHead(HokaiEruption entity, float yaw, float partialTick, PoseStack poseStack,
                                  MultiBufferSource source, int packedLight, CallbackInfo ci) {
        EruptionLight.beginEntity(Config.FIX_ERUPTION_LIGHTING.get() ? entity : null);
    }

    @Inject(method = RENDER, at = @At("RETURN"), remap = false)
    private void mhcdp$renderReturn(HokaiEruption entity, float yaw, float partialTick, PoseStack poseStack,
                                    MultiBufferSource source, int packedLight, CallbackInfo ci) {
        EruptionLight.endEntity();
    }

    @Inject(method = "addPopDebrisPiece", at = @At("HEAD"), remap = false)
    private void mhcdp$pieceHead(BufferBuilder buffer, PoseStack poseStack, @Coerce Object plane, int i, float x, float z,
                                 float radius, float time, int packedLight, float alphaMul, CallbackInfo ci) {
        EruptionPlaneAccessor p = (EruptionPlaneAccessor) plane;
        HokaiEruption.Face face = p.mhcdp$faces().get(HokaiEruption.cell(
                Mth.floor(p.mhcdp$cu() + (double) x), Mth.floor(p.mhcdp$cv() + (double) z)));
        EruptionLight.beginPiece(p.mhcdp$normal(), p.mhcdp$sign(), p.mhcdp$cu(), p.mhcdp$cv(), face, x, z);
    }

    @Redirect(method = "addPopDebrisPiece", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/kujira/mhc/entity/Obj/client/HokaiEruptionRenderer;addTexturedCube(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;FLnet/minecraft/client/renderer/texture/TextureAtlasSprite;II)V"))
    private void mhcdp$litCube(HokaiEruptionRenderer self, BufferBuilder buffer, Matrix4f matrix, float h,
                               TextureAtlasSprite sprite, int light, int a) {
        if (EruptionLight.active()) {
            EruptionLight.drawLitCube(buffer, matrix, h, sprite, light, a);
        } else {
            this.addTexturedCube(buffer, matrix, h, sprite, light, a);
        }
    }
}
