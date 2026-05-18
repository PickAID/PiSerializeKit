# PiSerializeKit

[中文](README.MD)

`PiSerializeKit` is the lower-level Pi library for writing, reading, upgrading, and packetizing data. It provides schemas, packets, serializers, object inspection, and annotation processor guardrails.

Current mainline documentation:

- [1.20.1 Chinese Wiki](docs/wiki/1.20.1/zhCN/README.md)
- [1.20.1 English Wiki](docs/wiki/1.20.1/enUS/README.md)

## Common Entry Points

- Annotations: `@PiSyncModel`, `@PiField`, `@PiPacket`
- Runtime: `PiSchemas`, `PiPackets`, `PiSerializeServices`
- Runtime payloads: `PiRuntimePayload`, `PiRuntimePayloadBinding`
- Serializers: `PiSerializer`, `PiSerializers`
- Object inspection: `PiObjectInspector`

Most projects should not directly depend on generated classes or treat `binding.codec()` as the first everyday API. The README stays short; schema, packet, runtime, inspection, and authoring rules live in the versioned wiki.
