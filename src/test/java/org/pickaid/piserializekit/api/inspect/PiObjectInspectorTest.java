package org.pickaid.piserializekit.api.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiObjectInspectorTest {
    @Test
    void cachesRecordFieldsAndCreatesRecords() {
        PiObjectType<Amount> type = PiObjectInspector.of(Amount.class);
        PiObjectType<Amount> cached = PiObjectInspector.of(Amount.class);

        assertSame(type, cached);
        assertTrue(type.record());
        assertEquals(List.of("base", "bonus"), fieldNames(type.fields()));
        assertTrue(type.requireField("base").recordComponent());

        Amount created = type.createRecord(3, 4);

        assertEquals(new Amount(3, 4), created);
        assertEquals(List.of(3, 4), type.readFields(created));
        assertEquals(3, type.readFieldMap(created).get("base"));
    }

    @Test
    void recordComponentAnnotationsAreVisibleOnFields() {
        PiObjectField field = PiObjectInspector.of(ComponentAnnotated.class).requireField("key");

        assertTrue(field.isAnnotationPresent(ComponentRule.class));
    }

    @Test
    void walksRecordsCollectionsMapsAndArraysWithStablePaths() {
        Spell spell = new Spell(
                "fireball",
                new Amount(8, 2),
                List.of(new Step("ignite", new Amount(1, 0))),
                linkedMap("minecraft:zombie", new Amount(3, 1)),
                new int[]{5, 10},
                "debug"
        );

        List<String> paths = new ArrayList<>();
        PiObjectInspector.walk(spell, visit -> paths.add(visit.path().toString()));

        assertTrue(paths.contains("$"));
        assertTrue(paths.contains("$.id"));
        assertTrue(paths.contains("$.amount.base"));
        assertTrue(paths.contains("$.steps[0].amount.bonus"));
        assertTrue(paths.contains("$.weights[\"minecraft:zombie\"].base"));
        assertTrue(paths.contains("$.costs[1]"));
        assertFalse(paths.contains("$.hidden"));
    }

    @Test
    void fieldFilterSkipsFieldsBeforeReadingThem() {
        Spell spell = new Spell(
                "fireball",
                new Amount(8, 2),
                List.of(),
                Map.of(),
                new int[0],
                "debug"
        );

        List<String> paths = new ArrayList<>();
        PiObjectInspector.walk(spell, visit -> paths.add(visit.path().toString()));

        assertTrue(paths.contains("$.amount.base"));
        assertFalse(paths.contains("$.hidden"));
    }

    @Test
    void inspectLeafStopsFieldTraversal() {
        LeafHolder holder = new LeafHolder(new LeafRecord("inside"));

        List<String> paths = new ArrayList<>();
        PiObjectInspector.walk(holder, visit -> paths.add(visit.path().toString()));

        assertTrue(paths.contains("$.leaf"));
        assertFalse(paths.contains("$.leaf.value"));
    }

    @Test
    void plainObjectsAreOnlyTraversedWhenEnabled() {
        PlainNode root = new PlainNode("root");
        root.next = new PlainNode("child");

        List<String> defaultPaths = new ArrayList<>();
        PiObjectInspector.walk(root, visit -> defaultPaths.add(visit.path().toString()));

        assertEquals(List.of("$"), defaultPaths);

        PiObjectWalkOptions options = PiObjectWalkOptions.builder()
                .traversePlainObjects(true)
                .build();
        List<String> plainPaths = new ArrayList<>();
        PiObjectInspector.walk(root, options, visit -> plainPaths.add(visit.path().toString()));

        assertTrue(plainPaths.contains("$.inherited"));
        assertTrue(plainPaths.contains("$.label"));
        assertTrue(plainPaths.contains("$.next.label"));
        assertFalse(plainPaths.contains("$.STATIC_FIELD"));
    }

    @Test
    void cycleDetectionKeepsRecursivePlainObjectsBounded() {
        PlainNode root = new PlainNode("root");
        root.next = root;
        PiObjectWalkOptions options = PiObjectWalkOptions.builder()
                .traversePlainObjects(true)
                .maxDepth(8)
                .build();

        List<String> paths = new ArrayList<>();
        PiObjectInspector.walk(root, options, visit -> paths.add(visit.path().toString()));

        assertTrue(paths.contains("$.next"));
        assertFalse(paths.contains("$.next.next"));
    }

    @Test
    void nullValuesCanBeHidden() {
        Spell spell = new Spell("empty", null, List.of(), Map.of(), new int[0], null);
        PiObjectWalkOptions options = PiObjectWalkOptions.builder()
                .includeNullValues(false)
                .build();

        List<String> paths = new ArrayList<>();
        PiObjectInspector.walk(spell, options, visit -> paths.add(visit.path().toString()));

        assertFalse(paths.contains("$.amount"));
        assertFalse(paths.contains("$.hidden"));
    }

    private static List<String> fieldNames(List<PiObjectField> fields) {
        return fields.stream().map(PiObjectField::name).toList();
    }

    private static Map<String, Amount> linkedMap(String key, Amount amount) {
        Map<String, Amount> map = new LinkedHashMap<>();
        map.put(key, amount);
        return map;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    private @interface ComponentRule {
    }

    private record Amount(int base, int bonus) {
    }

    private record Step(String name, Amount amount) {
    }

    private record ComponentAnnotated(@ComponentRule String key) {
    }

    private record Spell(
            String id,
            Amount amount,
            List<Step> steps,
            Map<String, Amount> weights,
            int[] costs,
            @PiInspectIgnore String hidden
    ) {
    }

    private record LeafHolder(@PiInspectLeaf LeafRecord leaf) {
    }

    private record LeafRecord(String value) {
    }

    private static class PlainBase {
        int inherited = 2;
    }

    private static final class PlainNode extends PlainBase {
        static final String STATIC_FIELD = "ignored";

        final String label;
        PlainNode next;

        private PlainNode(String label) {
            this.label = label;
        }
    }
}
