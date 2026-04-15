package org.pickaid.piserializekit.api.service;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.pickaid.piserializekit.PiSerializeKit;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketCodecs;
import org.pickaid.piserializekit.api.packet.buffer.PiPortablePacketCodec;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.runtime.packet.PiPacketSupport;

public final class PiSerializers {
    private static final String ROOT_VALUE_KEY = "__pi_value";

    public static final PiSerializerType<Byte> BYTE = type("byte", Byte.class);
    public static final PiSerializerType<Short> SHORT = type("short", Short.class);
    public static final PiSerializerType<Integer> INT = type("int", Integer.class);
    public static final PiSerializerType<Long> LONG = type("long", Long.class);
    public static final PiSerializerType<Boolean> BOOLEAN = type("boolean", Boolean.class);
    public static final PiSerializerType<Float> FLOAT = type("float", Float.class);
    public static final PiSerializerType<Double> DOUBLE = type("double", Double.class);
    public static final PiSerializerType<String> STRING = type("string", String.class);
    public static final PiSerializerType<UUID> UUID = type("uuid", UUID.class);
    public static final PiSerializerType<ResourceLocation> RESOURCE_LOCATION = type("resource_location", ResourceLocation.class);
    public static final PiSerializerType<CompoundTag> COMPOUND_TAG = type("compound_tag", CompoundTag.class);
    public static final PiSerializerType<BlockPos> BLOCK_POS = type("block_pos", BlockPos.class);
    public static final PiSerializerType<Vec3> VEC3 = type("vec3", Vec3.class);
    public static final PiSerializerType<ItemStack> ITEM_STACK = type("item_stack", ItemStack.class);

    private PiSerializers() {
    }

    public static <T> PiSerializerType<T> type(String path, Class<T> javaType) {
        return new PiSerializerType<>(PiSerializeKit.id(path), javaType);
    }

