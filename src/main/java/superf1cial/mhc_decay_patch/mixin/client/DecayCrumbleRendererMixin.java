package superf1cial.mhc_decay_patch.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import superf1cial.mhc_decay_patch.Config;

import java.util.List;

/**
 * MHC gives the 256 nearest decayed blocks the full shatter shader, then spawns a whole falling cube for every
 * block past that, with no limit. A big Cheat Area sends thousands of blocks per tick, so the client ends up
 * simulating/drawing thousands of cubes (each also raycasting for the ground and shedding grains).
 * The list is already sorted nearest-first, so we keep the near ones and drop the far overflow.
 */
@Mixin(targets = "com.kujira.mhc.effect.client.DecayCrumbleRenderer", remap = false)
public abstract class DecayCrumbleRendererMixin {
    @Shadow(remap = false)
    @Final
    private static List<?> DEBRIS;

    @Unique
    private static int mhcdp$extraThisPacket;

    @Inject(method = "applyBlocks", at = @At("HEAD"), remap = false)
    private static void mhcdp$resetCounter(CallbackInfo ci) {
        mhcdp$extraThisPacket = 0;
    }

    @Redirect(method = "applyBlocks", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/kujira/mhc/effect/client/DecayCrumbleRenderer;addWholeDebris(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;J)V"))
    private static void mhcdp$cappedWholeDebris(ClientLevel level, BlockPos pos, BlockState state, long now) {
        int perPacket = Config.MAX_WHOLE_DEBRIS_PER_PACKET.get();
        if (perPacket > 0 && mhcdp$extraThisPacket >= perPacket) {
            return;
        }
        int total = Config.MAX_TOTAL_DEBRIS.get();
        if (total > 0 && DEBRIS.size() >= total) {
            return;
        }
        int maxDist = Config.WHOLE_DEBRIS_MAX_DISTANCE.get();
        if (maxDist > 0) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (Vec3.atCenterOf(pos).distanceToSqr(cam) > (double) maxDist * maxDist) {
                return;
            }
        }
        mhcdp$extraThisPacket++;
        mhcdp$invokeAddWholeDebris(level, pos, state, now);
    }

    @Shadow(remap = false)
    private static void addWholeDebris(ClientLevel level, BlockPos pos, BlockState state, long now) {
    }

    @Unique
    private static void mhcdp$invokeAddWholeDebris(ClientLevel level, BlockPos pos, BlockState state, long now) {
        addWholeDebris(level, pos, state, now);
    }
}
