/**
 * Shared runtime validation and support that sits below generated bindings and
 * above raw Minecraft transport/NBT primitives.
 *
 * <p>This package also owns the shared provider-bootstrap helper path used by
 * packet, schema, and serializer runtime entry points so ServiceLoader failures
 * surface as consistent author-facing diagnostics.</p>
 */
package org.pickaid.piserializekit.runtime;
