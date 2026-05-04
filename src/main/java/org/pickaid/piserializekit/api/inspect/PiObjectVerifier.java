package org.pickaid.piserializekit.api.inspect;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Executes inspection rules over an object graph and returns structured issues.
 */
public final class PiObjectVerifier {
    private final PiObjectWalkOptions options;
    private final List<PiInspectionRule> rules;

    private PiObjectVerifier(PiObjectWalkOptions options, List<PiInspectionRule> rules) {
        this.options = Objects.requireNonNull(options, "options");
        this.rules = List.copyOf(rules);
    }

    /**
     * Creates a verifier with default walk options.
     *
     * @param rules inspection rules
     * @return object verifier
     */
    public static PiObjectVerifier of(PiInspectionRule... rules) {
        return builder().rules(rules).build();
    }

    /**
     * Creates a verifier with the built-in inspect annotation rule.
     *
     * @return standard object verifier
     */
    public static PiObjectVerifier standard() {
        return of(PiInspectionRules.inspectAnnotations());
    }

    /**
     * Creates a verifier builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Verifies one object graph.
     *
     * @param root root object
     * @return inspection result
     */
    public PiInspectionResult verify(Object root) {
        PiInspectionResult result = new PiInspectionResult();
        PiInspectionScope.Builder scope = PiInspectionScope.builder();
        try {
            PiObjectInspector.walk(root, options, visit -> {
                scope.visit(visit.path());
                for (PiInspectionRule rule : rules) {
                    try {
                        rule.check(visit, result);
                    } catch (RuntimeException exception) {
                        result.issue(
                                visit.path().toString(),
                                PiInspectionIssueCode.INSPECTION_FAILURE,
                                "Inspection rule failed: " + exception.getMessage(),
                                true
                        );
                    }
                }
            });
            PiInspectionScope completedScope = scope.build();
            for (PiInspectionRule rule : rules) {
                try {
                    rule.finish(completedScope, result);
                } catch (RuntimeException exception) {
                    result.issue(
                            "$",
                            PiInspectionIssueCode.INSPECTION_FAILURE,
                            "Inspection rule finish failed: " + exception.getMessage(),
                            true
                    );
                }
            }
        } catch (PiObjectInspectionException exception) {
            String path = exception.path().isEmpty() ? "$" : exception.path();
            result.issue(
                    path,
                    PiInspectionIssueCode.INSPECTION_FAILURE,
                    exception.getMessage(),
                    true
            );
        }
        return result;
    }

    /**
     * Builder for object verifiers.
     */
    public static final class Builder {
        private PiObjectWalkOptions options = PiObjectWalkOptions.defaults();
        private final java.util.ArrayList<PiInspectionRule> rules = new java.util.ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the walk options.
         *
         * @param options walk options
         * @return this builder
         */
        public Builder options(PiObjectWalkOptions options) {
            this.options = Objects.requireNonNull(options, "options");
            return this;
        }

        /**
         * Adds one inspection rule.
         *
         * @param rule inspection rule
         * @return this builder
         */
        public Builder rule(PiInspectionRule rule) {
            this.rules.add(Objects.requireNonNull(rule, "rule"));
            return this;
        }

        /**
         * Adds inspection rules.
         *
         * @param rules inspection rules
         * @return this builder
         */
        public Builder rules(PiInspectionRule... rules) {
            Objects.requireNonNull(rules, "rules");
            Arrays.stream(rules).forEach(this::rule);
            return this;
        }

        /**
         * Builds the verifier.
         *
         * @return object verifier
         */
        public PiObjectVerifier build() {
            return new PiObjectVerifier(options, rules);
        }
    }
}
