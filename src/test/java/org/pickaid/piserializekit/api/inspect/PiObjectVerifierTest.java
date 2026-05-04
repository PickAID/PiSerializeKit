package org.pickaid.piserializekit.api.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PiObjectVerifierTest {
    @Test
    void verifiesRequiredFieldsAndNumericRanges() {
        Spell spell = new Spell(null, List.of(new Step("blast", -2)));
        PiObjectVerifier verifier = PiObjectVerifier.standard();

        PiInspectionResult result = verifier.verify(spell);

        assertTrue(result.hasFatal());
        assertEquals(2, result.issues().size());
        assertTrue(result.hasIssueAt("$.id"));
        assertTrue(result.hasIssueAt("$.steps[0].amount"));
        assertEquals(PiInspectionIssueCode.NULL_VALUE, result.issues().get(0).code());
        assertEquals("required value is missing", result.issues().get(0).message());
        assertEquals("fatal", result.severityLabel());
    }

    @Test
    void resultSummariesDeduplicateAndPreferFatalIssues() {
        PiInspectionResult result = new PiInspectionResult();
        result.issue("$.a", PiInspectionIssueCode.UNKNOWN, "warning", false);
        result.issue("$.b", PiInspectionIssueCode.INVALID_VALUE, "fatal", true);
        result.issue("$.b", PiInspectionIssueCode.INVALID_VALUE, "fatal", true);
        result.issue("$.b", PiInspectionIssueCode.NULL_VALUE, "also fatal", true);

        assertEquals("$.b -> fatal; $.b -> also fatal; +1 more", result.summary(2));
        assertEquals("$.b -> fatal (+1 more); $.a -> warning", result.authorSummary(2));
    }

    @Test
    void verifierCanUseCustomVisitRules() {
        Spell spell = new Spell("fireball", List.of(new Step("blast", 12)));
        PiObjectVerifier verifier = PiObjectVerifier.of(
                PiInspectionRules.requireValue(
                        visit -> visit.path().toString().endsWith(".name"),
                        value -> value instanceof String text && !text.isBlank(),
                        PiInspectionIssueCode.INVALID_VALUE,
                        "name must be non-blank",
                        true
                )
        );

        PiInspectionResult result = verifier.verify(spell);

        assertFalse(result.hasIssues());
    }

    @Test
    void verifierCapturesRuleFailuresWithCurrentPath() {
        Spell spell = new Spell("fireball", List.of());
        PiObjectVerifier verifier = PiObjectVerifier.of((visit, result) -> {
            if (visit.path().toString().equals("$.id")) {
                throw new IllegalStateException("broken rule");
            }
        });

        PiInspectionResult result = verifier.verify(spell);

        assertTrue(result.hasFatal());
        assertTrue(result.hasIssueAt("$.id"));
        assertEquals(PiInspectionIssueCode.INSPECTION_FAILURE, result.issues().get(0).code());
        assertEquals("$.id -> Inspection rule failed: broken rule", result.summary());
    }

    @Test
    void verifierCanRequireAPathAfterTraversal() {
        Spell spell = new Spell("fireball", List.of());
        PiObjectVerifier verifier = PiObjectVerifier.of(
                PiInspectionRules.requireVisitedPath("$.steps[0].name", "at least one step name is required")
        );

        PiInspectionResult result = verifier.verify(spell);

        assertTrue(result.hasFatal());
        assertTrue(result.hasIssueAt("$.steps[0].name"));
        assertEquals(PiInspectionIssueCode.MISSING_FIELD, result.issues().get(0).code());
    }

    private record Spell(
            @PiInspectRequired(message = "required value is missing") String id,
            List<Step> steps
    ) {
    }

    private record Step(String name, @PiInspectRange(min = 0, max = 100, message = "amount must stay between 0 and 100") int amount) {
    }
}
