/**
 * Object field caching and path-aware object graph inspection.
 *
 * <p>This package is intentionally separate from schema and packet generation. Use schema/packet
 * bindings for stable persisted or synced payloads; use object inspection when a higher-level
 * runtime wants to understand an arbitrary record/action tree, verify child objects, or produce
 * precise diagnostics without writing reflection code at every call site. Lightweight annotations
 * such as {@link org.pickaid.piserializekit.api.inspect.PiInspectRequired},
 * {@link org.pickaid.piserializekit.api.inspect.PiInspectRange},
 * {@link org.pickaid.piserializekit.api.inspect.PiInspectIgnore}, and
 * {@link org.pickaid.piserializekit.api.inspect.PiInspectLeaf} cover common data-tree checks
 * while still allowing custom {@link org.pickaid.piserializekit.api.inspect.PiInspectionRule}
 * implementations.</p>
 */
package org.pickaid.piserializekit.api.inspect;
