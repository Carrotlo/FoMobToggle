package me.foesio.foMobToggle.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class ToggleMenuConfigMigrationTest {

    @Test
    void oldBundledStyleUpgradesWhileCustomOwnerTextSurvives() {
        YamlConfiguration legacy = legacyDefaults();
        legacy.set("gui.items.monster.name", "&dCustom Hostiles");
        legacy.set("gui.items.monster.lore", List.of("&5Translated line", "{state}"));
        legacy.set("gui.close.name", "&4Leave now");
        YamlConfiguration current = currentDefaults();

        assertTrue(ToggleMenuConfigMigration.migrateValues(
                legacy, current, currentDefaults(), false));

        assertEquals("&8ᴍᴏʙ ᴛᴏɢɢʟᴇ", current.getString("title"));
        assertEquals("&dCustom Hostiles", current.getString("items.monster.name"));
        assertEquals(List.of("&5Translated line", "{state}"), current.getStringList("items.monster.lore"));
        assertNull(current.get("items.close"));
    }

    @Test
    void releasedConfigOmittedLoreStaysOmittedInGeneratedStage() throws Exception {
        YamlConfiguration oldConfig = legacyDefaults();
        oldConfig.set("gui.items.mob.lore", null);
        YamlConfiguration oldGui = generatedDefaults();
        ToggleMenuConfigMigration.prepareLegacySplit(oldGui);
        ToggleMenuConfigMigration.preserveLegacyConfigOmissions(oldConfig, oldGui);
        ToggleMenuConfigMigration.migrateValues(oldConfig, oldGui, generatedDefaults(), false);
        YamlConfiguration source = new YamlConfiguration();
        source.loadFromString(oldGui.saveToString());
        ToggleMenuConfigMigration.migrateSchema4(oldGui, messageDefaults(),
                generatedDefaults(), messageDefaults(), new ArrayList<>());
        YamlConfiguration staged = ToggleMenuConfigMigration.stageGeneratedGui(
                source, oldGui, generatedDefaults());

        assertFalse(staged.contains("buttons.mob.lore"));
        ToggleMenu.validateMigrated(staged);
    }

    @Test
    void existingTargetCustomizationsAndDeletedValuesRemainAuthoritative() {
        YamlConfiguration legacy = legacyDefaults();
        legacy.set("gui.title", "&6Legacy title");
        legacy.set("gui.items.mob.lore", List.of("Legacy lore"));
        YamlConfiguration current = currentDefaults();
        current.set("title", "&bCurrent title");
        current.set("items.mob.lore", null);

        ToggleMenuConfigMigration.migrateValues(legacy, current, currentDefaults(), true);

        assertEquals("&bCurrent title", current.getString("title"));
        assertNull(current.get("items.mob.lore"));
    }

    @Test
    void customizedLegacyDynamicLabelsMoveIntoTheGuiFile() {
        YamlConfiguration messages = new YamlConfiguration();
        messages.set("messages.state.enabled", "&aAan");
        messages.set("messages.state.disabled", "{bad}Disabled");
        messages.set("messages.permission-mode.locked", "&cGeblokkeerd");
        messages.set("messages.permission-mode.gui", "{good}GUI toggle");
        YamlConfiguration current = currentDefaults();

        assertTrue(ToggleMenuConfigMigration.migrateVisibleLabels(
                messages, current, currentDefaults(), false));

        assertEquals("&aAan", current.getString("labels.state.enabled"));
        assertEquals("{bad}Disabled", current.getString("labels.state.disabled"));
        assertEquals("&cGeblokkeerd", current.getString("labels.permission-mode.locked"));
        assertEquals("{good}GUI toggle", current.getString("labels.permission-mode.gui"));
    }

    @Test
    void migrationIsIdempotentAndLegacyRemovalKeepsGuiEnabled() {
        YamlConfiguration legacy = legacyDefaults();
        legacy.set("gui.enabled", false);
        legacy.set("gui.close.name", "&4Custom close");
        legacy.set("gui.title", "&dTranslated title");
        YamlConfiguration current = currentDefaults();

        assertTrue(ToggleMenuConfigMigration.migrateValues(
                legacy, current, currentDefaults(), false));
        String once = current.saveToString();
        assertFalse(ToggleMenuConfigMigration.migrateValues(
                legacy, current, currentDefaults(), true));
        assertEquals(once, current.saveToString());

        ToggleMenuConfigMigration.removeLegacyGui(legacy);
        assertFalse(legacy.getBoolean("gui.enabled", true));
        assertFalse(ToggleMenuConfigMigration.hasLegacyGui(legacy));
    }

    @Test
    void schema4MovesCustomRowsItemsLabelsAndSelectiveFillerIntoCoreSchema() throws Exception {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(
                new java.io.File("src/main/resources/guis/toggle-menu.yml"));
        gui.set("rows", null);
        gui.set("size", 36);
        for (String key : List.of("mob", "monster", "passive")) {
            for (String leaf : gui.getConfigurationSection("buttons." + key).getKeys(false)) {
                if (!leaf.equals("action")) {
                    gui.set("items." + key + "." + leaf, gui.get("buttons." + key + "." + leaf));
                }
            }
        }
        gui.set("buttons", null);
        gui.set("items.monster.slot", 30);
        gui.set("items.monster.lore", List.of("Vertaalde Ω {state} {good}"));
        gui.set("items.mob.custom-model-data", "1234");
        gui.set("filler.slots", List.of(0, 11, 22));
        gui.set("labels.state.enabled", "AKTÍV");
        gui.set("labels.state.disabled", "UIT");
        gui.set("labels.permission-mode.locked", "SLOT");
        gui.set("labels.permission-mode.gui", "MENÜ");
        YamlConfiguration messages = new YamlConfiguration();

        assertTrue(migrateSchema4(gui, messages));
        assertEquals(4, gui.getInt("rows"));
        assertNull(gui.get("size"));
        assertNull(gui.get("items"));
        assertEquals(30, gui.getInt("buttons.monster.slot"));
        assertEquals(List.of("Vertaalde Ω {monster_state} {gui_good}"),
                gui.getStringList("buttons.monster.lore"));
        assertEquals(1234, gui.getInt("buttons.mob.custom-model-data"));
        assertEquals(List.of(0, 22), gui.getIntegerList("decorations.filler-region.slots"));
        assertEquals("AKTÍV", messages.getString("messages.gui-labels.state.enabled"));
        assertEquals("SLOT", messages.getString("messages.gui-labels.permission-mode.locked"));
        PublicGuiSchema.parse("fomobtoggle.main", "guis/toggle-menu.yml", gui,
                new PublicGuiContract(Set.of("toggle.mob", "toggle.monster", "toggle.passive"),
                        Map.of(), ToggleMenu.PLACEHOLDERS,
                        PublicGuiContract.InteractionMode.CORE));
        String first = gui.saveToString();
        assertFalse(migrateSchema4(gui, messages));
        assertEquals(first, gui.saveToString());
    }

    @Test
    void currentSchemaRemovalIsPreservedAndInvalidLegacySizeUsesGeneratedRows() throws Exception {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(
                new java.io.File("src/main/resources/guis/toggle-menu.yml"));
        gui.set("buttons.mob.lore", null);
        String first = gui.saveToString();
        assertFalse(migrateSchema4(gui, new YamlConfiguration()));
        assertEquals(first, gui.saveToString());

        gui.set("rows", null);
        gui.set("buttons", null);
        gui.set("size", 31);
        List<String> warnings = new ArrayList<>();
        assertTrue(ToggleMenuConfigMigration.migrateSchema4(gui, new YamlConfiguration(),
                generatedDefaults(), messageDefaults(), warnings));
        assertEquals(3, gui.getInt("rows"));
        assertFalse(gui.contains("size"));
        assertTrue(warnings.getFirst().contains("size=31"));
        ToggleMenu.validateMigrated(gui);
    }

    @Test
    void releasedConfigStagesGeneratedGuiWithTranslationAndInvalidMaterialFallback() throws Exception {
        YamlConfiguration legacy = legacyDefaults();
        legacy.set("gui.title", "&dEigen titel");
        legacy.set("gui.items.monster.name", "&bEigen monsters");
        legacy.set("gui.items.mob.material", "SPAWNER2");
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(
                new java.io.File("src/main/resources/guis/toggle-menu.yml"));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new java.io.File("src/main/resources/guis/toggle-menu.yml"));
        ToggleMenuConfigMigration.prepareLegacySplit(gui);
        assertTrue(ToggleMenuConfigMigration.migrateValues(legacy, gui, defaults, false));
        gui.set("items.monster.slot", 20);
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString(gui.saveToString());
        List<String> warnings = new ArrayList<>();
        ToggleMenuConfigMigration.migrateSchema4(gui, messageDefaults(),
                defaults, messageDefaults(), warnings);
        YamlConfiguration staged = ToggleMenuConfigMigration.stageGeneratedGui(old, gui, defaults);
        ToggleMenuConfigMigration.validateLegacyWithDefaults(staged, defaults, warnings);
        assertEquals("&dEigen titel", staged.getString("title"));
        assertEquals("&bEigen monsters", staged.getString("buttons.monster.name"));
        assertEquals("SPAWNER", staged.getString("buttons.mob.material"));
        assertEquals(20, staged.getInt("buttons.monster.slot"));
        assertEquals(3, staged.getInt("rows"));
        assertFalse(staged.contains("items"));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("SPAWNER2")));
        ToggleMenu.validateMigrated(staged);
    }

    @Test
    void invalidLegacyMaterialsUseGeneratedDefaultsAndTranslatedLabelsSurvive() throws Exception {
        YamlConfiguration gui = generatedDefaults();
        ToggleMenuConfigMigration.prepareLegacySplit(gui);
        gui.set("items.mob.material", "SPAWNER2");
        gui.set("items.monster.material", "DIAMOND_SWORD");
        gui.set("filler.material", "NOT_GLASS");
        gui.set("items.mob.name", "&dMy own mob name");
        gui.set("labels.state.enabled", "&aAKTÍV Ω");
        gui.set("labels.permission-mode.locked", "&cVAST ✓");
        YamlConfiguration messages = messageDefaults();
        messages.set("messages.permission-mode.locked", "Old obsolete value");
        messages.set("messages.permission-mode.gui", "Old obsolete value");
        List<String> warnings = new ArrayList<>();

        assertTrue(ToggleMenuConfigMigration.migrateSchema4(gui, messages,
                generatedDefaults(), messageDefaults(), warnings));
        assertEquals("SPAWNER", gui.getString("buttons.mob.material"));
        assertEquals("DIAMOND_SWORD", gui.getString("buttons.monster.material"));
        assertEquals("GRAY_STAINED_GLASS_PANE", gui.getString("filler.material"));
        assertEquals("&dMy own mob name", gui.getString("buttons.mob.name"));
        assertEquals("&aAKTÍV Ω", messages.getString("messages.gui-labels.state.enabled"));
        assertEquals("&cVAST ✓", messages.getString("messages.gui-labels.permission-mode.locked"));
        assertFalse(messages.contains("messages.permission-mode.locked"));
        assertFalse(messages.contains("messages.permission-mode.gui"));
        assertTrue(messages.contains("messages.state.enabled"));
        assertEquals(2, warnings.size());
        assertTrue(warnings.getFirst().contains("SPAWNER2"));
        ToggleMenu.validateMigrated(gui);
        String migrated = gui.saveToString();
        assertFalse(migrateSchema4(gui, messages));
        assertEquals(migrated, gui.saveToString());
    }

    @Test
    void currentSchemaInvalidMaterialIsNotSilentlyReplaced() {
        YamlConfiguration gui = generatedDefaults();
        gui.set("buttons.mob.material", "SPAWNER2");
        String original = gui.saveToString();
        assertFalse(migrateSchema4(gui, messageDefaults()));
        assertEquals(original, gui.saveToString());
        assertThrows(me.foesio.core.gui.publicgui.GuiDefinitionException.class,
                () -> ToggleMenu.validateMigrated(gui));
    }

    @Test
    void oldDefaultZombieSpriteUpdatesOnceButOwnerNamesStayUntouched() {
        YamlConfiguration defaults = generatedDefaults();
        YamlConfiguration owner = generatedDefaults();
        owner.set("buttons.monster.name", ":zombie_head: {monster_state_color}&lMONSTERS");
        assertTrue(ToggleMenuConfigMigration.migrateOldDefaultSprite(owner, defaults));
        assertEquals(":zombie_spawn_egg: {monster_state_color}&lMONSTERS",
                owner.getString("buttons.monster.name"));
        assertFalse(ToggleMenuConfigMigration.migrateOldDefaultSprite(owner, defaults));

        owner.set("buttons.monster.name", "&dEigen monsters");
        assertFalse(ToggleMenuConfigMigration.migrateOldDefaultSprite(owner, defaults));
        assertEquals("&dEigen monsters", owner.getString("buttons.monster.name"));
        owner.set("buttons.monster.name", "{monster_state_color}&lMONSTERS");
        assertFalse(ToggleMenuConfigMigration.migrateOldDefaultSprite(owner, defaults));
        assertEquals("{monster_state_color}&lMONSTERS", owner.getString("buttons.monster.name"));
    }

    @Test
    void versionedUpgradeRepairsMissingRequiredFieldWithoutRestoringOptionalLore() throws Exception {
        YamlConfiguration old = generatedDefaults();
        old.set("buttons.mob.action", null);
        old.set("buttons.mob.lore", null);
        old.set("buttons.monster.name", "&dOwner name Ω");
        List<String> warnings = new ArrayList<>();

        ToggleMenuConfigMigration.validateLegacyWithDefaults(old, generatedDefaults(), warnings);

        assertEquals("toggle.mob", old.getString("buttons.mob.action"));
        assertFalse(old.contains("buttons.mob.lore"));
        assertEquals("&dOwner name Ω", old.getString("buttons.monster.name"));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("buttons.mob.action")));
        ToggleMenu.validateMigrated(old);
    }

    @Test
    void otherMalformedLegacyFieldsUseGeneratedDefaultsButValidCustomTextSurvives() throws Exception {
        YamlConfiguration gui = generatedDefaults();
        ToggleMenuConfigMigration.prepareLegacySplit(gui);
        gui.set("size", "wrong");
        gui.set("title", List.of("not a title"));
        gui.set("items.mob.amount", 99);
        gui.set("items.mob.slot", -2);
        gui.set("items.mob.item-flags", List.of("NOT_AN_ITEM_FLAG"));
        gui.set("items.monster.enabled", "maybe");
        gui.set("items.monster.custom-model-data", "not-a-number");
        gui.set("items.passive.lore", List.of("valid custom line", 7));
        gui.set("items.passive.name", "&dValid owner name");
        gui.set("filler.slots", List.of(0, -1));
        List<String> warnings = new ArrayList<>();

        assertTrue(ToggleMenuConfigMigration.migrateSchema4(gui, new YamlConfiguration(),
                generatedDefaults(), messageDefaults(), warnings));
        ToggleMenuConfigMigration.validateLegacyWithDefaults(gui, generatedDefaults(), warnings);
        assertEquals(3, gui.getInt("rows"));
        assertEquals(generatedDefaults().getString("title"), gui.getString("title"));
        assertEquals(1, gui.getInt("buttons.mob.amount"));
        assertEquals(11, gui.getInt("buttons.mob.slot"));
        assertEquals(generatedDefaults().getStringList("buttons.mob.item-flags"),
                gui.getStringList("buttons.mob.item-flags"));
        assertTrue(gui.getBoolean("buttons.monster.enabled"));
        assertFalse(gui.contains("buttons.monster.custom-model-data"));
        assertEquals(generatedDefaults().getStringList("buttons.passive.lore"),
                gui.getStringList("buttons.passive.lore"));
        assertEquals("&dValid owner name", gui.getString("buttons.passive.name"));
        assertFalse(gui.contains("decorations.filler-region"));
        assertTrue(warnings.size() >= 7);
        ToggleMenu.validateMigrated(gui);
    }

    @Test
    void malformedLegacySectionAndConflictingSlotsUseDefaultsWithoutLosingOtherOverrides() throws Exception {
        YamlConfiguration gui = generatedDefaults();
        ToggleMenuConfigMigration.prepareLegacySplit(gui);
        gui.set("items.mob", "broken section");
        gui.set("items.monster.slot", 15);
        gui.set("items.passive.name", "&eOwner passive name");
        gui.set("labels.state.enabled", List.of("broken label"));
        YamlConfiguration messages = messageDefaults();
        List<String> warnings = new ArrayList<>();

        assertTrue(ToggleMenuConfigMigration.migrateSchema4(gui, messages,
                generatedDefaults(), messageDefaults(), warnings));
        ToggleMenuConfigMigration.validateLegacyWithDefaults(gui, generatedDefaults(), warnings);

        assertEquals(generatedDefaults().getString("buttons.mob.material"),
                gui.getString("buttons.mob.material"));
        assertEquals(generatedDefaults().getInt("buttons.monster.slot"),
                gui.getInt("buttons.monster.slot"));
        assertEquals(generatedDefaults().getInt("buttons.passive.slot"),
                gui.getInt("buttons.passive.slot"));
        assertEquals("&eOwner passive name", gui.getString("buttons.passive.name"));
        assertEquals(messageDefaults().getString("messages.gui-labels.state.enabled"),
                messages.getString("messages.gui-labels.state.enabled"));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("items.mob")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("buttons.monster.slot")));
        ToggleMenu.validateMigrated(gui);
    }

    @Test
    void finalOldGuiStageStartsFromGeneratedDefaultsAndDropsRetiredKeys() throws Exception {
        YamlConfiguration old = generatedDefaults();
        ToggleMenuConfigMigration.prepareLegacySplit(old);
        old.set("items.mob.lore", null);
        old.set("items.mob.item-flags", null);
        old.set("filler.item-flags", null);
        old.set("items.monster.name", "&dOwner monsters Ω");
        old.set("items.close.material", "BARRIER");
        old.set("unrelated-old-option", "discard me");
        YamlConfiguration migrated = new YamlConfiguration();
        migrated.loadFromString(old.saveToString());
        List<String> warnings = new ArrayList<>();

        ToggleMenuConfigMigration.migrateSchema4(migrated, messageDefaults(),
                generatedDefaults(), messageDefaults(), warnings);
        YamlConfiguration staged = ToggleMenuConfigMigration.stageGeneratedGui(
                old, migrated, generatedDefaults());
        ToggleMenuConfigMigration.validateLegacyWithDefaults(staged, generatedDefaults(), warnings);

        assertFalse(staged.contains("buttons.mob.lore"));
        assertFalse(staged.contains("buttons.mob.item-flags"));
        assertFalse(staged.contains("filler.item-flags"));
        assertEquals("&dOwner monsters Ω", staged.getString("buttons.monster.name"));
        assertEquals(generatedDefaults().getString("buttons.passive.name"),
                staged.getString("buttons.passive.name"));
        assertFalse(staged.contains("items"));
        assertFalse(staged.contains("size"));
        assertFalse(staged.contains("labels"));
        assertFalse(staged.contains("unrelated-old-option"));
        ToggleMenu.validateMigrated(staged);
    }

    private static boolean migrateSchema4(YamlConfiguration gui, YamlConfiguration messages) {
        return ToggleMenuConfigMigration.migrateSchema4(gui, messages,
                generatedDefaults(), messageDefaults(), new ArrayList<>());
    }

    private static YamlConfiguration generatedDefaults() {
        return YamlConfiguration.loadConfiguration(
                new java.io.File("src/main/resources/guis/toggle-menu.yml"));
    }

    private static YamlConfiguration messageDefaults() {
        return YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/messages.yml"));
    }

    private static YamlConfiguration legacyDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("gui.title", "&8Mob Toggle");
        config.set("gui.items.mob.material", "SPAWNER");
        config.set("gui.items.mob.name", "{state_color}All Mobs");
        config.set("gui.items.mob.lore", List.of(
                "{white}Master toggle for every mob type.",
                "{white}Current result: {state}",
                "{white}Saved GUI state: {saved_state}",
                "{white}Mode: {permission_mode}", "", "{white}Click to switch this setting."));
        config.set("gui.items.monster.material", "ZOMBIE_HEAD");
        config.set("gui.items.monster.name", "{state_color}Monsters");
        config.set("gui.items.monster.lore", List.of(
                "{white}Blocks natural hostile mob spawns.",
                "{white}Current result: {state}",
                "{white}Saved GUI state: {saved_state}",
                "{white}Mode: {permission_mode}", "", "{white}Click to switch this setting."));
        config.set("gui.items.passive.material", "WHEAT");
        config.set("gui.items.passive.name", "{state_color}Passive Mobs");
        config.set("gui.items.passive.lore", List.of(
                "{white}Blocks natural passive mob spawns.",
                "{white}Current result: {state}",
                "{white}Saved GUI state: {saved_state}",
                "{white}Mode: {permission_mode}", "", "{white}Click to switch this setting."));
        config.set("gui.close.name", "&cClose");
        config.set("gui.close.lore", List.of("{white}Click to close this menu."));
        return config;
    }

    private static YamlConfiguration currentDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("title", "&8ᴍᴏʙ ᴛᴏɢɢʟᴇ");
        config.set("items.mob.name", ":spawner: {state_color}&lALL MOBS");
        config.set("items.mob.lore", List.of("Current default lore"));
        config.set("items.monster.name", ":zombie_head: {state_color}&lMONSTERS");
        config.set("items.monster.lore", List.of("Current monster lore"));
        config.set("items.passive.name", ":wheat: {state_color}&lPASSIVE MOBS");
        config.set("items.passive.lore", List.of("Current passive lore"));
        config.set("labels.state.enabled", "{good}Enabled");
        config.set("labels.state.disabled", "{bad}Disabled");
        config.set("labels.permission-mode.locked", "{bad}Permission override");
        config.set("labels.permission-mode.gui", "{good}GUI toggle");
        return config;
    }
}
