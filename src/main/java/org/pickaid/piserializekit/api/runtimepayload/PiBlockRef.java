package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record PiBlockRef(PiDimensionRef dimension, BlockPos pos) {
    public PiBlockRef {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(pos, "pos");
    }

    public static PiBlockRef at(PiDimensionRef dimension, BlockPos pos) {
        return new PiBlockRef(dimension, pos);
    }
}
