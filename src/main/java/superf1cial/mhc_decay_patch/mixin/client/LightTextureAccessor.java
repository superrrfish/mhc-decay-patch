package superf1cial.mhc_decay_patch.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightTexture.class)
public interface LightTextureAccessor {
    /** CPU copy of the 16x16 lightmap (x = block light, y = sky light), refreshed every frame by vanilla. */
    @Accessor("lightPixels")
    NativeImage mhcdp$lightPixels();
}
