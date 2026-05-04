# PiSerializeKit

[中文版](README.MD)

`PiSerializeKit` is the part of the Pi stack that answers one question: how is data described, encoded, decoded, upgraded, and looked up at runtime?

Use it when you need any of these:

1. a typed state model that can be saved and synced;
2. a packet with a stable id, version, and decode path;
3. a reusable serializer for one Java type;
4. direct runtime save/load helpers by type;
5. field scanning for a record/action tree when you need recursive checks or path-based errors.

Most day-to-day code only touches these layers:

1. annotations such as `@PiSyncModel`, `@PiField`, and `@PiPacket`;
2. runtime entry points such as `PiSchemas`, `PiPackets`, and `PiSerializeServices`;
3. serializer APIs such as `PiSerializer` and `PiSerializers`;
4. object inspection through `PiObjectInspector`.

You normally do not hand-reference `_PiSchema`, `_PiPacket`, or other generated companion names, and the low-level `binding.codec()` path should not be the first example most users see.

The usual split is:

1. `api.*` contains the annotations and contracts you write against most of the time;
2. `PiSchemas` and `PiPackets` are the runtime lookup entry points;
3. `PiNet` consumes the packet ids, versions, and codecs generated here.

## Typical usage

### 1. Define a persisted and synced state

```java
@PiSyncModel(id = "example:mana_state", version = 1)
public final class ManaState {
    @PiField(id = "mana", sync = PiSyncScope.TRACKING, persist = true)
    public int mana;

    @PiField(id = "selected_spell", sync = PiSyncScope.OWNER, persist = true)
    public String selectedSpell = "";
}
```

The direct path is now:

```java
CompoundTag full = PiSchemas.saveFull(state);
ManaState restored = PiSchemas.loadFull(ManaState.class, full);
```

If you need projections, persisted views, or deltas, then step down to the binding:

```java
PiStateBinding<ManaState> binding = PiSchemas.require(ManaState.class);

CompoundTag clientView = binding.saveClientView(state);
CompoundTag persisted = binding.savePersisted(state);
CompoundTag delta = binding.writeClientDelta(state, dirtySet);
```

### 2. Define a packet

You can set a namespace once per package:

```java
@PiPacketNamespace("example")
package com.example.packet;
```

Then write the packet itself:

```java
@PiPacket
public final class CastSkillPacket extends PiServerPacket {
    @PiField(id = "skill", sync = PiSyncScope.OWNER, persist = false)
    public String skill;

    @PiField(id = "level", sync = PiSyncScope.OWNER, persist = false)
    public int level;

    public CastSkillPacket(String skill, int level) {
        this.skill = skill;
        this.level = level;
    }
}
```

Most of the time, defining the class is the main job. `PiSerializeKit` gives the packet a stable id, version, and decode path, and `PiNet` consumes that result for registration and transport.

The `@PiField(sync = ...)` values on packet fields only reuse the shared field descriptor model. Delivery concepts such as tracking, broadcast, and reply are still chosen by the network layer above.

If you are debugging, bridging another transport, or wiring low-level runtime code, you can still encode and decode it directly:

```java
CastSkillPacket packet = new CastSkillPacket("fireball", 2);
FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
PiPacketBuffer buffer = PiPacketBuffers.wrap(raw);

PiPackets.write(buffer, packet);
raw.readerIndex(0);

ResourceLocation id = packet.packetId();
int version = packet.version();
CastSkillPacket decoded = PiPackets.read(CastSkillPacket.class, buffer);
```

Use `PiPackets.require(...)` only when you really need binding lookup, field metadata, version details, or migration details.

### 3. Install or scope a serializer runtime

If you want the built-in serializers:

```java
PiSerializeRuntime runtime = new PiSerializeRuntime();
PiBuiltInSerializers.install(runtime);
PiSerializeServices.install(runtime);
```

Those classes live in:

