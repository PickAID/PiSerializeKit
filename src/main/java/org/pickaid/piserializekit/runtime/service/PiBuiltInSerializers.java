package org.pickaid.piserializekit.runtime.service;

import com.mojang.serialization.Codec;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.service.PiSerializeService;
import org.pickaid.piserializekit.api.service.PiSerializers;

public final class PiBuiltInSerializers {
    private PiBuiltInSerializers() {
    }

    public static void install(PiSerializeService service) {
        service.register(PiSerializers.INT, PiSerializers.of(Codec.INT, new VarIntCodec()));
        service.register(PiSerializers.LONG, PiSerializers.of(Codec.LONG, new VarLongCodec()));
        service.register(PiSerializers.BOOLEAN, PiSerializers.of(Codec.BOOL, new BooleanCodec()));
        service.register(PiSerializers.STRING, PiSerializers.of(Codec.STRING, new StringCodec()));
        service.register(PiSerializers.UUID, PiSerializers.of(Codec.STRING.xmap(UUID::fromString, UUID::toString), new UuidCodec()));
        service.register(PiSerializers.RESOURCE_LOCATION, PiSerializers.of(ResourceLocation.CODEC, new ResourceLocationCodec()));
        service.register(PiSerializers.COMPOUND_TAG, PiSerializers.of(Codec.PASSTHROUGH.xmap(dynamic -> (CompoundTag) dynamic.convert(net.minecraft.nbt.NbtOps.INSTANCE).getValue(), tag -> new com.mojang.serialization.Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, tag)), new CompoundTagCodec()));
    }

    private static final class VarIntCodec implements org.pickaid.piserializekit.api.packet.PiPacketCodec<Integer> {
        @Override
        public void write(net.minecraft.network.FriendlyByteBuf buffer, Integer value) {
            buffer.writeVarInt(value);
        }

        @Override
        public Integer read(net.minecraft.network.FriendlyByteBuf buffer) {
            return buffer.readVarInt();
        }
    }

    private static final class VarLongCodec implements org.pickaid.piserializekit.api.packet.PiPacketCodec<Long> {
        @Override
        public void write(net.minecraft.network.FriendlyByteBuf buffer, Long value) {
            buffer.writeVarLong(value);
        }

        @Override
        public Long read(net.minecraft.network.FriendlyByteBuf buffer) {
            return buffer.readVarLong();
        }
    }

    private static final class BooleanCodec implements org.pickaid.piserializekit.api.packet.PiPacketCodec<Boolean> {
        @Override
        public void write(net.minecraft.network.FriendlyByteBuf buffer, Boolean value) {
            buffer.writeBoolean(value);
        }

        @Override
        public Boolean read(net.minecraft.network.FriendlyByteBuf buffer) {
            return buffer.readBoolean();
        }
    }

    private static final class StringCodec implements org.pickaid.piserializekit.api.packet.PiPacketCodec<String> {
        @Override
        public void write(net.minecraft.network.FriendlyByteBuf buffer, String value) {
            buffer.writeUtf(value);
        }

        @Override
        public String read(net.minecraft.network.FriendlyByteBuf buffer) {
            return buffer.readUtf();
        }
    }

    private static final class UuidCodec implements org.pickaid.piserializekit.api.packet.PiPacketCodec<UUID> {
        @Override
        public void write(net.minecraft.network.FriendlyByteBuf buffer, UUID value) {
            buffer.writeUUID(value);
        }

        @Override
        public UUID read(net.minecraft.network.FriendlyByteBuf buffer) {
            return buffer.readUUID();
        }
    }

    private static final class ResourceLocationCodec implements org.pickaid.piserializekit.api.packet.PiPacketCodec<ResourceLocation> {
        @Override
        public void write(net.minecraft.network.FriendlyByteBuf buffer, ResourceLocation value) {
            buffer.writeResourceLocation(value);
        }

        @Override
        public ResourceLocation read(net.minecraft.network.FriendlyByteBuf buffer) {
            return buffer.readResourceLocation();
        }
    }

    private static final class CompoundTagCodec implements org.pickaid.piserializekit.api.packet.PiPacketCodec<CompoundTag> {
        @Override
        public void write(net.minecraft.network.FriendlyByteBuf buffer, CompoundTag value) {
            buffer.writeNbt(value);
        }

        @Override
        public CompoundTag read(net.minecraft.network.FriendlyByteBuf buffer) {
            CompoundTag tag = buffer.readNbt();
            return tag == null ? new CompoundTag() : tag;
        }
    }
}
