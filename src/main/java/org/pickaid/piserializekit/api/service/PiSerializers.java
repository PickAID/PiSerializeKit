package org.pickaid.piserializekit.api.service;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.PiSerializeKit;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;

public final class PiSerializers {
    private static final String ROOT_VALUE_KEY = "__pi_value";

    public static final PiSerializerType<Integer> INT = type("int", Integer.class);
    public static final PiSerializerType<Long> LONG = type("long", Long.class);
    public static final PiSerializerType<Boolean> BOOLEAN = type("boolean", Boolean.class);
    public static final PiSerializerType<String> STRING = type("string", String.class);
    public static final PiSerializerType<UUID> UUID = type("uuid", UUID.class);
    public static final PiSerializerType<ResourceLocation> RESOURCE_LOCATION = type("resource_location", ResourceLocation.class);
    public static final PiSerializerType<CompoundTag> COMPOUND_TAG = type("compound_tag", CompoundTag.class);

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
            public T decode(CompoundTag tag) {
                Tag payload = unwrap(tag);
                return require(codec.parse(NbtOps.INSTANCE, payload), "decode value from NBT");
            }
        };
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
                new PiPacketCodec<>() {
                    @Override
                    public void write(FriendlyByteBuf buffer, T value) {
                        buffer.writeEnum(value);
                    }

                    @Override
                    public T read(FriendlyByteBuf buffer) {
                        return buffer.readEnum(enumType);
                    }
                }
        );
    }

    public static <T> PiSerializer<Optional<T>> optionalOf(PiSerializer<T> element) {
        Objects.requireNonNull(element, "element");
        return of(
                element.valueCodec().optionalFieldOf("value").codec(),
                new PiPacketCodec<>() {
                    @Override
                    public void write(FriendlyByteBuf buffer, Optional<T> value) {
                        buffer.writeBoolean(value.isPresent());
                        value.ifPresent(v -> element.packetCodec().write(buffer, v));
                    }

                    @Override
                    public Optional<T> read(FriendlyByteBuf buffer) {
                        return buffer.readBoolean() ? Optional.of(element.packetCodec().read(buffer)) : Optional.empty();
                    }
                }
        );
    }

    public static <T> PiSerializer<List<T>> listOf(PiSerializer<T> element) {
        Objects.requireNonNull(element, "element");
        return of(
                element.valueCodec().listOf(),
                new PiPacketCodec<>() {
                    @Override
                    public void write(FriendlyByteBuf buffer, List<T> value) {
                        buffer.writeVarInt(value.size());
                        for (T entry : value) {
                            element.packetCodec().write(buffer, entry);
                        }
                    }

                    @Override
                    public List<T> read(FriendlyByteBuf buffer) {
                        int size = buffer.readVarInt();
                        List<T> values = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            values.add(element.packetCodec().read(buffer));
                        }
                        return values;
                    }
                }
        );
    }

    public static <T> PiSerializer<Set<T>> setOf(PiSerializer<T> element) {
        Objects.requireNonNull(element, "element");
        return of(
                element.valueCodec().listOf().xmap(LinkedHashSet::new, ArrayList::new),
                new PiPacketCodec<>() {
                    @Override
                    public void write(FriendlyByteBuf buffer, Set<T> value) {
                        buffer.writeVarInt(value.size());
                        for (T entry : value) {
                            element.packetCodec().write(buffer, entry);
                        }
                    }

                    @Override
                    public Set<T> read(FriendlyByteBuf buffer) {
                        int size = buffer.readVarInt();
                        Set<T> values = new LinkedHashSet<>(size);
                        for (int i = 0; i < size; i++) {
                            values.add(element.packetCodec().read(buffer));
                        }
                        return values;
                    }
                }
        );
    }

    public static <K, V> PiSerializer<Map<K, V>> mapOf(PiSerializer<K> keySerializer, PiSerializer<V> valueSerializer) {
        Objects.requireNonNull(keySerializer, "keySerializer");
        Objects.requireNonNull(valueSerializer, "valueSerializer");
        return of(
                Codec.unboundedMap(keySerializer.valueCodec(), valueSerializer.valueCodec())
                        .xmap(LinkedHashMap::new, LinkedHashMap::new),
                new PiPacketCodec<>() {
                    @Override
                    public void write(FriendlyByteBuf buffer, Map<K, V> value) {
                        buffer.writeVarInt(value.size());
                        for (Map.Entry<K, V> entry : value.entrySet()) {
                            keySerializer.packetCodec().write(buffer, entry.getKey());
                            valueSerializer.packetCodec().write(buffer, entry.getValue());
                        }
                    }

                    @Override
                    public Map<K, V> read(FriendlyByteBuf buffer) {
                        int size = buffer.readVarInt();
                        Map<K, V> values = new LinkedHashMap<>(size);
                        for (int i = 0; i < size; i++) {
                            K key = keySerializer.packetCodec().read(buffer);
                            V value = valueSerializer.packetCodec().read(buffer);
                            values.put(key, value);
                        }
                        return values;
                    }
                }
        );
    }

    private static Tag unwrap(CompoundTag tag) {
        if (tag.contains(ROOT_VALUE_KEY) && tag.getAllKeys().size() == 1) {
            return tag.get(ROOT_VALUE_KEY);
        }
        return tag;
    }

    private static <T> T require(DataResult<T> result, String action) {
        return result.resultOrPartial(message -> {
        }).orElseThrow(() -> new IllegalStateException("Failed to " + action + ": " + result.error().map(DataResult.PartialResult::message).orElse("unknown error")));
    }
}