    public static <T> PiSerializer<T> of(Codec<T> codec, PiPacketCodec<T> packetCodec) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(packetCodec, "packetCodec");
        PiNbtCodec<T> nbtCodec = new PiNbtCodec<>() {
            @Override
            public CompoundTag encode(T value) {
                Tag encoded = require(codec.encodeStart(NbtOps.INSTANCE, value), "encode value to NBT");
                if (encoded instanceof CompoundTag compoundTag) {
                    return compoundTag.copy();
                }
                CompoundTag wrapped = new CompoundTag();
                wrapped.put(ROOT_VALUE_KEY, encoded);
                return wrapped;
            }

            @Override
            public Tag encodeTag(T value) {
                Tag encoded = require(codec.encodeStart(NbtOps.INSTANCE, value), "encode value to NBT");
                return encoded.copy();
            }

            @Override
            public T decode(CompoundTag tag) {
                Tag payload = unwrap(tag);
                return require(codec.parse(NbtOps.INSTANCE, payload), "decode value from NBT");
            }

            @Override
            public T decodeTag(Tag tag) {
                Tag payload = tag instanceof CompoundTag compoundTag ? unwrap(compoundTag) : tag;
                return require(codec.parse(NbtOps.INSTANCE, payload), "decode value from NBT");
            }
        };
        return of(codec, nbtCodec, packetCodec);
    }

    public static <T> PiSerializer<T> of(Codec<T> codec, PiPortablePacketCodec<T> packetCodec) {
        Objects.requireNonNull(packetCodec, "packetCodec");
        return of(codec, PiPacketCodecs.fromPortable(packetCodec));
    }

    public static <T> PiSerializer<T> of(Codec<T> codec, PiNbtCodec<T> nbtCodec, PiPacketCodec<T> packetCodec) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(nbtCodec, "nbtCodec");
        Objects.requireNonNull(packetCodec, "packetCodec");
        return new PiSerializer<>() {
            @Override
            public Codec<T> valueCodec() {
                return codec;
            }

            @Override
            public PiNbtCodec<T> nbtCodec() {
                return nbtCodec;
            }

            @Override
            public PiPacketCodec<T> packetCodec() {
                return packetCodec;
            }
        };
    }

    public static <T> PiSerializer<T> of(Codec<T> codec, PiNbtCodec<T> nbtCodec, PiPortablePacketCodec<T> packetCodec) {
        Objects.requireNonNull(packetCodec, "packetCodec");
        return of(codec, nbtCodec, PiPacketCodecs.fromPortable(packetCodec));
    }

    public static <T> PiSerializer<T> codecBacked(Codec<T> codec, PiPacketCodec<T> packetCodec) {
        return of(codec, packetCodec);
    }

    public static <T> PiSerializer<T> codecBacked(Codec<T> codec, PiPortablePacketCodec<T> packetCodec) {
        Objects.requireNonNull(packetCodec, "packetCodec");
        return of(codec, packetCodec);
    }

    public static <T extends Enum<T>> PiSerializer<T> enumType(Class<T> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        return of(
                Codec.STRING.comapFlatMap(
                        name -> {
                            try {
                                return DataResult.success(Enum.valueOf(enumType, name));
                            } catch (IllegalArgumentException ex) {
                                return DataResult.error(() -> "Unknown enum constant " + name + " for " + enumType.getName());
                            }
                        },
                        Enum::name
                ),
                new PiPortablePacketCodec<>() {
                    @Override
                    public void write(PiPacketBuffer buffer, T value) {
                        buffer.writeEnum(value);
                    }

                    @Override
                    public T read(PiPacketBuffer buffer, PiDecodeContext context) {
                        T[] constants = enumType.getEnumConstants();
                        T fallback = constants.length == 0 ? null : constants[0];
                        return PiPacketSupport.safeRead(context, "", () -> buffer.readEnum(enumType), fallback);
                    }
                }
        );
    }

    public static <T> PiSerializer<T[]> arrayOf(Class<T[]> arrayType, PiSerializer<T> element) {
        Objects.requireNonNull(arrayType, "arrayType");
        Objects.requireNonNull(element, "element");
        if (!arrayType.isArray()) {
            throw new IllegalArgumentException("arrayType must be an array class: " + arrayType.getName());
        }
        Class<?> componentType = arrayType.getComponentType();
        Codec<T[]> codec = element.valueCodec().listOf().xmap(
                values -> toArray(arrayType, componentType, values),
                values -> new ArrayList<>(Arrays.asList(values))
        );
        PiNbtCodec<T[]> nbtCodec = new PiNbtCodec<>() {
            @Override
            public CompoundTag encode(T[] value) {
                CompoundTag wrapped = new CompoundTag();
                wrapped.put(ROOT_VALUE_KEY, encodeListPayload(element, Arrays.asList(value)));
                return wrapped;
            }

            @Override
            public T[] decode(CompoundTag tag) {
                return decodeTag(unwrap(tag));
            }

            @Override
            public T[] decodeTag(Tag tag) {
                return toArray(arrayType, componentType, decodeListPayload(tag, element));
            }
        };
        return serializer(codec, nbtCodec, new PiPortablePacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, T[] value) {
                buffer.writeVarInt(value.length);
                for (T entry : value) {
                    element.portablePacketCodec().write(buffer, entry);
                }
            }

            @Override
            public T[] read(PiPacketBuffer buffer, PiDecodeContext context) {
                int size = PiPacketSupport.safeRead(context, "", buffer::readVarInt, 0);
                List<T> values = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    T decoded = element.portablePacketCodec().read(buffer, context.child("[" + i + "]"));
                    if (decoded != null) {
                        values.add(decoded);
                    }
                }
                return toArray(arrayType, componentType, values);
            }
        });
    }

    public static <T> PiSerializer<Optional<T>> optionalOf(PiSerializer<T> element) {
        Objects.requireNonNull(element, "element");
        return of(
                element.valueCodec().optionalFieldOf("value").codec(),
                new PiPortablePacketCodec<>() {
                    @Override
                    public void write(PiPacketBuffer buffer, Optional<T> value) {
                        buffer.writeBoolean(value.isPresent());
                        value.ifPresent(v -> element.portablePacketCodec().write(buffer, v));
                    }

                    @Override
                    public Optional<T> read(PiPacketBuffer buffer, PiDecodeContext context) {
                        boolean present = PiPacketSupport.safeRead(context, "", buffer::readBoolean, false);
                        return present
                                ? Optional.ofNullable(element.portablePacketCodec().read(buffer, context.child("value")))
                                : Optional.empty();
                    }
                }
        );
    }

    public static <T> PiSerializer<List<T>> listOf(PiSerializer<T> element) {
        Objects.requireNonNull(element, "element");
        Codec<List<T>> codec = element.valueCodec().listOf();
        PiNbtCodec<List<T>> nbtCodec = new PiNbtCodec<>() {
            @Override
            public CompoundTag encode(List<T> value) {
                CompoundTag wrapped = new CompoundTag();
                wrapped.put(ROOT_VALUE_KEY, encodeListPayload(element, value));
                return wrapped;
            }

            @Override
            public List<T> decode(CompoundTag tag) {
                return decodeTag(unwrap(tag));
            }

            @Override
            public List<T> decodeTag(Tag tag) {
                return decodeListPayload(tag, element);
            }

            @Override
            public List<T> decodeInto(CompoundTag tag, List<T> current) {
                return decodeIntoTag(unwrap(tag), current);
            }

            @Override
            public List<T> decodeIntoTag(Tag tag, List<T> current) {
                return decodeListPayloadInto(tag, element, current);
            }
        };
        return serializer(codec, nbtCodec, new PiPortablePacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, List<T> value) {
                buffer.writeVarInt(value.size());
                for (T entry : value) {
                    element.portablePacketCodec().write(buffer, entry);
                }
            }

            @Override
            public List<T> read(PiPacketBuffer buffer, PiDecodeContext context) {
                int size = PiPacketSupport.safeRead(context, "", buffer::readVarInt, 0);
                List<T> values = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    T decoded = element.portablePacketCodec().read(buffer, context.child("[" + i + "]"));
                    if (decoded != null) {
                        values.add(decoded);
                    }
                }
                return values;
            }
        });
    }

    public static <T> PiSerializer<Set<T>> setOf(PiSerializer<T> element) {
        Objects.requireNonNull(element, "element");
        Codec<Set<T>> codec = element.valueCodec().listOf().xmap(LinkedHashSet::new, ArrayList::new);
        PiNbtCodec<Set<T>> nbtCodec = new PiNbtCodec<>() {
            @Override
            public CompoundTag encode(Set<T> value) {
                CompoundTag wrapped = new CompoundTag();
                wrapped.put(ROOT_VALUE_KEY, encodeListPayload(element, value));
                return wrapped;
            }

            @Override
            public Set<T> decode(CompoundTag tag) {
                return decodeTag(unwrap(tag));
            }

            @Override
            public Set<T> decodeTag(Tag tag) {
                return new LinkedHashSet<>(decodeListPayload(tag, element));
            }

            @Override
            public Set<T> decodeInto(CompoundTag tag, Set<T> current) {
                return decodeIntoTag(unwrap(tag), current);
            }

            @Override
            public Set<T> decodeIntoTag(Tag tag, Set<T> current) {
                Set<T> target = current != null ? current : new LinkedHashSet<>();
                target.clear();
                target.addAll(decodeListPayload(tag, element));
                return target;
            }
        };
        return serializer(codec, nbtCodec, new PiPortablePacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, Set<T> value) {
                buffer.writeVarInt(value.size());
                for (T entry : value) {
                    element.portablePacketCodec().write(buffer, entry);
                }
            }

            @Override
            public Set<T> read(PiPacketBuffer buffer, PiDecodeContext context) {
                int size = PiPacketSupport.safeRead(context, "", buffer::readVarInt, 0);
                Set<T> values = new LinkedHashSet<>(size);
                for (int i = 0; i < size; i++) {
                    T decoded = element.portablePacketCodec().read(buffer, context.child("[" + i + "]"));
                    if (decoded != null) {
                        values.add(decoded);
                    }
                }
                return values;
            }
        });
    }

    public static <K, V> PiSerializer<Map<K, V>> mapOf(PiSerializer<K> keySerializer, PiSerializer<V> valueSerializer) {
        Objects.requireNonNull(keySerializer, "keySerializer");
        Objects.requireNonNull(valueSerializer, "valueSerializer");
        Codec<Map<K, V>> codec = Codec.unboundedMap(keySerializer.valueCodec(), valueSerializer.valueCodec())
                .xmap(LinkedHashMap::new, LinkedHashMap::new);
        PiNbtCodec<Map<K, V>> nbtCodec = new PiNbtCodec<>() {
            @Override
            public CompoundTag encode(Map<K, V> value) {
                CompoundTag direct = tryEncodeCompoundMapPayload(value, keySerializer, valueSerializer);
                if (direct != null) {
                    return direct;
                }
                return wrapEncodedTag(encodeWithCodec(codec, value));
            }

            @Override
            public Map<K, V> decode(CompoundTag tag) {
                return decodeTag(unwrap(tag));
            }

            @Override
            public Map<K, V> decodeTag(Tag tag) {
                Map<K, V> direct = tryDecodeCompoundMapPayload(tag, keySerializer, valueSerializer);
                return direct != null ? direct : decodeWithCodec(codec, tag);
            }

            @Override
            public Map<K, V> decodeInto(CompoundTag tag, Map<K, V> current) {
                return decodeIntoTag(unwrap(tag), current);
            }

            @Override
            public Map<K, V> decodeIntoTag(Tag tag, Map<K, V> current) {
                Map<K, V> target = current != null ? current : new LinkedHashMap<>();
                if (tryDecodeCompoundMapPayloadInto(tag, keySerializer, valueSerializer, target)) {
                    return target;
                }
                Map<K, V> decoded = decodeWithCodec(codec, tag);
                target.clear();
                target.putAll(decoded);
                return target;
            }
        };
        return serializer(codec, nbtCodec, new PiPortablePacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, Map<K, V> value) {
                buffer.writeVarInt(value.size());
                for (Map.Entry<K, V> entry : value.entrySet()) {
                    keySerializer.portablePacketCodec().write(buffer, entry.getKey());
                    valueSerializer.portablePacketCodec().write(buffer, entry.getValue());
                }
            }

            @Override
            public Map<K, V> read(PiPacketBuffer buffer, PiDecodeContext context) {
                int size = PiPacketSupport.safeRead(context, "", buffer::readVarInt, 0);
                Map<K, V> values = new LinkedHashMap<>(size);
                for (int i = 0; i < size; i++) {
                    K key = keySerializer.portablePacketCodec().read(buffer, context.child("[" + i + "].key"));
                    V value = valueSerializer.portablePacketCodec().read(buffer, context.child("[" + i + "].value"));
                    if (key != null && value != null) {
                        values.put(key, value);
                    }
                }
                return values;
            }
        });
    }

    private static Tag unwrap(CompoundTag tag) {
        if (tag.contains(ROOT_VALUE_KEY) && tag.getAllKeys().size() == 1) {
            return tag.get(ROOT_VALUE_KEY);
        }
        return tag;
    }

    private static <T> PiSerializer<T> serializer(Codec<T> codec, PiNbtCodec<T> nbtCodec, PiPacketCodec<T> packetCodec) {
        return new PiSerializer<>() {
            @Override
            public Codec<T> valueCodec() {
                return codec;
            }

            @Override
            public PiNbtCodec<T> nbtCodec() {
                return nbtCodec;
            }

            @Override
            public PiPacketCodec<T> packetCodec() {
                return packetCodec;
            }
        };
    }

    private static <T> PiSerializer<T> serializer(Codec<T> codec, PiNbtCodec<T> nbtCodec, PiPortablePacketCodec<T> packetCodec) {
        return serializer(codec, nbtCodec, PiPacketCodecs.fromPortable(packetCodec));
    }

    private static <T> ListTag encodeListPayload(PiSerializer<T> element, Iterable<T> values) {
        ListTag listTag = new ListTag();
        for (T value : values) {
            listTag.add(encodePayloadTag(element, value));
        }
        return listTag;
    }

    private static <T> List<T> decodeListPayload(Tag tag, PiSerializer<T> element) {
        return decodeListPayloadInto(tag, element, new ArrayList<>());
    }

    private static <T> List<T> decodeListPayloadInto(Tag tag, PiSerializer<T> element, List<T> target) {
        Tag payload = tag instanceof CompoundTag compoundTag ? unwrap(compoundTag) : tag;
        if (!(payload instanceof ListTag listTag)) {
            throw new IllegalStateException("Expected list tag");
        }
        List<T> values = target != null ? target : new ArrayList<>(listTag.size());
        values.clear();
        for (int i = 0; i < listTag.size(); i++) {
            values.add(element.nbtCodec().decodeTag(listTag.get(i)));
        }
        return values;
    }

    private static <T> Tag encodePayloadTag(PiSerializer<T> serializer, T value) {
        return serializer.nbtCodec().encodeTag(value);
    }

    private static <T> Tag encodeWithCodec(Codec<T> codec, T value) {
        return require(codec.encodeStart(NbtOps.INSTANCE, value), "encode value to NBT").copy();
    }

    private static <T> T decodeWithCodec(Codec<T> codec, Tag payload) {
        return require(codec.parse(NbtOps.INSTANCE, payload), "decode value from NBT");
    }

    private static CompoundTag wrapEncodedTag(Tag payload) {
        if (payload instanceof CompoundTag compoundTag) {
            return compoundTag.copy();
        }
        CompoundTag wrapped = new CompoundTag();
        wrapped.put(ROOT_VALUE_KEY, payload.copy());
        return wrapped;
    }

    private static <K, V> CompoundTag tryEncodeCompoundMapPayload(
            Map<K, V> value,
            PiSerializer<K> keySerializer,
            PiSerializer<V> valueSerializer
    ) {
        CompoundTag payload = new CompoundTag();
        for (Map.Entry<K, V> entry : value.entrySet()) {
            Tag keyTag = keySerializer.nbtCodec().encodeTag(entry.getKey());
            if (!(keyTag instanceof StringTag stringTag)) {
                return null;
            }
            payload.put(stringTag.getAsString(), valueSerializer.nbtCodec().encodeTag(entry.getValue()));
        }
        return payload;
    }

    private static <K, V> Map<K, V> tryDecodeCompoundMapPayload(
            Tag tag,
            PiSerializer<K> keySerializer,
            PiSerializer<V> valueSerializer
    ) {
        Map<K, V> values = new LinkedHashMap<>();
        return tryDecodeCompoundMapPayloadInto(tag, keySerializer, valueSerializer, values) ? values : null;
    }

    private static <K, V> boolean tryDecodeCompoundMapPayloadInto(
            Tag tag,
            PiSerializer<K> keySerializer,
            PiSerializer<V> valueSerializer,
            Map<K, V> target
    ) {
        Tag payload = tag instanceof CompoundTag compoundTag ? unwrap(compoundTag) : tag;
        if (!(payload instanceof CompoundTag compoundTag)) {
            return false;
        }
        target.clear();
        try {
            for (String key : compoundTag.getAllKeys()) {
                K decodedKey = keySerializer.nbtCodec().decodeTag(StringTag.valueOf(key));
                V decodedValue = valueSerializer.nbtCodec().decodeTag(compoundTag.get(key));
                target.put(decodedKey, decodedValue);
            }
            return true;
        } catch (RuntimeException exception) {
            target.clear();
            return false;
        }
    }

    private static <T> T[] toArray(Class<T[]> arrayType, Class<?> componentType, List<T> values) {
        T[] array = newArray(componentType, values.size());
        return values.toArray(array);
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] newArray(Class<?> componentType, int size) {
        return (T[]) Array.newInstance(componentType, size);
    }

    private static <T> T require(DataResult<T> result, String action) {
        return result.resultOrPartial(message -> {
        }).orElseThrow(() -> new IllegalStateException("Failed to " + action + ": " + result.error().map(DataResult.PartialResult::message).orElse("unknown error")));
    }
}
