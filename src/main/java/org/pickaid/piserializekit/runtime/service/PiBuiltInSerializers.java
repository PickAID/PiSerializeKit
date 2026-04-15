package org.pickaid.piserializekit.runtime.service;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer;
import org.pickaid.piserializekit.api.packet.buffer.PiPortablePacketCodec;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.api.service.PiSerializeService;
import org.pickaid.piserializekit.api.service.PiSerializerType;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.packet.PiPacketSupport;

public final class PiBuiltInSerializers {
    private static final Codec<ItemStack> ITEM_STACK_VALUE_CODEC = Codec.PASSTHROUGH.comapFlatMap(
            dynamic -> decodeItemStack(dynamic.convert(NbtOps.INSTANCE).getValue()),
            stack -> new Dynamic<>(NbtOps.INSTANCE, stack.save(new CompoundTag()))
    );

    private PiBuiltInSerializers() {
    }

    /**
     * Ensures the built-in serializer set is present without clobbering existing matching ids.
     */
    public static void install(PiSerializeService service) {
        installMissing(service, PiSerializers.BYTE, PiSerializers.of(Codec.BYTE, scalarNbtCodec(ByteTag::valueOf, tag -> requireNumeric(tag, "byte").getAsByte()), new ByteCodec()));
        installMissing(service, PiSerializers.SHORT, PiSerializers.of(Codec.SHORT, scalarNbtCodec(ShortTag::valueOf, tag -> requireNumeric(tag, "short").getAsShort()), new ShortCodec()));
        installMissing(service, PiSerializers.INT, PiSerializers.of(Codec.INT, scalarNbtCodec(IntTag::valueOf, tag -> requireNumeric(tag, "int").getAsInt()), new VarIntCodec()));
        installMissing(service, PiSerializers.LONG, PiSerializers.of(Codec.LONG, scalarNbtCodec(LongTag::valueOf, tag -> requireNumeric(tag, "long").getAsLong()), new VarLongCodec()));
        installMissing(service, PiSerializers.BOOLEAN, PiSerializers.of(Codec.BOOL, scalarNbtCodec(value -> ByteTag.valueOf(value), tag -> requireNumeric(tag, "boolean").getAsByte() != 0), new BooleanCodec()));
        installMissing(service, PiSerializers.FLOAT, PiSerializers.of(Codec.FLOAT, scalarNbtCodec(FloatTag::valueOf, tag -> requireNumeric(tag, "float").getAsFloat()), new FloatCodec()));
        installMissing(service, PiSerializers.DOUBLE, PiSerializers.of(Codec.DOUBLE, scalarNbtCodec(DoubleTag::valueOf, tag -> requireNumeric(tag, "double").getAsDouble()), new DoubleCodec()));
        installMissing(service, PiSerializers.STRING, PiSerializers.of(Codec.STRING, scalarNbtCodec(StringTag::valueOf, tag -> requireString(tag, "string")), new StringCodec()));
        installMissing(
                service,
                PiSerializers.UUID,
                PiSerializers.of(
                        Codec.STRING.xmap(UUID::fromString, UUID::toString),
                        scalarNbtCodec(value -> StringTag.valueOf(value.toString()), tag -> UUID.fromString(requireString(tag, "uuid"))),
                        new UuidCodec()
                )
        );
        installMissing(
                service,
                PiSerializers.RESOURCE_LOCATION,
                PiSerializers.of(
                        ResourceLocation.CODEC,
                        scalarNbtCodec(
                                value -> StringTag.valueOf(value.toString()),
                                tag -> requireResourceLocation(requireString(tag, "resource location"))
                        ),
                        new ResourceLocationCodec()
                )
        );
        installMissing(
                service,
                PiSerializers.COMPOUND_TAG,
                PiSerializers.of(
                        Codec.PASSTHROUGH.xmap(dynamic -> (CompoundTag) dynamic.convert(NbtOps.INSTANCE).getValue(), tag -> new Dynamic<>(NbtOps.INSTANCE, tag)),
                        directNbtCodec(
                                value -> value.copy(),
                                tag -> {
                                    if (tag instanceof CompoundTag compoundTag) {
                                        return compoundTag.copy();
                                    }
                                    throw new IllegalStateException("Expected compound tag");
                                }
                        ),
                        new CompoundTagCodec()
                )
        );
        installMissing(service, PiSerializers.BLOCK_POS, PiSerializers.codecBacked(BlockPos.CODEC, new BlockPosCodec()));
        installMissing(service, PiSerializers.VEC3, PiSerializers.codecBacked(Vec3.CODEC, new Vec3Codec()));
        installMissing(service, PiSerializers.ITEM_STACK, PiSerializers.codecBacked(ITEM_STACK_VALUE_CODEC, new ItemStackCodec()));
    }

