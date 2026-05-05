# Schema State Models

Mark a state class with `@PiSyncModel`, then mark the fields that belong in persistence or sync payloads with `@PiField`.

```java
@PiSyncModel(id = "example:mana_state", version = 1)
public final class ManaState {
    @PiField(id = "mana", sync = PiSyncScope.TRACKING, persist = true)
    public int mana;

    @PiField(id = "selected_spell", sync = PiSyncScope.OWNER, persist = true)
    public String selectedSpell = "";
}
```

The common save/load path is short:

```java
CompoundTag tag = PiSchemas.saveFull(state);
ManaState restored = PiSchemas.loadFull(ManaState.class, tag);
```

Use the binding when you need client views, persisted views, or deltas:

```java
PiStateBinding<ManaState> binding = PiSchemas.require(ManaState.class);

CompoundTag clientView = binding.saveClientView(state);
CompoundTag persisted = binding.savePersisted(state);
CompoundTag delta = binding.writeClientDelta(state, dirtySet);
```

`sync` controls which sync view can see a field. `persist` controls whether a field is included in persisted saves. A field can sync without being saved, or be saved without being part of the default client view.

Use `@PiSchemaUpgrade` for old payload versions. The processor checks that migration steps can reach the current version.
