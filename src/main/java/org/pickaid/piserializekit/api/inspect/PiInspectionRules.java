package org.pickaid.piserializekit.api.inspect;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Small reusable inspection rules for common object tree checks.
 */
public final class PiInspectionRules {
    private PiInspectionRules() {
    }

    /**
     * Requires every visited field value matching the supplied field predicate to be non-null.
     *
     * @param fieldPredicate field predicate
     * @param message issue message
     * @return inspection rule
     */
    public static PiInspectionRule requireNonNullField(
            Predicate<PiObjectField> fieldPredicate,
            String message
    ) {
        Objects.requireNonNull(fieldPredicate, "fieldPredicate");
        Objects.requireNonNull(message, "message");
        return (visit, result) -> visit.field()
                .filter(fieldPredicate)
                .filter(field -> visit.value() == null)
                .ifPresent(field -> result.issue(
                        visit.path().toString(),
                        PiInspectionIssueCode.NULL_VALUE,
                        message,
                        true
                ));
    }

    /**
     * Requires one exact path to contain a non-null value.
     *
     * @param path expected path
     * @param message issue message
     * @return inspection rule
     */
    public static PiInspectionRule requireNonNullPath(String path, String message) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
        return (visit, result) -> {
            if (visit.path().toString().equals(path) && visit.value() == null) {
                result.issue(path, PiInspectionIssueCode.NULL_VALUE, message, true);
            }
        };
    }

    /**
     * Requires every visited value matching a value predicate to also match a validity predicate.
     *
     * @param valuePredicate selects values this rule applies to
     * @param validValue returns true when the selected value is valid
     * @param code issue category
     * @param message issue message
     * @param fatal whether the issue is fatal
     * @return inspection rule
     */
    public static PiInspectionRule requireValue(
            Predicate<PiObjectVisit> valuePredicate,
            Predicate<Object> validValue,
            PiInspectionIssueCode code,
            String message,
            boolean fatal
    ) {
        Objects.requireNonNull(valuePredicate, "valuePredicate");
        Objects.requireNonNull(validValue, "validValue");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        return (visit, result) -> {
            if (valuePredicate.test(visit) && !validValue.test(visit.value())) {
                result.issue(visit.path().toString(), code, message, fatal);
            }
        };
    }

    /**
     * Requires numeric values matching a path predicate to stay within an inclusive range.
     *
     * @param pathPredicate selects paths this rule applies to
     * @param min inclusive minimum
     * @param max inclusive maximum
     * @param message issue message
     * @return inspection rule
     */
    public static PiInspectionRule numberRange(
            Predicate<PiObjectPath> pathPredicate,
            double min,
            double max,
            String message
    ) {
        Objects.requireNonNull(pathPredicate, "pathPredicate");
        Objects.requireNonNull(message, "message");
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        return (visit, result) -> {
            if (!pathPredicate.test(visit.path()) || !(visit.value() instanceof Number number)) {
                return;
            }
            double value = number.doubleValue();
            if (value < min || value > max) {
                result.issue(
                        visit.path().toString(),
                        PiInspectionIssueCode.INVALID_VALUE,
                        message,
                        true
                );
            }
        };
    }

    /**
     * Applies built-in inspect annotations:
     * {@link PiInspectRequired} and {@link PiInspectRange}.
     *
     * @return inspection rule
     */
    public static PiInspectionRule inspectAnnotations() {
        return (visit, result) -> visit.field().ifPresent(field -> {
            field.annotation(PiInspectRequired.class).ifPresent(required -> {
                if (visit.value() == null) {
                    result.issue(
                            visit.path().toString(),
                            PiInspectionIssueCode.NULL_VALUE,
                            required.message().isBlank() ? field.name() + " is required" : required.message(),
                            true
                    );
                }
            });
            field.annotation(PiInspectRange.class).ifPresent(range -> checkRange(visit, result, field, range));
        });
    }

    /**
     * Requires one exact path to have appeared during traversal.
     *
     * @param path required path
     * @param message issue message
     * @return inspection rule
     */
    public static PiInspectionRule requireVisitedPath(String path, String message) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
        return new PiInspectionRule() {
            @Override
            public void check(PiObjectVisit visit, PiInspectionResult result) {
            }

            @Override
            public void finish(PiInspectionScope scope, PiInspectionResult result) {
                if (!scope.visited(path)) {
                    result.issue(path, PiInspectionIssueCode.MISSING_FIELD, message, true);
                }
            }
        };
    }

    /**
     * Selects visits whose path ends with the supplied suffix.
     *
     * @param suffix path suffix
     * @return path predicate
     */
    public static Predicate<PiObjectPath> pathEndsWith(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        return path -> path.toString().endsWith(suffix);
    }

    private static void checkRange(
            PiObjectVisit visit,
            PiInspectionResult result,
            PiObjectField field,
            PiInspectRange range
    ) {
        Object value = visit.value();
        if (value == null) {
            return;
        }
        String message = range.message().isBlank()
                ? field.name() + " must stay between " + range.min() + " and " + range.max()
                : range.message();
        if (!(value instanceof Number number)) {
            result.issue(visit.path().toString(), PiInspectionIssueCode.TYPE_MISMATCH, message, true);
            return;
        }
        double numeric = number.doubleValue();
        if (numeric < range.min() || numeric > range.max()) {
            result.issue(visit.path().toString(), PiInspectionIssueCode.INVALID_VALUE, message, true);
        }
    }
}
