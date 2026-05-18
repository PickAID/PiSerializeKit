package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.resources.ResourceLocation;

/**
 * Base contract for runtime payloads exchanged between Pi runtime modules.
 */
public interface PiRuntimePayload {
    ResourceLocation type();

    long sequence();
}
