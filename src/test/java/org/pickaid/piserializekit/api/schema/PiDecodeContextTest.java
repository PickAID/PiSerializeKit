package org.pickaid.piserializekit.api.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PiDecodeContextTest {
    @Test
    void childIssuesAccumulateIntoSharedParentResult() {
        PiDecodeContext root = PiDecodeContext.strict();
        PiDecodeContext child = root.child("trial");

        child.issue(PiDecodeIssueCode.INVALID_VALUE, "title", "bad title", false);

        assertTrue(root.result().hasIssues());
        assertSame(root.result(), child.result());
        assertEquals(1, root.result().issues().size());
        assertEquals("trial.title", root.result().issues().get(0).path());
    }

    @Test
    void hasIssueStaysFalseUntilOneIssueIsRecorded() {
        PiDecodeContext root = PiDecodeContext.strict();
        PiDecodeContext child = root.child("players");

        assertFalse(root.hasIssue("players"));
        assertFalse(child.hasIssue("[0]"));

        child.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, "[0]", "missing entry", false);

        assertTrue(root.hasIssue("players[0]"));
        assertTrue(child.hasIssue("[0]"));
    }
}
