package me.foesio.foMobToggle.editor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.foesio.core.FoCoreContext;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.ConfigEditorButton;
import me.foesio.core.editor.ConfigEditorMenu;
import me.foesio.core.editor.ConfigEditorValueType;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorMenuHolder;
import me.foesio.core.editor.EditorSaveResult;
import me.foesio.core.editor.EditorSettingSaver;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.foMobToggle.FoMobToggle;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public final class FoMobToggleEditor implements Listener {

    private static final String EDITOR_ID = "fomobtoggle-editor";
    private static final String RADIUS_PATH = "spawn-check-radius";
    private static final double MIN_RADIUS = 0.0D;
    private static final double MAX_RADIUS = 512.0D;

    private final FoMobToggle plugin;
    private final FoCoreContext core;
    private final FoMessageService messages;
    private final ConfigEditorMenu menu;
    private final ConfigEditorButton guiEnabledButton;
    private final ConfigEditorButton radiusButton;
    private final EditorSettingSaver settingSaver;

    public FoMobToggleEditor(FoMobToggle plugin, FoCoreContext core, FoMessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.guiEnabledButton = ConfigEditorButton.booleanSetting(
                "gui-enabled",
                "gui.enabled",
                "GUI Enabled",
                "buttons.gui-enabled",
                11,
                () -> plugin.getConfig().getBoolean("gui.enabled", true)
        ).toggleMaterials(Material.LIME_DYE, Material.RED_DYE).build();
        this.radiusButton = ConfigEditorButton.action("spawn-radius", "buttons.spawn-radius", 15)
                .fallbackMaterial(Material.COMPASS)
                .build();

        YamlConfiguration gui = new YamlConfiguration();
        gui.set("title", "&8FᴏMᴏʙTᴏɢɢʟᴇ Eᴅɪᴛᴏʀ");
        gui.set("size", 27);
        gui.set("filler.enabled", true);
        gui.set("filler.material", Material.GRAY_STAINED_GLASS_PANE.name());
        gui.set("buttons.gui-enabled.name", "{theme}GUI Enabled");
        gui.set("buttons.gui-enabled.lore", List.of(
                "{white}Click to toggle the public GUI.",
                "{muted}Current: {value}"
        ));
        gui.set("buttons.gui-enabled.enabled-material", Material.LIME_DYE.name());
        gui.set("buttons.gui-enabled.disabled-material", Material.RED_DYE.name());
        gui.set("buttons.spawn-radius.name", "{theme}Spawn Check Radius");
        gui.set("buttons.spawn-radius.lore", List.of(
                "{white}Click to edit the radius used for spawn checks.",
                "{muted}The current value is shown in the input.",
                "{muted}Range: {white}0-512"
        ));
        gui.set("buttons.spawn-radius.material", Material.COMPASS.name());

        this.settingSaver = new EditorSettingSaver(plugin, plugin::reloadConfig);
        this.menu = ConfigEditorMenu.builder(plugin, messages, gui)
                .id(EDITOR_ID)
                .title("title", "&8FᴏMᴏʙTᴏɢɢʟᴇ Eᴅɪᴛᴏʀ")
                .size("size", 27)
                .filler("filler", true, Material.GRAY_STAINED_GLASS_PANE)
                .booleanLabels("{good}Enabled", "{bad}Disabled")
                .reloadSettings(plugin::reloadConfig)
                .button(guiEnabledButton)
                .button(radiusButton)
                .build();
    }

    public void open(Player player) {
        if (!player.hasPermission("fomobtoggle.admin")) {
            messages.send(player, "messages.no-permission", "{prefix}{bad}You do not have permission to use this.");
            return;
        }
        menu.open(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorMenuHolder holder) || !menu.matches(holder)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }

        event.setCancelled(true);
        Optional<ConfigEditorButton> button = menu.buttonAt(holder, rawSlot);
        if (button.isEmpty()) {
            return;
        }
        handleClick(player, button.get());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorMenuHolder holder) || !menu.matches(holder)) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            core.inventoryCloseSuppressor().consumeSuppressedClose(player);
        }
    }

    private void handleClick(Player player, ConfigEditorButton button) {
        if (button.type() == ConfigEditorValueType.BOOLEAN) {
            EditorSaveResult result = menu.toggle(button);
            sendSaveResult(player, result, button.label());
            menu.open(player);
            return;
        }
        if (button.id().equals(radiusButton.id())) {
            openRadiusInput(player);
        }
    }

    private void openRadiusInput(Player player) {
        String current = formatRadius(plugin.getConfig().getDouble(RADIUS_PATH, 128.0D));
        TextDialogRequest request = TextDialogRequest.number(
                List.of(
                        messages.renderTemplate("{white}Set the spawn-check radius in blocks."),
                        messages.renderTemplate("{muted}Enter 0-512. Supports values such as {white}128{muted} or {white}2.5k{muted}.")
                ),
                current
        );
        EditorDialogInputs.openTextFromInventory(
                plugin,
                core.inventoryCloseSuppressor(),
                core.dialogService(),
                player,
                request,
                value -> saveRadius(player, value),
                () -> open(player)
        );
    }

    private void saveRadius(Player player, String input) {
        Optional<BigDecimal> parsed = LargeNumberParser.parse(input);
        if (parsed.isEmpty()
                || parsed.get().compareTo(BigDecimal.valueOf(MIN_RADIUS)) < 0
                || parsed.get().compareTo(BigDecimal.valueOf(MAX_RADIUS)) > 0) {
            messages.send(player, "messages.editor-invalid-radius",
                    "{prefix}{bad}Radius must be a number from {theme}{min}{bad} to {theme}{max}{bad}.",
                    Map.of("min", formatRadius(MIN_RADIUS), "max", formatRadius(MAX_RADIUS)));
            open(player);
            return;
        }

        double radius = parsed.get().doubleValue();
        if (!Double.isFinite(radius)) {
            messages.send(player, "messages.editor-invalid-radius",
                    "{prefix}{bad}Radius must be a finite number from {theme}{min}{bad} to {theme}{max}{bad}.",
                    Map.of("min", formatRadius(MIN_RADIUS), "max", formatRadius(MAX_RADIUS)));
            open(player);
            return;
        }

        EditorSaveResult result = settingSaver.save(RADIUS_PATH, radius);
        sendSaveResult(player, result, "Spawn Check Radius");
        open(player);
    }

    private void sendSaveResult(Player player, EditorSaveResult result, String setting) {
        if (result.successful()) {
            messages.send(player, "messages.editor-setting-saved",
                    "{prefix}{good}Saved {theme}{setting}{good}.",
                    Map.of("setting", setting));
            return;
        }
        String error = result.errorMessage().isBlank() ? "The value could not be saved." : result.errorMessage();
        messages.send(player, "messages.editor-setting-failed",
                "{prefix}{bad}Could not save {theme}{setting}{bad}. {muted}{error}",
                Map.of("setting", setting, "error", error));
    }

    private static String formatRadius(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
