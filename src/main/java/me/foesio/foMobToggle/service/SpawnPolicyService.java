package me.foesio.foMobToggle.service;

import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.data.PlayerSettingsManager;
import me.foesio.foMobToggle.model.ToggleCategory;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
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
        if (!isEffectiveCategoryEnabled(player, ToggleCategory.ALL_MOBS)) {
            return true;
        }

        ToggleCategory category = resolveSpecificCategory(mob);
        return category != null && !isEffectiveCategoryEnabled(player, category);
    }

    public boolean shouldHandle(CreatureSpawnEvent.SpawnReason reason) {
        return switch (reason) {
            case NATURAL, CHUNK_GEN -> true;
            default -> false;
        };
    }

    private ToggleCategory resolveSpecificCategory(Mob mob) {
        if (mob instanceof Enemy) {
            return ToggleCategory.MONSTERS;
        }

        if (mob instanceof Animals
                || mob instanceof WaterMob
                || mob instanceof Ambient
                || mob instanceof AbstractVillager
                || mob instanceof Golem
                || mob instanceof Allay) {
            return ToggleCategory.PASSIVE;
        }

        return null;
    }
}
