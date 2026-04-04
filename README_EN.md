# PiSerializeKit

[中文版](README.MD)

`PiSerializeKit` is the independent serialization foundation for the Pi stack.

It gives the Pi repos one shared language for value codecs, NBT codecs, and packet codecs, instead of letting every mod invent another serialization layer.

It should also become the typed serialization base for `PiDataGraph` counters, reaction states, and graph node payloads.

## Start-Stage Goals

1. provide stable serializer, type-key, and service lookup contracts;
2. give `Pibrary`, `PiNet`, `PiKubeJSCompat`, and future engine repos one shared serialization boundary;
3. keep the repo independent instead of folding it back into the `Pibrary` monolith;
4. get the contracts right before committing to derivation, migration, or schema-evolution systems.

## Current Surface

1. `PiSerializer`
   groups value codec, NBT codec, and packet codec.
2. `PiNbtCodec`
   `CompoundTag` serialization contract.
3. `PiPacketCodec`
   `FriendlyByteBuf` serialization contract.
4. `PiSerializerType`
   stable serializer identity.
5. `PiSerializeService`
   unified registration and lookup surface.
6. `PiSerializeServices`
   installation and access point for the active runtime service.

## Explicitly Not Heavy Yet

This stage does not directly implement:
1. automatic serializer derivation;
2. schema migration systems;
3. compatibility chains across versions;
4. a heavyweight engine-specific serialization toolkit;
5. reflective black-box read/write generators.

## Repo Role

1. this is an independent foundation repo;
2. multiple Pi repos should be able to consume it directly;
3. the first priority is clean data boundaries, not premature runtime complexity.
