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
11. `PiRuntimeLookupException` / `PiRuntimeConflictException` / `PiRuntimeBootstrapException`
    one runtime exception surface for missing bindings, conflicting registrations, and provider bootstrap failures, with minimal machine-readable context.

## Authoring Guardrails

Before using `@PiSyncModel` or `@PiPacket`, the current processor contract is:

1. `@PiSyncModel.version` and `@PiPacket.version` must be `>= 1`;
2. schema ids and packet ids must stay unique within the same compilation;
3. `@PiSyncModel`, `@PiPacket`, and `@PiLivingService` must be top-level concrete classes;
4. `@PiSyncModel` needs an accessible no-arg constructor that does not declare checked exceptions;
5. packet constructors used for generated decode must match declared `@PiField` order and must not declare checked exceptions;
6. `@PiField(serializer = ...)` providers need an accessible no-arg constructor that does not declare checked exceptions;
7. `@PiAfterDecode`, `@PiSchemaUpgrade`, and `@PiPacketUpgrade` methods must not declare checked exceptions.

## Verification Gate

The minimum cold verification gate for this repo is:

```bash
bash ./gradlew clean test --no-daemon
```

The repo also ships `.github/workflows/ci.yml` so the same `clean test` path runs in CI instead of relying only on a passing local workstation.

## API Stability Boundaries

The current compatibility boundary is meant to be read in three layers:

1. Stable author-facing API:
   `org.pickaid.piserializekit.api.schema`, `api.packet`, `api.service`, `api.runtime`, plus the runtime entry points `PiSchemas`, `PiPackets`, and `PiSerializeServices`.
2. Internal implementation API:
   `processor.*`, `processor.support.*`, `processor.model.*`, `runtime.*.support`, and `runtime.*.codec` do not currently promise downstream stability and should not be treated as public extension points.
3. Generated naming boundary:
   `_PiSchema`, `_PiFields`, `_PiPacket`, and `_PiPacketProvider` companions are primarily build artifacts. Downstream code should prefer `PiSchemas` / `PiPackets` or higher-level host APIs instead of hard-coding generated type names as stable handwritten contracts.

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
