package org.pickaid.piserializekit.api.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public interface PiNbtCodec<T> {
    String ROOT_VALUE_KEY = "__pi_value";

    CompoundTag encode(T value);

    default Tag encodeTag(T value) {
        CompoundTag encoded = encode(value);
        if (encoded.contains(ROOT_VALUE_KEY) && encoded.getAllKeys().size() == 1) {
            Tag raw = encoded.get(ROOT_VALUE_KEY);
            return raw == null ? new CompoundTag() : raw.copy();
        }
        return encoded.copy();
    }

    T decode(CompoundTag tag);

    default T decodeInto(CompoundTag tag, T current) {
        return decode(tag);
    }

    default T decodeTag(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            return decode(compoundTag);
        }
        CompoundTag wrapped = new CompoundTag();
        wrapped.put(ROOT_VALUE_KEY, tag);
        return decode(wrapped);
    }

    default T decodeIntoTag(Tag tag, T current) {
        if (tag instanceof CompoundTag compoundTag) {
            return decodeInto(compoundTag, current);
        }
        CompoundTag wrapped = new CompoundTag();
        wrapped.put(ROOT_VALUE_KEY, tag);
        return decodeInto(wrapped, current);
    }
}
