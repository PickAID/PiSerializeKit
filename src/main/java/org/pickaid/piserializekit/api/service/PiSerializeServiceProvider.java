package org.pickaid.piserializekit.api.service;

/**
 * Runtime provider for the default Pi serializer service bootstrap.
 */
public interface PiSerializeServiceProvider {
    PiSerializeService create();
}
