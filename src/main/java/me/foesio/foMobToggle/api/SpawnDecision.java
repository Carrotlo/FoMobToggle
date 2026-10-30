package me.foesio.foMobToggle.api;

/** FoMobToggle's decision only; other plugins may still prevent a spawn. */
public enum SpawnDecision {
    IGNORED,
    ALLOWED,
    BLOCKED
}
