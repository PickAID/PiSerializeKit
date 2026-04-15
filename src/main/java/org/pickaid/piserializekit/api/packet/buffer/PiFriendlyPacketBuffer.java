package org.pickaid.piserializekit.api.packet.buffer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

final class PiFriendlyPacketBuffer implements PiPacketBuffer {
    private final FriendlyByteBuf buffer;

    PiFriendlyPacketBuffer(FriendlyByteBuf buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
    }

    FriendlyByteBuf buffer() {
        return buffer;
    }

    @Override
    public boolean isReadable() {
        return buffer.isReadable();
    }

    @Override
    public <T> Optional<T> capability(Class<T> capabilityType) {
        Objects.requireNonNull(capabilityType, "capabilityType");
        if (capabilityType.isInstance(buffer)) {
            return Optional.of(capabilityType.cast(buffer));
        }
        return PiPacketBuffer.super.capability(capabilityType);
    }

    @Override
    public void writeByte(int value) {
        buffer.writeByte(value);
    }

    @Override
    public byte readByte() {
        return buffer.readByte();
    }

    @Override
    public void writeShort(int value) {
        buffer.writeShort(value);
    }

    @Override
    public short readShort() {
        return buffer.readShort();
    }

    @Override
    public void writeVarInt(int value) {
        buffer.writeVarInt(value);
    }

    @Override
    public int readVarInt() {
        return buffer.readVarInt();
    }

    @Override
    public void writeVarLong(long value) {
        buffer.writeVarLong(value);
    }

    @Override
    public long readVarLong() {
        return buffer.readVarLong();
    }

    @Override
    public void writeBoolean(boolean value) {
        buffer.writeBoolean(value);
    }

    @Override
    public boolean readBoolean() {
        return buffer.readBoolean();
    }

    @Override
    public void writeFloat(float value) {
        buffer.writeFloat(value);
    }

    @Override
    public float readFloat() {
        return buffer.readFloat();
    }

    @Override
    public void writeDouble(double value) {
        buffer.writeDouble(value);
    }

    @Override
    public double readDouble() {
        return buffer.readDouble();
    }

    @Override
    public void writeUtf(String value) {
        buffer.writeUtf(value);
    }

    @Override
    public String readUtf() {
        return buffer.readUtf();
    }

    @Override
    public String readUtf(int maxLength) {
        return buffer.readUtf(maxLength);
    }

    @Override
    public <E extends Enum<E>> void writeEnum(E value) {
        buffer.writeEnum(value);
    }

    @Override
    public <E extends Enum<E>> E readEnum(Class<E> enumType) {
        return buffer.readEnum(enumType);
    }

    @Override
    public void writeUuid(UUID value) {
        buffer.writeUUID(value);
    }

    @Override
    public UUID readUuid() {
        return buffer.readUUID();
    }

    @Override
    public void writeResourceLocation(ResourceLocation value) {
        buffer.writeResourceLocation(value);
    }

    @Override
    public ResourceLocation readResourceLocation() {
        return buffer.readResourceLocation();
    }

    @Override
    public void writeNbt(CompoundTag value) {
        buffer.writeNbt(value);
    }

    @Override
    public CompoundTag readNbt() {
        return buffer.readNbt();
    }

    @Override
    public void writeBlockPos(BlockPos value) {
        buffer.writeBlockPos(value);
    }

    @Override
    public BlockPos readBlockPos() {
        return buffer.readBlockPos();
    }
}
