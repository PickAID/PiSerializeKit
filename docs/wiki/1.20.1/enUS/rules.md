# Rules

These rules are checked as early as possible:

- `@PiSyncModel.version` and `@PiPacket.version` must be `>= 1`.
- Schema ids cannot repeat within one compilation.
- Packet ids cannot repeat within one compilation.
- `@PiSyncModel` and `@PiPacket` must be top-level concrete classes.
- `@PiSyncModel` needs an accessible no-arg constructor with no checked exceptions.
- Packet decode constructors must match `@PiField` order and must not declare checked exceptions.
- `@PiField(serializer = ...)` providers need an accessible no-arg constructor with no checked exceptions.
- `@PiAfterDecode`, `@PiSchemaUpgrade`, and `@PiPacketUpgrade` methods must not declare checked exceptions.

Runtime registries still validate as a fallback. Missing bindings, duplicate ids, and invalid generated bindings report structured exceptions with the relevant id and type names.
