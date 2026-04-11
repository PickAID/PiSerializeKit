package org.pickaid.piserializekit.runtime.service;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.service.PiSerializeService;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.packet.PiPacketSupport;

public final class PiBuiltInSerializers {
    private static final Codec<ItemStack> ITEM_STACK_VALUE_CODEC = Codec.PASSTHROUGH.comapFlatMap(
            dynamic -> decodeItemStack(dynamic.convert(NbtOps.INSTANCE).getValue()),
            stack -> new Dynamic<>(NbtOps.INSTANCE, stack.save(new CompoundTag()))
    );

    private PiBuiltInSerializers() {
    }

    public static void install(PiSerializeService service) {
        service.register(PiSerializers.BYTE, PiSerializers.of(Codec.BYTE, new ByteCodec()));
        service.register(PiSerializers.SHORT, PiSerializers.of(Codec.SHORT, new ShortCodec()));
        service.register(PiSerializers.INT, PiSerializers.of(Codec.INT, new VarIntCodec()));
        service.register(PiSerializers.LONG, PiSerializers.of(Codec.LONG, new VarLongCodec()));
        service.register(PiSerializers.BOOLEAN, PiSerializers.of(Codec.BOOL, new BooleanCodec()));
        service.register(PiSerializers.FLOAT, PiSerializers.of(Codec.FLOAT, new FloatCodec()));
        service.register(PiSerializers.DOUBLE, PiSerializers.of(Codec.DOUBLE, new DoubleCodec()));
        service.register(PiSerializers.STRING, PiSerializers.of(Codec.STRING, new StringCodec()));
        service.register(PiSerializers.UUID, PiSerializers.of(Codec.STRING.xmap(UUID::fromString, UUID::toString), new UuidCodec()));
        service.register(PiSerializers.RESOURCE_LOCATION, PiSerializers.of(ResourceLocation.CODEC, new ResourceLocationCodec()));
        service.register(
                PiSerializers.COMPOUND_TAG,
                PiSerializers.of(
                        Codec.PASSTHROUGH.xmap(dynamic -> (CompoundTag) dynamic.convert(NbtOps.INSTANCE).getValue(), tag -> new Dynamic<>(NbtOps.INSTANCE, tag)),
                        new CompoundTagCodec()
                )
        );
        service.register(PiSerializers.BLOCK_POS, PiSerializers.codecBacked(BlockPos.CODEC, new BlockPosCodec()));
        service.register(PiSerializers.VEC3, PiSerializers.codecBacked(Vec3.CODEC, new Vec3Codec()));
        service.register(PiSerializers.ITEM_STACK, PiSerializers.codecBacked(ITEM_STACK_VALUE_CODEC, new ItemStackCodec()));
    }

    private static final class ByteCodec implements PiPacketCodec<Byte> {
        @Override
        public void write(FriendlyByteBuf buffer, Byte value) {
            buffer.writeByte(value);
        }

