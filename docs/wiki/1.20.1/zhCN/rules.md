# 使用规则

这些规则会尽量在编译期拦住：

- `@PiSyncModel.version` 和 `@PiPacket.version` 必须 `>= 1`。
- 同一轮编译里的 schema id 不能重复。
- 同一轮编译里的 packet id 不能重复。
- `@PiSyncModel` 和 `@PiPacket` 必须是 top-level concrete class。
- `@PiSyncModel` 需要可访问的无参构造器，且不能声明 checked exception。
- `@PiPacket` 的 decode 构造器必须和 `@PiField` 顺序一致，且不能声明 checked exception。
- `@PiField(serializer = ...)` provider 需要可访问的无参构造器，且不能声明 checked exception。
- `@PiAfterDecode`、`@PiSchemaUpgrade`、`@PiPacketUpgrade` 方法不能声明 checked exception。

运行时 registry 仍会做兜底检查。正常情况下，重复 id、缺失 binding、非法 generated binding 会给出带 id 和类型名的结构化异常。