1. `org.pickaid.piserializekit.runtime.service.PiSerializeRuntime`
2. `org.pickaid.piserializekit.runtime.service.PiBuiltInSerializers`
3. `org.pickaid.piserializekit.api.service.PiSerializeServices`

If you want a temporary override for one code path:

```java
PiSerializeServices.withScope(runtime, () -> {
    PiSerializer<String> serializer = PiSerializeServices.requireSerializer(PiSerializers.STRING);
});
```

That scoped override is useful for tests, isolated local overrides, and higher-level packs that need their own serializer policy.

### 4. Inspect a record or action tree

Some systems do not want to save data to NBT immediately. They first need to see the fields,
children, and paths inside an object tree. Data graphs, spell actions, recipe layouts, and
condition trees often need recursive validation with clear paths such as
`$.actions[0].damage.amount`.

```java
record DamageStep(String id, @PiInspectRange(min = 0, max = 100) int amount) {
}

record SpellGraph(@PiInspectRequired String id, List<DamageStep> steps, @PiInspectIgnore String debugNote) {
}

PiObjectType<SpellGraph> type = PiObjectInspector.of(SpellGraph.class);
List<String> fields = type.fields().stream()
        .map(PiObjectField::name)
        .toList();

PiObjectInspector.walk(graph, visit -> {
    System.out.println(visit.path() + " = " + visit.value());
});
```

The default walker traverses records, lists, maps, and arrays. Plain Java objects are treated as
leaves by default so entity, level, and other large runtime objects do not get expanded by accident.
Enable plain object traversal only when you really want it:

```java
PiObjectWalkOptions options = PiObjectWalkOptions.builder()
        .traversePlainObjects(true)
        .fieldFilter(field -> !field.isTransient())
        .build();

PiObjectInspector.walk(root, options, visitor);
```

For config, data graph, or action tree validation, the verifier path is usually more direct:

```java
PiObjectVerifier verifier = PiObjectVerifier.of(
        PiInspectionRules.inspectAnnotations(),
        PiInspectionRules.requireVisitedPath("$.steps[0].id", "at least one step is required")
);

PiInspectionResult result = verifier.verify(graph);
if (result.hasFatal()) {
    throw new IllegalArgumentException(result.authorSummary());
}
```

The lightweight annotations are:

1. `@PiInspectRequired`: the field or record component cannot be `null`
2. `@PiInspectRange`: numeric range validation
3. `@PiInspectIgnore`: skip this field during default traversal
4. `@PiInspectLeaf`: visit this value but do not expand its internal fields

## Drop lower only when needed

These are the main entry points used most of the time:

1. `org.pickaid.piserializekit.api.schema.*`
2. `org.pickaid.piserializekit.api.packet.*`
3. `org.pickaid.piserializekit.api.service.*`
4. `org.pickaid.piserializekit.api.runtime.*`
5. `org.pickaid.piserializekit.api.inspect.*`
6. `PiSchemas`
7. `PiPackets`
8. `PiSerializeServices`
9. `PiObjectInspector`

If you need more control, the next layer down is:

1. `PiStateBinding`
2. `PiPacketBinding`
3. `PiDecodeContext`
4. `PiSerializer`

That is the advanced path, not the main path most code should start from.

## Usage rules

Before using `@PiSyncModel` or `@PiPacket`, keep these hard rules in mind:

1. `@PiSyncModel.version` and `@PiPacket.version` must be `>= 1`
2. schema ids and packet ids must stay unique within the same compilation
3. `@PiSyncModel` and `@PiPacket` must be top-level concrete classes
4. `@PiSyncModel` needs an accessible no-arg constructor with no checked exceptions
5. packet constructors used for generated decode must match `@PiField` order and must not declare checked exceptions
6. `@PiField(serializer = ...)` providers need an accessible no-arg constructor with no checked exceptions
7. `@PiAfterDecode`, `@PiSchemaUpgrade`, and `@PiPacketUpgrade` methods must not declare checked exceptions
