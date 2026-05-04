package org.pickaid.piserializekit.api.packet;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffers;

class PiPacketBufferCapabilityTest {
    @Test
    void friendlyPacketBufferDoesNotExposeUnknownCapabilitiesByDefault() {
        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PiPacketBuffer buffer = PiPacketBuffers.wrap(byteBuf);

            assertTrue(buffer.capability(TestCapability.class).isEmpty());
            assertThrows(IllegalStateException.class, () -> buffer.requireCapability(TestCapability.class));
        } finally {
            byteBuf.release();
        }
    }

    @Test
    void capabilityAwarePacketBufferCanExposeCustomCapability() {
        TestCapability capability = new TestCapability() {
        };
        PiPacketBuffer buffer = new CapabilityAwarePacketBuffer(capability);

        assertSame(capability, buffer.capability(TestCapability.class).orElseThrow());
        assertSame(capability, buffer.requireCapability(TestCapability.class));
    }

    private interface TestCapability {
    }

    private record CapabilityAwarePacketBuffer(TestCapability capability) implements PiPacketBuffer {
        @Override
        public <T> java.util.Optional<T> capability(Class<T> capabilityType) {
            if (capabilityType.isInstance(capability)) {
                return java.util.Optional.of(capabilityType.cast(capability));
            }
            return java.util.Optional.empty();
        }

        @Override
        public void writeByte(int value) {
        }

        @Override
        public byte readByte() {
            return 0;
        }

        @Override
        public void writeShort(int value) {
        }

        @Override
        public short readShort() {
            return 0;
        }

        @Override
        public void writeVarInt(int value) {
        }

        @Override
        public int readVarInt() {
            return 0;
        }

        @Override
        public void writeVarLong(long value) {
        }

        @Override
        public long readVarLong() {
            return 0;
        }

        @Override
        public void writeBoolean(boolean value) {
        }

        @Override
        public boolean readBoolean() {
            return false;
        }

        @Override
        public void writeFloat(float value) {
        }

        @Override
        public float readFloat() {
            return 0;
        }

        @Override
        public void writeDouble(double value) {
        }

        @Override
        public double readDouble() {
            return 0;
        }

        @Override
        public void writeUtf(String value) {
        }

        @Override
        public String readUtf() {
            return "";
        }

        @Override
        public String readUtf(int maxLength) {
            return "";
        }

        @Override
        public <E extends Enum<E>> void writeEnum(E value) {
        }

        @Override
        public <E extends Enum<E>> E readEnum(Class<E> enumType) {
            return enumType.getEnumConstants()[0];
        }

        @Override
        public void writeUuid(java.util.UUID value) {
        }

        @Override
        public java.util.UUID readUuid() {
            return new java.util.UUID(0L, 0L);
        }

        @Override
        public void writeResourceLocation(net.minecraft.resources.ResourceLocation value) {
        }

        @Override
        public net.minecraft.resources.ResourceLocation readResourceLocation() {
            return new net.minecraft.resources.ResourceLocation("test", "value");
        }

        @Override
        public void writeNbt(net.minecraft.nbt.CompoundTag value) {
        }

        @Override
        public net.minecraft.nbt.CompoundTag readNbt() {
            return new net.minecraft.nbt.CompoundTag();
        }

        @Override
        public void writeBlockPos(net.minecraft.core.BlockPos value) {
        }

        @Override
        public net.minecraft.core.BlockPos readBlockPos() {
            return net.minecraft.core.BlockPos.ZERO;
        }
    }
}
