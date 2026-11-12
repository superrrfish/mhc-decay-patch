package superf1cial.mhc_decay_patch.mixin;

import com.kujira.mhc.entity.Obj.HokaiEruption;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import superf1cial.mhc_decay_patch.Config;
import superf1cial.mhc_decay_patch.EruptionScanJob;

import java.util.Map;

/**
 * {@code HokaiEruption.scanIfNeeded} (the terrain-hugging purple eruption) rebuilds the whole surface inside
 * the radius every 5 ticks, synchronously, on the server tick AND on the client render thread. At high Quirk
 * Lv + Trigger the radius is huge and one rebuild costs tens of ms: a frame/tick spike 4x a second.
 *
 * We run the same rebuild (same output) as a resumable job with a per-frame / per-tick time budget, keep
 * showing the previous surface meanwhile, and swap in the new one when it's done.
 */
@Mixin(value = HokaiEruption.class, remap = false)
public abstract class HokaiEruptionMixin {
    @Shadow(remap = false) @Final private Map<Direction, Long2ObjectMap<HokaiEruption.Face>> faces;
    @Shadow(remap = false) @Final @Mutable private LongOpenHashSet surface;
    @Shadow(remap = false) private int scannedAt;
    @Shadow(remap = false) private float scannedRadius;

    @Unique private EruptionScanJob mhcdp$job;
    @Unique private long mhcdp$lastWorkEnd;

    @Inject(method = "scanIfNeeded", at = @At("HEAD"), cancellable = true, remap = false)
    private void mhcdp$budgetedScan(CallbackInfo ci) {
        if (!Config.OPTIMIZE_ERUPTION_SCAN.get()) {
            return;
        }
        ci.cancel();
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        float radius = ((HokaiEruption) (Object) this).getRadius();
        int tick = self.tickCount;

        if (this.mhcdp$job == null) {
            if (tick - this.scannedAt < 5 && this.scannedRadius == radius) {
                return;
            }
            this.scannedAt = tick;
            this.scannedRadius = radius;
            this.mhcdp$job = new EruptionScanJob(level, self.position(), radius);
        }

        long now = System.nanoTime();
        // The renderer asks for faces 6x per frame; only the first call each frame does work.
        if (now - this.mhcdp$lastWorkEnd < 1_000_000L) {
            return;
        }
        double budgetMs = level.isClientSide() ? Config.CLIENT_SCAN_BUDGET_MS.get() : Config.SERVER_SCAN_BUDGET_MS.get();
        long deadline = budgetMs <= 0.0D ? Long.MAX_VALUE : now + (long) (budgetMs * 1_000_000.0D);
        boolean done = this.mhcdp$job.step(deadline);
        this.mhcdp$lastWorkEnd = System.nanoTime();
        if (done) {
            EruptionScanJob job = this.mhcdp$job;
            this.mhcdp$job = null;
            for (Direction d : Direction.values()) {
                this.faces.put(d, job.faces.get(d));
            }
            this.surface = job.surface;
        }
    }
}
