package org.pickaid.piserializekit.api.schema;

/**
 * Declares which runtime audience may observe a field.
 */
public enum PiSyncScope {
    /**
     * Field is never synced automatically.
     */
    NONE,

    /**
     * Field is visible to chunk watchers.
     */
    CHUNK,

    /**
     * Field is visible to nearby tracking clients.
     */
    TRACKING,

    /**
     * Field is visible only to the owning player or owner-side runtime.
     */
    OWNER,

    /**
     * Field is visible only to menu/screen sync channels.
     */
    MENU,

    /**
     * Field is visible to all relevant clients regardless of chunk or owner routing.
     */
    GLOBAL
}
