package org.pickaid.piserializekit;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.pickaid.piserializekit.api.service.PiSerializeServices;
import org.pickaid.piserializekit.runtime.service.PiBuiltInSerializers;
import org.pickaid.piserializekit.runtime.service.PiSerializeRuntime;
import org.slf4j.Logger;

@Mod(PiSerializeKit.MOD_ID)
public final class PiSerializeKit {
    public static final String MOD_ID = "piserializekit";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PiSerializeKit() {
        LOGGER.info("Initializing {}", MOD_ID);
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);
        PiSerializeServices.install(runtime);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
