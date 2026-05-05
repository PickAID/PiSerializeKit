# PiSerializeKit 1.20.1 Wiki

PiSerializeKit owns data shape: how state is saved, how packets are encoded, how older payloads migrate, and how object trees produce clear validation errors.

Common docs:

- [Schema State Models](schema.md)
- [Packets](packet.md)
- [Serializer Runtime](runtime.md)
- [Object Inspection](inspection.md)
- [Rules](rules.md)

## Everyday Entry Points

- `@PiSyncModel` + `@PiField`: generate save, load, projection, and delta logic for state.
- `@PiPacket`: generate stable packet ids, versions, and codecs.
- `PiSchemas`: save and load state by type.
- `PiPackets`: encode and decode packets by type or packet id.
- `PiSerializeServices`: install or temporarily scope serializer runtimes.
- `PiObjectInspector`: walk record, list, map, and array object trees.

Most code should not reference generated classes directly or start from `binding.codec()`. Use `PiStateBinding` or `PiPacketBinding` when you need finer control.
