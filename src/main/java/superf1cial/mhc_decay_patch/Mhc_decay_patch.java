package superf1cial.mhc_decay_patch;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(Mhc_decay_patch.MODID)
public class Mhc_decay_patch {
    public static final String MODID = "mhc_decay_patch";

    public Mhc_decay_patch() {
        ModLoadingContext ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        ctx.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        // Works on either side alone: server-only fixes the tick lag, client-only fixes the FPS drop.
        ctx.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (remote, isServer) -> true));
    }
}
