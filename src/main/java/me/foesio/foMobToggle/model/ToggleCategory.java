package me.foesio.foMobToggle.model;

import me.foesio.core.gui.GuiSlots;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public enum ToggleCategory {
    ALL_MOBS("mob", "fomobtoggle.spawn.mob", 11, "items.mob.name", Material.SPAWNER.name()),
    MONSTERS("monster", "fomobtoggle.spawn.monster", 13, "items.monster.name", Material.ZOMBIE_HEAD.name()),
    PASSIVE("passive", "fomobtoggle.spawn.passive", 15, "items.passive.name", Material.WHEAT.name());

    private final String configKey;
    private final String permission;
    private final int slot;
    private final String displayPath;
    private final String defaultMaterial;

    ToggleCategory(String configKey, String permission, int slot, String displayPath, String defaultMaterial) {
        this.configKey = configKey;
        this.permission = permission;
        this.slot = slot;
        this.displayPath = displayPath;
        this.defaultMaterial = defaultMaterial;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getPermission() {
        return permission;
    }

    public int getSlot(FileConfiguration guiConfig) {
        int configured = guiConfig.getInt("items." + configKey + ".slot", slot);
        return GuiSlots.isValidSlot(3, configured) ? configured : slot;
    }

    public int getDefaultSlot() {
        return slot;
    }

    public boolean hasInvalidSlot(FileConfiguration guiConfig) {
        if (!guiConfig.contains("items." + configKey + ".slot")) {
            return false;
        }
        int configured = guiConfig.getInt("items." + configKey + ".slot", slot);
        return !GuiSlots.isValidSlot(3, configured);
    }

    public String getDisplayName(FileConfiguration guiConfig) {
        return guiConfig.getString(displayPath, configKey);
    }

    public String getDefaultMaterial() {
        return defaultMaterial;
    }

    public static ToggleCategory fromSlot(int slot, FileConfiguration guiConfig) {
        for (ToggleCategory category : values()) {
            if (category.getSlot(guiConfig) == slot) {
                return category;
            }
        }
        return null;
    }
}
