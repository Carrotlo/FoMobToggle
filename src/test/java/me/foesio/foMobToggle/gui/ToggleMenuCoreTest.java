package me.foesio.foMobToggle.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import me.foesio.core.gui.publicgui.GuiDefinitionException;
import me.foesio.core.gui.publicgui.GuiSnapshot;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiFrame;
import me.foesio.core.gui.publicgui.PublicGuiPlanner;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.PublicGuiStartupFallback;
import me.foesio.core.gui.publicgui.PublicGuiText;
import me.foesio.foMobToggle.model.ToggleCategory;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ToggleMenuCoreTest {

    @TempDir Path folder;

    private static final String MENU = "fomobtoggle.main";
    private static final PublicGuiContract CONTRACT = new PublicGuiContract(
            Set.of("toggle.mob", "toggle.monster", "toggle.passive"), Map.of(),
            ToggleMenu.PLACEHOLDERS, PublicGuiContract.InteractionMode.CORE);

    @Test
    void generatedDefaultsUseRowsAndCoreRoutesAllThreeButtons() throws Exception {
        YamlConfiguration yaml = defaults();
        PublicGuiDefinition definition = parse(yaml);
        PublicGuiFrame frame = plan(definition);
        assertEquals(3, yaml.getInt("rows"));
        assertFalse(yaml.contains("size"));
        assertEquals(27, definition.size());
        assertEquals("toggle.mob", frame.bindings().get(11).action());
        assertEquals("toggle.monster", frame.bindings().get(13).action());
        assertEquals("toggle.passive", frame.bindings().get(15).action());
        assertNull(frame.bindings().get(22));
        assertTrue(frame.visuals().containsKey(22));
        assertNull(yaml.get("buttons.close"));
    }

    @Test
    void movedAndDisabledControlsChangeTheCoreRenderAndActionMapTogether() throws Exception {
        YamlConfiguration yaml = defaults();
        yaml.set("rows", 4);
        yaml.set("buttons.monster.slot", 30);
        PublicGuiFrame moved = plan(parse(yaml));
        assertEquals(36, moved.definition().size());
        assertEquals("toggle.monster", moved.bindings().get(30).action());
        assertNull(moved.bindings().get(13));
        assertEquals(Material.ZOMBIE_HEAD, moved.visuals().get(30).fixed().material());
        assertTrue(moved.visuals().get(30).fixed().name().startsWith(":zombie_spawn_egg:"));
        yaml.set("buttons.monster.enabled", false);
        PublicGuiFrame disabled = plan(parse(yaml));
        assertNull(disabled.bindings().get(30));
        assertNull(disabled.bindings().get(13));
    }

    @Test
    void configuredUtf8NameLoreSpriteRemovalAndMetadataReachCoreFrame() throws Exception {
        YamlConfiguration yaml = defaults();
        yaml.set("buttons.monster.name", "NÁAM Ω {monster_state}");
        yaml.set("buttons.monster.lore", List.of("LŐRE ✓ {monster_saved_state}", ""));
        yaml.set("buttons.monster.custom-model-data", 2468);
        yaml.set("buttons.monster.glow", true);
        PublicGuiFrame.Visual visual = plan(parse(yaml)).visuals().get(13);
        assertEquals("NÁAM Ω Aan", PublicGuiText.scalar(visual.fixed().name(),
                visual.placeholders(), "buttons.monster.name"));
        assertEquals(List.of("LŐRE ✓ Uit", ""), PublicGuiText.lore(visual.fixed().lore(),
                visual.placeholders(), null, null, "buttons.monster.lore"));
        assertEquals(2468, visual.fixed().customModelData());
        assertTrue(visual.fixed().glow());
        assertFalse(visual.fixed().name().contains(":zombie_spawn_egg:"));
        yaml.set("buttons.monster.lore", List.of());
        assertTrue(plan(parse(yaml)).visuals().get(13).fixed().lore().isEmpty());
    }

    @Test
    void ownerStateAndPermissionLabelsReachRenderedCoreItems() throws Exception {
        YamlConfiguration messages = new YamlConfiguration();
        messages.set("messages.gui-labels.state.enabled", "AKTÍV Ω");
        messages.set("messages.gui-labels.state.disabled", "UIT ✓");
        messages.set("messages.gui-labels.permission-mode.locked", "VAST 🔒");
        messages.set("messages.gui-labels.permission-mode.gui", "VRIJ ♢");
        messages.save(folder.resolve("messages.yml").toFile());
        PublicPresentation presentation = new PublicPresentation(folder.toFile());
        Map<ToggleCategory, Boolean> effective = Map.of(
                ToggleCategory.ALL_MOBS, true, ToggleCategory.MONSTERS, true,
                ToggleCategory.PASSIVE, false);
        Map<ToggleCategory, Boolean> stored = Map.of(
                ToggleCategory.ALL_MOBS, true, ToggleCategory.MONSTERS, false,
                ToggleCategory.PASSIVE, false);
        Map<ToggleCategory, Boolean> locked = Map.of(
                ToggleCategory.ALL_MOBS, false, ToggleCategory.MONSTERS, true,
                ToggleCategory.PASSIVE, false);
        PublicGuiRenderInput input = ToggleMenu.renderInput(presentation, effective, stored, locked);
        YamlConfiguration yaml = defaults();
        yaml.set("buttons.monster.name", "ÉTAT {monster_state}");
        yaml.set("buttons.monster.lore", List.of("{monster_permission_mode}",
                "{monster_saved_state} / {monster_state}"));
        PublicGuiFrame frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1, Map.of(MENU, parse(yaml))),
                MENU, input);
        PublicGuiFrame.Visual visual = frame.visuals().get(13);
        assertEquals("ÉTAT AKTÍV Ω", PublicGuiText.scalar(visual.fixed().name(),
                visual.placeholders(), "buttons.monster.name"));
        assertEquals(List.of("VAST 🔒", "UIT ✓ / AKTÍV Ω"), PublicGuiText.lore(
                visual.fixed().lore(), visual.placeholders(), null, null, "buttons.monster.lore"));
        assertEquals("VRIJ ♢", input.placeholders().get("mob_permission_mode"));
        assertEquals("UIT ✓", input.placeholders().get("passive_state"));
    }

    @Test
    void coreRejectsMissingRowsAndCollisions() throws Exception {
        YamlConfiguration yaml = defaults();
        yaml.set("rows", null);
        assertThrows(GuiDefinitionException.class, () -> parse(yaml));
        yaml.set("rows", 3);
        yaml.set("buttons.monster.slot", 11);
        assertThrows(GuiDefinitionException.class, () -> parse(yaml));
    }

    @Test
    void coldStartupRepairsOnlyInvalidFieldInMemory() throws Exception {
        YamlConfiguration installed = defaults();
        installed.set("rows", "invalid");
        installed.set("buttons.monster.name", "NÁAM Ω");
        installed.set("buttons.monster.lore", List.of());
        installed.set("buttons.mob.name", "without sprite");
        Path ownerFile = folder.resolve("toggle-menu.yml");
        installed.save(ownerFile.toFile());
        byte[] originalBytes = java.nio.file.Files.readAllBytes(ownerFile);
        List<PublicGuiStartupFallback.Replacement> replacements = new java.util.ArrayList<>();

        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(ownerFile.toFile());
        PublicGuiDefinition recovered = PublicGuiStartupFallback.parse(
                MENU, "guis/toggle-menu.yml", loaded, defaults(), CONTRACT, replacements::add);

        assertEquals(3, recovered.rows());
        assertEquals("NÁAM Ω", recovered.buttons().get("monster").item().name());
        assertTrue(recovered.buttons().get("monster").item().lore().isEmpty());
        assertEquals("without sprite", recovered.buttons().get("mob").item().name());
        assertEquals(1, replacements.size());
        assertEquals("rows", replacements.get(0).path());
        assertEquals(3, replacements.get(0).defaultValue());
        // Core repairs the parsed candidate; it leaves the owner file byte-for-byte intact.
        assertTrue(java.util.Arrays.equals(originalBytes, java.nio.file.Files.readAllBytes(ownerFile)));

        YamlConfiguration missing = defaults();
        missing.set("rows", null);
        assertEquals(3, PublicGuiStartupFallback.parse(MENU, "guis/toggle-menu.yml",
                missing, defaults(), CONTRACT, ignored -> { }).rows());
    }

    private static YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/guis/toggle-menu.yml"));
    }

    private static PublicGuiDefinition parse(YamlConfiguration yaml) throws GuiDefinitionException {
        return PublicGuiSchema.parse(MENU, "guis/toggle-menu.yml", yaml, CONTRACT);
    }

    private static PublicGuiFrame plan(PublicGuiDefinition definition) {
        Map<String, String> values = new HashMap<>();
        for (String key : ToggleMenu.PLACEHOLDERS) {
            values.put(key, "");
        }
        values.put("monster_state", "Aan");
        values.put("monster_saved_state", "Uit");
        return PublicGuiPlanner.plan(new GuiSnapshot<>(1, Map.of(MENU, definition)), MENU,
                new PublicGuiRenderInput(values, Map.of(), Map.of(), Map.of()));
    }
}
