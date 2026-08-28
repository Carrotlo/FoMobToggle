package me.foesio.foMobToggle.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.GuiItems;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.message.FoMessageService;
import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.data.PlayerSettings;
import me.foesio.foMobToggle.data.PlayerSettingsManager;
import me.foesio.foMobToggle.model.ToggleCategory;
import me.foesio.foMobToggle.service.SpawnPolicyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ToggleMenu {

    private static final int ROWS = 3;
    private static final int SIZE = GuiSlots.sizeForRows(ROWS);

    private final FoMobToggle plugin;
    private final FoMessageService messages;
    private final PlayerSettingsManager playerSettingsManager;
    private final SpawnPolicyService spawnPolicyService;
    private final File guiFile;
    private FileConfiguration guiConfig;

    public ToggleMenu(
            FoMobToggle plugin,
            FoMessageService messages,
            PlayerSettingsManager playerSettingsManager,
            SpawnPolicyService spawnPolicyService,
            File guiFile
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.playerSettingsManager = playerSettingsManager;
        this.spawnPolicyService = spawnPolicyService;
        this.guiFile = guiFile;
        reloadConfig();
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new ToggleMenuHolder(), SIZE,
                GuiTitles.format(messages.renderTemplate(guiConfig.getString("title", "&8Mob Toggle"))));

        fillBackground(inventory);
        for (ToggleCategory category : ToggleCategory.values()) {
            inventory.setItem(category.getSlot(guiConfig), createToggleItem(player, category));
        }
        plugin.getGuiSounds().open(player);
        player.openInventory(inventory);
    }

    public void reloadConfig() {
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        Set<Integer> usedSlots = new HashSet<>();
        for (ToggleCategory category : ToggleCategory.values()) {
            String materialPath = "items." + category.getConfigKey() + ".material";
            String materialName = guiConfig.getString(materialPath, category.getDefaultMaterial());
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Invalid GUI material '" + materialName + "' at "
                        + materialPath + "; using LEVER.");
            }
            if (category.hasInvalidSlot(guiConfig)) {
                plugin.getLogger().warning("Invalid GUI slot at items." + category.getConfigKey()
                        + ".slot; using " + category.getDefaultSlot() + ".");
            }
            if (!usedSlots.add(category.getSlot(guiConfig))) {
                plugin.getLogger().warning("Duplicate GUI slot for " + category.getConfigKey()
                        + "; category items may overlap.");
            }
        }
    }

    public boolean isMenu(Inventory inventory) {
        return inventory.getHolder() instanceof ToggleMenuHolder;
    }

    public void handleClick(Player player, int slot) {
        ToggleCategory category = ToggleCategory.fromSlot(slot, guiConfig);
        if (category == null) {
            return;
        }

        if (player.isPermissionSet(category.getPermission())) {
            messages.send(player, "messages.locked-by-permission",
                    "{prefix}{bad}This toggle is locked by your permissions.");
            plugin.getGuiSounds().error(player);
            return;
        }

        PlayerSettings settings = playerSettingsManager.get(player.getUniqueId());
        boolean newValue = !settings.isEnabled(category);
        playerSettingsManager.set(player.getUniqueId(), category, newValue);

        Inventory topInventory = player.getOpenInventory().getTopInventory();
        topInventory.setItem(category.getSlot(guiConfig), createToggleItem(player, category));

        String toggle = messages.renderTemplate(category.getDisplayName(guiConfig)
                .replace("{state_color}", "{theme}"));
        String state = messages.render(
                newValue ? "messages.state.enabled" : "messages.state.disabled",
                newValue ? "{good}Enabled" : "{bad}Disabled");
        messages.send(player, "messages.toggle-updated",
                "{prefix}{muted}Updated {toggle}{muted} to {state}{muted}.",
                Map.of("toggle", toggle, "state", state));
        plugin.getSounds().playWithPitchVariation(
                player,
                newValue ? "mob-toggle.enabled" : "mob-toggle.disabled",
                0.04f
        );
    }

    private void fillBackground(Inventory inventory) {
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, EditorItemFactory.filler());
        }
    }

    private ItemStack createToggleItem(Player player, ToggleCategory category) {
        boolean effectiveEnabled = spawnPolicyService.isEffectiveCategoryEnabled(player, category);
        boolean savedEnabled = playerSettingsManager.get(player.getUniqueId()).isEnabled(category);
        boolean locked = player.isPermissionSet(category.getPermission());

        String materialName = guiConfig.getString("items." + category.getConfigKey() + ".material",
                category.getDefaultMaterial());
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            material = Material.LEVER;
        }

        String statePath = effectiveEnabled ? "messages.state.enabled" : "messages.state.disabled";
        String displayName = messages.renderTemplate(category.getDisplayName(guiConfig)
                .replace("{state_color}", effectiveEnabled
                        ? "{good}" : "{bad}"));

        List<String> lore = new ArrayList<>();
        ConfigurationSection section = guiConfig.getConfigurationSection("items." + category.getConfigKey());
        if (section != null) {
            String stateText = messages.render(statePath,
                    effectiveEnabled ? "{good}Enabled" : "{bad}Disabled");
            String savedStateText = messages.render(
                    savedEnabled ? "messages.state.enabled" : "messages.state.disabled",
                    savedEnabled ? "{good}Enabled" : "{bad}Disabled");
            String permissionModeText = messages.render(
                    locked ? "messages.permission-mode.locked" : "messages.permission-mode.gui",
                    locked ? "{bad}Permission override" : "{good}GUI toggle");
            for (String line : section.getStringList("lore")) {
                lore.add(messages.renderTemplate(line
                        .replace("{state}", stateText)
                        .replace("{saved_state}", savedStateText)
                        .replace("{permission_mode}", permissionModeText)));
            }
        }
        return GuiItems.create(material, displayName, lore);
    }

}
