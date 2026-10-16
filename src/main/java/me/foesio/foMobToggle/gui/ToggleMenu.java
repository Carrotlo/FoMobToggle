package me.foesio.foMobToggle.gui;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import me.foesio.core.gui.publicgui.GuiDefinitionException;
import me.foesio.core.gui.publicgui.GuiLoadReport;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiService;
import me.foesio.core.gui.publicgui.PublicGuiText;
import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.data.PlayerSettings;
import me.foesio.foMobToggle.data.PlayerSettingsManager;
import me.foesio.foMobToggle.model.ToggleCategory;
import me.foesio.foMobToggle.service.SpawnPolicyService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Public menu: Core owns loading, rendering, inventory holders, and click routing. */
public final class ToggleMenu implements AutoCloseable {

    private static final String MENU_ID = "fomobtoggle.main";
    private static final String ENTRY_POINT = "command:/fomobtoggle";
    static final Set<String> PLACEHOLDERS = Set.of(
            "gui_theme", "gui_muted", "gui_white", "gui_yellow", "gui_good", "gui_bad",
            "mob_state_color", "mob_state", "mob_saved_state", "mob_permission_mode",
            "monster_state_color", "monster_state", "monster_saved_state", "monster_permission_mode",
            "passive_state_color", "passive_state", "passive_saved_state", "passive_permission_mode");
    private static final PublicGuiContract CONTRACT = new PublicGuiContract(
            Set.of("toggle.mob", "toggle.monster", "toggle.passive"), Map.of(),
            PLACEHOLDERS, PublicGuiContract.InteractionMode.CORE);

    private final PublicPresentation presentation;
    private final PlayerSettingsManager playerSettingsManager;
    private final SpawnPolicyService spawnPolicyService;
    private final PublicGuiService guis;
    private final boolean migrationReady;

    public ToggleMenu(FoMobToggle plugin, PublicPresentation presentation,
                      PlayerSettingsManager playerSettingsManager, SpawnPolicyService spawnPolicyService,
                      File guiFile, boolean migrationReady) {
        this.presentation = presentation;
        this.playerSettingsManager = playerSettingsManager;
        this.spawnPolicyService = spawnPolicyService;
        this.migrationReady = migrationReady;
        this.guis = new PublicGuiService(plugin)
                .menu(MENU_ID, guiFile.toPath(), CONTRACT)
                .action("toggle.mob", click -> toggle(click.player(), ToggleCategory.ALL_MOBS))
                .action("toggle.monster", click -> toggle(click.player(), ToggleCategory.MONSTERS))
                .action("toggle.passive", click -> toggle(click.player(), ToggleCategory.PASSIVE))
                .entryPoint(ENTRY_POINT, MENU_ID)
                .entryPoint("command:/mobtoggle", MENU_ID)
                .entryPoint("command:/togglemob", MENU_ID)
                .entryPoint("command:/togglemobs", MENU_ID);
        guis.start();
        if (migrationReady) {
            try {
                reloadConfig();
            } catch (IllegalStateException exception) {
                plugin.getLogger().warning(exception.getMessage() + "; the public menu is suppressed.");
            }
        }
    }

    public boolean isAvailable() {
        return migrationReady && guis.snapshot() != null;
    }

    public void open(Player player) {
        if (!isAvailable()) {
            presentation.send(player, "messages.gui-unavailable");
            return;
        }
        guis.openEntryPoint(player, ENTRY_POINT, renderInput(player));
        presentation.play(player, "gui.open");
    }

    public void reloadConfig() {
        if (!migrationReady) {
            throw new IllegalStateException("The public GUI migration is pending; restart after correcting the logged error");
        }
        GuiLoadReport report = guis.reload();
        if (!report.successful()) {
            throw new IllegalStateException("Public GUI reload failed: " + report.diagnostics().size()
                    + " error(s); " + (report.activeRevision() == 0
                    ? "no menu is active" : "the previous working menu remains active")
                    + ". Check the server log for file and key details");
        }
    }

    public static void validateMigrated(YamlConfiguration yaml) throws GuiDefinitionException {
        me.foesio.core.gui.publicgui.PublicGuiSchema.parse(
                MENU_ID, "guis/toggle-menu.yml", yaml, CONTRACT);
    }

    private void toggle(Player player, ToggleCategory category) {
        if (player.isPermissionSet(category.getPermission())) {
            presentation.send(player, "messages.locked-by-permission");
            presentation.play(player, "gui.error");
            return;
        }
        PlayerSettings settings = playerSettingsManager.get(player.getUniqueId());
        boolean newValue = !settings.isEnabled(category);
        playerSettingsManager.set(player.getUniqueId(), category, newValue);
        PublicGuiRenderInput input = renderInput(player);
        guis.openEntryPoint(player, ENTRY_POINT, input);
        PublicGuiDefinition.Button button = guis.snapshot().require(MENU_ID).buttons().get(category.getConfigKey());
        String name = button == null || button.item() == null || button.item().name() == null ? ""
                : PublicGuiText.scalar(button.item().name(), input.placeholders(),
                        "buttons." + category.getConfigKey() + ".name");
        String state = presentation.render(newValue ? "messages.state.enabled" : "messages.state.disabled");
        presentation.send(player, "messages.toggle-updated", Map.of("toggle", name, "state", state));
        presentation.play(player, newValue ? "mob-toggle.enabled" : "mob-toggle.disabled");
    }

    private PublicGuiRenderInput renderInput(Player player) {
        Map<ToggleCategory, Boolean> effective = new EnumMap<>(ToggleCategory.class);
        Map<ToggleCategory, Boolean> stored = new EnumMap<>(ToggleCategory.class);
        Map<ToggleCategory, Boolean> locked = new EnumMap<>(ToggleCategory.class);
        PlayerSettings saved = playerSettingsManager.get(player.getUniqueId());
        for (ToggleCategory category : ToggleCategory.values()) {
            effective.put(category, spawnPolicyService.isEffectiveCategoryEnabled(player, category));
            stored.put(category, saved.isEnabled(category));
            locked.put(category, player.isPermissionSet(category.getPermission()));
        }
        return renderInput(presentation, effective, stored, locked);
    }

    static PublicGuiRenderInput renderInput(PublicPresentation presentation,
                                           Map<ToggleCategory, Boolean> effective,
                                           Map<ToggleCategory, Boolean> stored,
                                           Map<ToggleCategory, Boolean> locked) {
        Map<String, String> values = new HashMap<>();
        for (String style : Set.of("theme", "muted", "white", "yellow", "good", "bad")) {
            values.put("gui_" + style, presentation.renderTemplate("{" + style + "}", Map.of()));
        }
        for (ToggleCategory category : ToggleCategory.values()) {
            String prefix = category.getConfigKey() + "_";
            values.put(prefix + "state_color", values.get(effective.get(category) ? "gui_good" : "gui_bad"));
            values.put(prefix + "state", guiState(presentation, effective.get(category)));
            values.put(prefix + "saved_state", guiState(presentation, stored.get(category)));
            values.put(prefix + "permission_mode", presentation.render(locked.get(category)
                    ? "messages.gui-labels.permission-mode.locked"
                    : "messages.gui-labels.permission-mode.gui"));
        }
        return new PublicGuiRenderInput(values, Map.of(), Map.of(), Map.of());
    }

    private static String guiState(PublicPresentation presentation, boolean enabled) {
        return presentation.render(enabled
                ? "messages.gui-labels.state.enabled" : "messages.gui-labels.state.disabled");
    }

    @Override
    public void close() {
        guis.close();
    }
}
