package me.foesio.foMobToggle.service;

import java.util.Objects;
import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.api.SpawnDecision;
import me.foesio.foMobToggle.data.PlayerSettingsManager;
import me.foesio.foMobToggle.model.ToggleCategory;
import org.bukkit.Location;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class SpawnPolicyService {

    private final FoMobToggle plugin;
    private final PlayerSettingsManager playerSettingsManager;

    public SpawnPolicyService(FoMobToggle plugin, PlayerSettingsManager playerSettingsManager) {
        this.plugin = plugin;
        this.playerSettingsManager = playerSettingsManager;
    }

    public boolean isCategoryEnabled(Player player, ToggleCategory category) {
        if (player.isPermissionSet(category.getPermission())) {
            return player.hasPermission(category.getPermission());
        }
        return playerSettingsManager.get(player.getUniqueId()).isEnabled(category);
    }

    public boolean isEffectiveCategoryEnabled(Player player, ToggleCategory category) {
        if (category == ToggleCategory.ALL_MOBS) {
            return isCategoryEnabled(player, ToggleCategory.ALL_MOBS);
        }

        return isCategoryEnabled(player, ToggleCategory.ALL_MOBS) && isCategoryEnabled(player, category);
    }

    public boolean shouldBlockSpawn(Player player, Mob mob) {
        return shouldBlockSpawn(player, classify(mob.getClass()));
    }

    public SpawnDecision evaluateSpawn(Location location, EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        Objects.requireNonNull(type, "type");
        Class<? extends Entity> entityClass = type.getEntityClass();
        return evaluateSpawn(location, classify(entityClass), reason,
                entityClass != null && Mob.class.isAssignableFrom(entityClass));
    }

    public SpawnDecision evaluateSpawn(Location location, Mob mob, CreatureSpawnEvent.SpawnReason reason) {
        Objects.requireNonNull(mob, "mob");
        return evaluateSpawn(location, classify(mob.getClass()), reason, true);
    }

    private SpawnDecision evaluateSpawn(Location location, ToggleCategory category,
                                        CreatureSpawnEvent.SpawnReason reason, boolean isMob) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(reason, "reason");
        if (!shouldHandle(reason)) {
            return SpawnDecision.IGNORED;
        }
        if (!isMob) {
            return SpawnDecision.IGNORED;
        }
        double radius = plugin.getConfig().getDouble("spawn-check-radius", 128.0D);
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius,
                candidate -> candidate instanceof Player)) {
            Player player = (Player) entity;
            if (shouldBlockSpawn(player, category)) {
                return SpawnDecision.BLOCKED;
            }
        }
        return SpawnDecision.ALLOWED;
    }

    private boolean shouldBlockSpawn(Player player, ToggleCategory category) {
        return !isEffectiveCategoryEnabled(player, ToggleCategory.ALL_MOBS)
                || category != null && !isEffectiveCategoryEnabled(player, category);
    }

    public boolean shouldHandle(CreatureSpawnEvent.SpawnReason reason) {
        return switch (reason) {
            case NATURAL, CHUNK_GEN -> true;
            default -> false;
        };
    }

    static ToggleCategory classify(Class<? extends Entity> entityClass) {
        if (entityClass == null) {
            return null;
        }
        if (Enemy.class.isAssignableFrom(entityClass)) {
            return ToggleCategory.MONSTERS;
        }
        if (Animals.class.isAssignableFrom(entityClass)
                || WaterMob.class.isAssignableFrom(entityClass)
                || Ambient.class.isAssignableFrom(entityClass)
                || AbstractVillager.class.isAssignableFrom(entityClass)
                || Golem.class.isAssignableFrom(entityClass)
                || Allay.class.isAssignableFrom(entityClass)) {
            return ToggleCategory.PASSIVE;
        }
        return null;
    }
}