    /**
     * Registers one built-in serializer only when the target id and java type are still absent.
     */
    private static <T> void installMissing(PiSerializeService service, PiSerializerType<T> type, PiSerializer<T> serializer) {
        if (service.lookup(type).isPresent()) {
            return;
        }
        service.register(type, serializer);
    }

    private static final class ByteCodec implements PiPortablePacketCodec<Byte> {
        @Override
        public void write(PiPacketBuffer buffer, Byte value) {
            buffer.writeByte(value);
        }

        @Override
        public Byte read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readByte, (byte) 0);
        }
    }

    private static final class ShortCodec implements PiPortablePacketCodec<Short> {
        @Override
        public void write(PiPacketBuffer buffer, Short value) {
            buffer.writeShort(value);
        }

        @Override
        public Short read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readShort, (short) 0);
        }
    }

    private static final class VarIntCodec implements PiPortablePacketCodec<Integer> {
        @Override
        public void write(PiPacketBuffer buffer, Integer value) {
            buffer.writeVarInt(value);
        }

        @Override
        public Integer read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readVarInt, 0);
        }
    }

    private static final class VarLongCodec implements PiPortablePacketCodec<Long> {
        @Override
        public void write(PiPacketBuffer buffer, Long value) {
            buffer.writeVarLong(value);
        }

        @Override
        public Long read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readVarLong, 0L);
        }
    }

    private static final class BooleanCodec implements PiPortablePacketCodec<Boolean> {
        @Override
        public void write(PiPacketBuffer buffer, Boolean value) {
            buffer.writeBoolean(value);
        }

        @Override
        public Boolean read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readBoolean, false);
        }
    }

    private static final class FloatCodec implements PiPortablePacketCodec<Float> {
        @Override
        public void write(PiPacketBuffer buffer, Float value) {
            buffer.writeFloat(value);
        }

        @Override
        public Float read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readFloat, 0F);
        }
    }

    private static final class DoubleCodec implements PiPortablePacketCodec<Double> {
        @Override
        public void write(PiPacketBuffer buffer, Double value) {
            buffer.writeDouble(value);
        }

        @Override
        public Double read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readDouble, 0D);
        }
    }

    private static final class StringCodec implements PiPortablePacketCodec<String> {
        @Override
        public void write(PiPacketBuffer buffer, String value) {
            buffer.writeUtf(value);
        }

        @Override
        public String read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readUtf, "");
        }
    }

    private static final class UuidCodec implements PiPortablePacketCodec<UUID> {
        @Override
        public void write(PiPacketBuffer buffer, UUID value) {
            buffer.writeUuid(value);
        }

        @Override
        public UUID read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readUuid, new UUID(0L, 0L));
        }
    }

    private static final class ResourceLocationCodec implements PiPortablePacketCodec<ResourceLocation> {
        @Override
        public void write(PiPacketBuffer buffer, ResourceLocation value) {
            buffer.writeResourceLocation(value);
        }

        @Override
        public ResourceLocation read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readResourceLocation, null);
        }
    }

    private static final class CompoundTagCodec implements PiPortablePacketCodec<CompoundTag> {
        @Override
        public void write(PiPacketBuffer buffer, CompoundTag value) {
            buffer.writeNbt(value);
        }

        @Override
        public CompoundTag read(PiPacketBuffer buffer, PiDecodeContext context) {
            CompoundTag tag = PiPacketSupport.safeRead(context, "", buffer::readNbt, new CompoundTag());
            return tag == null ? new CompoundTag() : tag;
        }
    }

    private static final class BlockPosCodec implements PiPortablePacketCodec<BlockPos> {
        @Override
        public void write(PiPacketBuffer buffer, BlockPos value) {
            buffer.writeBlockPos(value);
        }

        @Override
        public BlockPos read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(context, "", buffer::readBlockPos, BlockPos.ZERO);
        }
    }

    private static final class Vec3Codec implements PiPortablePacketCodec<Vec3> {
        @Override
        public void write(PiPacketBuffer buffer, Vec3 value) {
            buffer.writeDouble(value.x);
            buffer.writeDouble(value.y);
            buffer.writeDouble(value.z);
        }

        @Override
        public Vec3 read(PiPacketBuffer buffer, PiDecodeContext context) {
            return PiPacketSupport.safeRead(
                    context,
                    "",
                    () -> new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    Vec3.ZERO
            );
        }
    }

    private static final class ItemStackCodec implements PiPortablePacketCodec<ItemStack> {
        @Override
        public void write(PiPacketBuffer buffer, ItemStack value) {
            buffer.writeNbt(value.save(new CompoundTag()));
        }

        @Override
        public ItemStack read(PiPacketBuffer buffer, PiDecodeContext context) {
            CompoundTag tag = PiPacketSupport.safeRead(context, "", buffer::readNbt, new CompoundTag());
            if (tag == null || tag.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return PiPacketSupport.safeRead(context, "", () -> ItemStack.of(tag.copy()), ItemStack.EMPTY);
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
        if (!containsRegisteredItem(itemId)) {
            return DataResult.error(() -> "Unknown item id for ItemStack: " + id);
        }
        return DataResult.success(ItemStack.of(compoundTag.copy()));
    }

    @SuppressWarnings("deprecation")
    private static boolean containsRegisteredItem(ResourceLocation itemId) {
        // Forge 1.20.1 still routes direct built-in item existence checks through this deprecated constant.
        // Keep the use isolated here so serializer compilation stays clean while preserving strict unknown-id diagnostics.
        return BuiltInRegistries.ITEM.containsKey(itemId);
    }

    private static <T> PiNbtCodec<T> scalarNbtCodec(java.util.function.Function<T, Tag> encoder, java.util.function.Function<Tag, T> decoder) {
        return directNbtCodec(encoder, decoder);
    }

    private static <T> PiNbtCodec<T> directNbtCodec(java.util.function.Function<T, Tag> encoder, java.util.function.Function<Tag, T> decoder) {
        Objects.requireNonNull(encoder, "encoder");
        Objects.requireNonNull(decoder, "decoder");
        return new PiNbtCodec<>() {
            @Override
            public CompoundTag encode(T value) {
                return wrapTag(encodeTag(value));
            }

            @Override
            public Tag encodeTag(T value) {
                return encoder.apply(value).copy();
            }

            @Override
            public T decode(CompoundTag tag) {
                return decodeTag(unwrapTag(tag));
            }

            @Override
            public T decodeTag(Tag tag) {
                if (tag instanceof CompoundTag compoundTag) {
                    return decoder.apply(unwrapTag(compoundTag));
                }
                return decoder.apply(tag);
            }

            @Override
            public T decodeInto(CompoundTag tag, T current) {
                return decode(tag);
            }

            @Override
            public T decodeIntoTag(Tag tag, T current) {
                return decodeTag(tag);
            }
        };
    }

    private static CompoundTag wrapTag(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            return compoundTag.copy();
        }
        CompoundTag wrapped = new CompoundTag();
        wrapped.put(PiNbtCodec.ROOT_VALUE_KEY, tag.copy());
        return wrapped;
    }

    private static Tag unwrapTag(CompoundTag tag) {
        if (tag.contains(PiNbtCodec.ROOT_VALUE_KEY) && tag.getAllKeys().size() == 1) {
            Tag payload = tag.get(PiNbtCodec.ROOT_VALUE_KEY);
            return payload == null ? new CompoundTag() : payload;
        }
        return tag;
    }

    private static NumericTag requireNumeric(Tag tag, String label) {
        if (tag instanceof NumericTag numericTag) {
            return numericTag;
        }
        throw new IllegalStateException("Expected numeric tag for " + label);
    }

    private static String requireString(Tag tag, String label) {
        if (tag instanceof StringTag stringTag) {
            return stringTag.getAsString();
        }
        throw new IllegalStateException("Expected string tag for " + label);
    }

    private static ResourceLocation requireResourceLocation(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new IllegalStateException("Invalid resource location: " + value);
        }
        return location;
    }
}
