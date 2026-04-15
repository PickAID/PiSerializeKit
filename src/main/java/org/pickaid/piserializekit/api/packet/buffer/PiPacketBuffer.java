package org.pickaid.piserializekit.api.packet.buffer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable packet-buffer surface for codec logic that should not depend directly on
 * one Minecraft transport buffer implementation.
 */
public interface PiPacketBuffer {
    void writeByte(int value);

    byte readByte();

    void writeShort(int value);

    short readShort();

    void writeVarInt(int value);

    int readVarInt();

    void writeVarLong(long value);

    long readVarLong();

    void writeBoolean(boolean value);

    boolean readBoolean();

    void writeFloat(float value);

    float readFloat();

    void writeDouble(double value);

    double readDouble();

    void writeUtf(String value);

    String readUtf();

    String readUtf(int maxLength);

    <E extends Enum<E>> void writeEnum(E value);

    <E extends Enum<E>> E readEnum(Class<E> enumType);

    void writeUuid(UUID value);

    UUID readUuid();

    void writeResourceLocation(ResourceLocation value);

    ResourceLocation readResourceLocation();

    void writeNbt(CompoundTag value);

    CompoundTag readNbt();

    void writeBlockPos(BlockPos value);

    BlockPos readBlockPos();

    default boolean isReadable() {
        return true;
    }

    default <T> Optional<T> capability(Class<T> capabilityType) {
        Objects.requireNonNull(capabilityType, "capabilityType");
        return Optional.empty();
    }

    default <T> T requireCapability(Class<T> capabilityType) {
        return capability(capabilityType).orElseThrow(() -> new IllegalStateException(
                "Missing packet buffer capability " + Objects.requireNonNull(capabilityType, "capabilityType").getName()
        ));
    }
}