        @Override
        public Byte read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readByte, (byte) 0);
        }
    }

    private static final class ShortCodec implements PiPacketCodec<Short> {
        @Override
        public void write(FriendlyByteBuf buffer, Short value) {
            buffer.writeShort(value);
        }

        @Override
        public Short read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readShort, (short) 0);
        }
    }

    private static final class VarIntCodec implements PiPacketCodec<Integer> {
        @Override
        public void write(FriendlyByteBuf buffer, Integer value) {
            buffer.writeVarInt(value);
        }

        @Override
        public Integer read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readVarInt, 0);
        }
    }

    private static final class VarLongCodec implements PiPacketCodec<Long> {
        @Override
        public void write(FriendlyByteBuf buffer, Long value) {
            buffer.writeVarLong(value);
        }

        @Override
        public Long read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readVarLong, 0L);
        }
    }

    private static final class BooleanCodec implements PiPacketCodec<Boolean> {
        @Override
        public void write(FriendlyByteBuf buffer, Boolean value) {
            buffer.writeBoolean(value);
        }

        @Override
        public Boolean read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readBoolean, false);
        }
    }

    private static final class FloatCodec implements PiPacketCodec<Float> {
        @Override
        public void write(FriendlyByteBuf buffer, Float value) {
            buffer.writeFloat(value);
        }

        @Override
        public Float read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readFloat, 0F);
        }
    }

    private static final class DoubleCodec implements PiPacketCodec<Double> {
        @Override
        public void write(FriendlyByteBuf buffer, Double value) {
            buffer.writeDouble(value);
        }

        @Override
        public Double read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readDouble, 0D);
        }
    }

    private static final class StringCodec implements PiPacketCodec<String> {
        @Override
        public void write(FriendlyByteBuf buffer, String value) {
            buffer.writeUtf(value);
        }

        @Override
        public String read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readUtf, "");
        }
    }

    private static final class UuidCodec implements PiPacketCodec<UUID> {
        @Override
        public void write(FriendlyByteBuf buffer, UUID value) {
            buffer.writeUUID(value);
        }

        @Override
        public UUID read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readUUID, new UUID(0L, 0L));
        }
    }

    private static final class ResourceLocationCodec implements PiPacketCodec<ResourceLocation> {
        @Override
        public void write(FriendlyByteBuf buffer, ResourceLocation value) {
            buffer.writeResourceLocation(value);
        }

        @Override
        public ResourceLocation read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readResourceLocation, null);
        }
    }

    private static final class CompoundTagCodec implements PiPacketCodec<CompoundTag> {
        @Override
        public void write(FriendlyByteBuf buffer, CompoundTag value) {
            buffer.writeNbt(value);
        }

        @Override
        public CompoundTag read(FriendlyByteBuf buffer, PiDecodeContext context) {
            CompoundTag tag = PiPacketSupport.safeRead(context, "", buffer::readNbt, new CompoundTag());
            return tag == null ? new CompoundTag() : tag;
        }
    }

    private static final class BlockPosCodec implements PiPacketCodec<BlockPos> {
        @Override
        public void write(FriendlyByteBuf buffer, BlockPos value) {
            buffer.writeBlockPos(value);
        }

        @Override
        public BlockPos read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readBlockPos, BlockPos.ZERO);
        }
    }

    private static final class Vec3Codec implements PiPacketCodec<Vec3> {
        @Override
        public void write(FriendlyByteBuf buffer, Vec3 value) {
            buffer.writeDouble(value.x);
            buffer.writeDouble(value.y);
            buffer.writeDouble(value.z);
        }

        @Override
        public Vec3 read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(
                    context,
                    "",
                    () -> new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    Vec3.ZERO
            );
        }
    }

    private static final class ItemStackCodec implements PiPacketCodec<ItemStack> {
        @Override
        public void write(FriendlyByteBuf buffer, ItemStack value) {
            buffer.writeItem(value);
        }

        @Override
        public ItemStack read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readItem, ItemStack.EMPTY);
        }
    }

    private static DataResult<ItemStack> decodeItemStack(Tag value) {
        if (!(value instanceof CompoundTag compoundTag)) {
            return DataResult.error(() -> "Expected compound tag for ItemStack");
        }
        if (compoundTag.isEmpty()) {
            return DataResult.success(ItemStack.EMPTY);
        }
        String id = compoundTag.getString("id");
        if (id.isEmpty()) {
            return DataResult.error(() -> "Missing item id for ItemStack");
        }
        ResourceLocation itemId = ResourceLocation.tryParse(id);
        if (itemId == null) {
            return DataResult.error(() -> "Invalid item id for ItemStack: " + id);
        }
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            return DataResult.error(() -> "Unknown item id for ItemStack: " + id);
        }
        return DataResult.success(ItemStack.of(compoundTag.copy()));
    }
}
