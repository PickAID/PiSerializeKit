package org.pickaid.piserializekit.api.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PiDecodeResultTest {
    @Test
    void summaryPrioritizesFatalIssuesAndLimitsOutput() {
        PiDecodeResult result = new PiDecodeResult();
        result.add(new PiDecodeIssue("alpha", PiDecodeIssueCode.INVALID_VALUE, "alpha bad", false));
        result.add(new PiDecodeIssue("beta", PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, "beta missing", false));
        result.add(new PiDecodeIssue("critical", PiDecodeIssueCode.SERIALIZER_FAILURE, "critical exploded", true));
        result.add(new PiDecodeIssue("gamma", PiDecodeIssueCode.TYPE_MISMATCH, "gamma mismatch", false));

        assertEquals(
                "critical -> critical exploded; alpha -> alpha bad; beta -> beta missing; +1 more",
                result.summary()
        );
    }

    @Test
    void summarySuppressesDuplicateEntries() {
        PiDecodeResult result = new PiDecodeResult();
        result.add(new PiDecodeIssue("value", PiDecodeIssueCode.SERIALIZER_FAILURE, "boom", true));
        result.add(new PiDecodeIssue("value", PiDecodeIssueCode.SERIALIZER_FAILURE, "boom", true));
        result.add(new PiDecodeIssue("title", PiDecodeIssueCode.INVALID_VALUE, "bad title", false));

        assertEquals("value -> boom; title -> bad title", result.summary());
    }

    @Test
    void authorSummaryCollapsesSecondaryIssuesByPath() {
        PiDecodeResult result = new PiDecodeResult();
        result.add(new PiDecodeIssue("alpha", PiDecodeIssueCode.INVALID_VALUE, "alpha bad", false));
        result.add(new PiDecodeIssue("value", PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, "missing value", false));
        result.add(new PiDecodeIssue("value", PiDecodeIssueCode.TYPE_MISMATCH, "wrong type", false));
        result.add(new PiDecodeIssue("critical", PiDecodeIssueCode.SERIALIZER_FAILURE, "critical exploded", true));
        result.add(new PiDecodeIssue("critical", PiDecodeIssueCode.INVALID_VALUE, "secondary noise", false));

        assertEquals(
                "critical -> critical exploded (+1 more); alpha -> alpha bad; value -> missing value (+1 more)",
                result.authorSummary()
        );
        assertEquals("fatal", result.severityLabel());
    }
}
