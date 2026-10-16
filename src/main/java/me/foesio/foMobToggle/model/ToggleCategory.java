package me.foesio.foMobToggle.model;


public enum ToggleCategory {
    ALL_MOBS("mob", "fomobtoggle.spawn.mob"),
    MONSTERS("monster", "fomobtoggle.spawn.monster"),
    PASSIVE("passive", "fomobtoggle.spawn.passive");

    private final String configKey;
    private final String permission;
    ToggleCategory(String configKey, String permission) {
        this.configKey = configKey;
        this.permission = permission;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getPermission() {
        return permission;
    }

}
