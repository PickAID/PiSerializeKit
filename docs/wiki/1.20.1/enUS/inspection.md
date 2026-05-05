# Object Inspection

Some data should be checked before it is saved to NBT: data graphs, action lists, condition trees, and recipe layouts. `PiObjectInspector` turns those object trees into visitable paths.

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

The default walker traverses records, lists, maps, and arrays. Plain Java objects are treated as leaves by default so runtime objects such as entities and levels are not expanded accidentally.

Use a verifier when you want a list of issues:

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

Common annotations:

- `@PiInspectRequired`: field or record component cannot be `null`.
- `@PiInspectRange`: numeric range check.
- `@PiInspectIgnore`: skip this field during default walking.
- `@PiInspectLeaf`: visit this value, but do not expand its internals.
