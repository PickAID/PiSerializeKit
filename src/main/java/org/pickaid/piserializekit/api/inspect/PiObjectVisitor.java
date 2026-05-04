package org.pickaid.piserializekit.api.inspect;

/**
 * Receives values while walking an inspected object graph.
 */
@FunctionalInterface
public interface PiObjectVisitor {
    /**
     * Visits one object graph value.
     *
     * @param visit visited value
     */
    void visit(PiObjectVisit visit);
}
