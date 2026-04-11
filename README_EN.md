# PiSerializeKit

[中文版](README.MD)

`PiSerializeKit` is the independent serialization foundation for the Pi stack.

It gives the Pi repos one shared language for value codecs, NBT codecs, and packet codecs, instead of letting every mod invent another serialization layer.

It should also become the typed serialization base for `PiDataGraph` counters, reaction states, and graph node payloads.

## Start-Stage Goals

1. provide stable serializer, type-key, and service lookup contracts;
2. give `Pibrary`, `PiNet`, `PiKubeJSCompat`, and future engine repos one shared serialization boundary;
3. keep the repo independent instead of folding it back into the `Pibrary` monolith;
4. converge state schema and packet schema onto one field language.

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
7. `@PiSyncModel` / `@PiField`
   compile-time state bindings with projections, deltas, and schema migrations.
8. `@PiPacket` / `@PiPacketNamespace` / `@PiPacketUpgrade`
   compile-time packet bindings, providers, packet ids, packet versions, and packet migration chains.
9. `PiSchemas` / `PiPackets`
   `ServiceLoader`-backed runtime registries for schema and packet bindings, including packet-id lookup.
10. `PiDecodeContext`
    structured decode diagnostics with field paths, fatal flags, and migration failure reporting.

## Explicitly Not Heavy Yet

This repo still does not own:
1. channel installation, send targets, thread routing, or transport guards;
2. capability / host runtime wiring and gameplay service lifecycles;
3. higher-level UI / render / world author magic;
4. reflective black-box read/write generators;
5. engine-specific heavyweight runtime packaging.

## Repo Role

1. this is an independent foundation repo;
2. multiple Pi repos should be able to consume it directly;
3. it owns how data models are described, upgraded, diagnosed, and bound;
4. `PiNet` owns how packets are transported, routed, and guarded;
5. `Pibrary` and later packs own how those schemas and packets enter concrete gameplay hosts.
