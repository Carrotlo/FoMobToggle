package me.foesio.foMobToggle.api;

/**
 * An immutable view of one category for an online player. permissionLocked covers this category's
 * explicit permission node; effectiveEnabled also incorporates the ALL_MOBS parent category.
 */
public record ToggleState(boolean savedPreference, boolean effectiveEnabled, boolean permissionLocked) {
}
