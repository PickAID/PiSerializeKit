# PiSerializeKit

[中文版](README.MD)

`PiSerializeKit` is the part of the Pi stack that answers one question: how is data described, encoded, decoded, upgraded, and looked up at runtime?

Use it when you need any of these:

1. a typed state model that can be saved and synced;
2. a packet with a stable id, version, and decode path;
3. a reusable serializer for one Java type;
4. runtime lookup from an authored class to its generated schema or packet binding.

Most authors only touch three layers:

1. annotations such as `@PiSyncModel`, `@PiField`, and `@PiPacket`;
2. runtime entry points such as `PiSchemas`, `PiPackets`, and `PiSerializeServices`;
3. serializer APIs such as `PiSerializer` and `PiSerializers`.

You normally do not hand-reference `_PiSchema`, `_PiPacket`, or other generated companion names.

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

At runtime you resolve the binding from the authored class:

```java
PiStateBinding<ManaState> binding = PiSchemas.require(ManaState.class);

CompoundTag full = binding.saveFull(state);
binding.loadFull(restored, full, PiDecodeContext.strict());
```

The same binding also gives you projections and deltas:

```java
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

    @Override
    protected void handle(PiServerPacketContext context) {
    }
}
```

Again, runtime code resolves the binding from the authored class or stable id:

```java
PiPacketBinding<CastSkillPacket, ?> binding = PiPackets.require(CastSkillPacket.class);
FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

binding.codec().write(buf, new CastSkillPacket("fireball", 2));
CastSkillPacket decoded = binding.codec().read(buf);
```

### 3. Install or scope a serializer runtime

If you want the built-in serializers:

```java
PiSerializeRuntime runtime = new PiSerializeRuntime();
PiBuiltInSerializers.install(runtime);
PiSerializeServices.install(runtime);
```

If you want a temporary override for one code path:

```java
PiSerializeServices.withScope(runtime, () -> {
    PiSerializer<String> serializer = PiSerializeServices.requireSerializer(PiSerializers.STRING);
});
```

That scoped override is useful for tests, isolated local overrides, and higher-level packs that need their own serializer policy.

## What you should depend on

These are the main stable author-facing entry points:

1. `org.pickaid.piserializekit.api.schema.*`
2. `org.pickaid.piserializekit.api.packet.*`
3. `org.pickaid.piserializekit.api.service.*`
4. `org.pickaid.piserializekit.api.runtime.*`
5. `PiSchemas`
6. `PiPackets`
7. `PiSerializeServices`

These should currently be treated as internal implementation detail:

1. `processor.*`
2. `processor.support.*`
3. `processor.model.*`
4. `runtime.*.support`
5. `runtime.*.codec`
6. generated companion class names themselves

## Authoring rules

Before using `@PiSyncModel` or `@PiPacket`, keep these hard rules in mind:

1. `@PiSyncModel.version` and `@PiPacket.version` must be `>= 1`
2. schema ids and packet ids must stay unique within the same compilation
3. `@PiSyncModel`, `@PiPacket`, and `@PiLivingService` must be top-level concrete classes
4. `@PiSyncModel` needs an accessible no-arg constructor with no checked exceptions
5. packet constructors used for generated decode must match `@PiField` order and must not declare checked exceptions
6. `@PiField(serializer = ...)` providers need an accessible no-arg constructor with no checked exceptions
7. `@PiAfterDecode`, `@PiSchemaUpgrade`, and `@PiPacketUpgrade` methods must not declare checked exceptions

## What this repo does not do

`PiSerializeKit` does not own:

1. channel installation, send targets, thread routing, or transport guards;
2. capability or host-runtime wiring;
3. gameplay service lifecycles;
4. high-level UI, render, or world author magic;
5. reflective black-box auto-read/write.

In practice:

1. `PiSerializeKit` owns how data is described, upgraded, diagnosed, and bound;
2. `PiNet` owns how packets are transported, routed, and guarded;
3. `Pibrary` and later packs own how schemas and packets enter real gameplay hosts.

## Verification

The minimum cold verification gate for this repo is:

```bash
bash ./gradlew clean test --no-daemon
```

The repo also ships `.github/workflows/ci.yml`, which runs the same `clean test` gate on GitHub.
