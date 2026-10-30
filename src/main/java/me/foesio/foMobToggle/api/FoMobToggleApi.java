package me.foesio.foMobToggle.api;

import me.foesio.foMobToggle.model.ToggleCategory;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Stable integration surface, discovered through Bukkit's ServicesManager.
 * Call only on the relevant player or location's owning server thread/region.
 * A cached service reference throws IllegalStateException after provider disable.
 */
public interface FoMobToggleApi {

    /**
     * Returns the current in-memory saved preference and permission-adjusted state for an online player.
     * The effective category state includes the ALL_MOBS gate, but is not a whole-spawn decision.
     * @throws IllegalArgumentException if the player is offline
     */
    ToggleState getState(Player player, ToggleCategory category);

    /**
     * Changes an online player's saved preference using the same permission lock as the public menu.
     * CHANGED updates memory and schedules a database write; it does not mean the write has committed.
     * LOCKED takes precedence over UNCHANGED when a permission node is explicitly set.
     * @throws IllegalArgumentException if the player is offline
     */
    SetPreferenceResult setPreference(Player player, ToggleCategory category, boolean enabled);

    /**
     * Evaluates FoMobToggle's current policy without spawning an entity. IGNORED means the reason
     * is not NATURAL or CHUNK_GEN, or its entity class is unavailable or is not a Mob.
     * The location must have a world.
     * This previews FoMobToggle only; other plugins can still cancel an allowed spawn.
     */
    SpawnDecision evaluateSpawn(Location location, EntityType type, CreatureSpawnEvent.SpawnReason reason);
}
