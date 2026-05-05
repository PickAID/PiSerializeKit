# Schema 状态模型

状态类用 `@PiSyncModel` 标记，用 `@PiField` 标记要进入持久化或同步 payload 的字段。

```java
@PiSyncModel(id = "example:mana_state", version = 1)
public final class ManaState {
    @PiField(id = "mana", sync = PiSyncScope.TRACKING, persist = true)
    public int mana;

    @PiField(id = "selected_spell", sync = PiSyncScope.OWNER, persist = true)
    public String selectedSpell = "";
}
```

最常见的保存和读取：

```java
CompoundTag tag = PiSchemas.saveFull(state);
ManaState restored = PiSchemas.loadFull(ManaState.class, tag);
```

需要区分客户端可见内容、持久化内容或 delta 时，再拿 binding：

```java
PiStateBinding<ManaState> binding = PiSchemas.require(ManaState.class);

CompoundTag clientView = binding.saveClientView(state);
CompoundTag persisted = binding.savePersisted(state);
CompoundTag delta = binding.writeClientDelta(state, dirtySet);
```

`sync` 控制字段进入哪类同步视图；`persist` 控制字段是否进入持久化视图。一个字段可以只同步、不保存，也可以只保存、不默认同步。

旧版本 payload 的升级用 `@PiSchemaUpgrade`。迁移链必须能从旧版本走到当前版本，processor 会在编译期检查断链。
