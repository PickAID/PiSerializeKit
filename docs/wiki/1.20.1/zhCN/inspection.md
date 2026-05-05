# 对象结构检查

有些数据不是马上保存成 NBT，而是先要检查一个对象树，比如数据图、action list、条件树、recipe layout。`PiObjectInspector` 用来把这些对象变成可遍历的字段路径。

```java
record DamageStep(String id, @PiInspectRange(min = 0, max = 100) int amount) {
}

record SpellGraph(
        @PiInspectRequired String id,
        List<DamageStep> steps,
        @PiInspectIgnore String debugNote
) {
}

PiObjectInspector.walk(graph, visit -> {
    System.out.println(visit.path() + " = " + visit.value());
});
```

默认会递归 record、list、map 和 array。普通 Java 对象默认只当作叶子，避免把 entity、level 这类运行时大对象整棵扫进去。

需要生成错误列表时，用 verifier：

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

常用标注：

- `@PiInspectRequired`：字段或 record component 不能是 `null`。
- `@PiInspectRange`：数字范围校验。
- `@PiInspectIgnore`：默认遍历时跳过这个字段。
- `@PiInspectLeaf`：访问这个值，但不继续展开内部字段。
