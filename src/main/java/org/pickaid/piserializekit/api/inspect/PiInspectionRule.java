package org.pickaid.piserializekit.api.inspect;

/**
 * One rule executed for each visited value during object verification.
 */
@FunctionalInterface
public interface PiInspectionRule {
    /**
     * Checks one visited value and writes any issues into the result.
     *
     * @param visit visited value
     * @param result mutable result
     */
    void check(PiObjectVisit visit, PiInspectionResult result);

    /**
     * Runs after traversal is complete.
     *
     * <p>Rules that need whole-tree knowledge, such as "path X must have appeared at least once",
     * can use this hook without participating in every visit.</p>
     *
     * @param scope completed inspection scope
     * @param result mutable result
     */
    default void finish(PiInspectionScope scope, PiInspectionResult result) {
    }
}
