/**
 * Runtime serializer registry installation and built-in serializer implementations.
 *
 * <p>The runtime layer keeps author-facing serializer ids stable, exposes known-id and known-type
 * views for diagnostics, and treats built-in installation as an ensure-present step rather than a
 * destructive overwrite.</p>
 */
package org.pickaid.piserializekit.runtime.service;
