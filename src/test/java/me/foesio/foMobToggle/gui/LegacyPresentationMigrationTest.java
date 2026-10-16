package me.foesio.foMobToggle.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class LegacyPresentationMigrationTest {

    @Test
    void malformedOldMessageAndSoundFieldsUseBundledDefaultsAndKeepCustomSiblings() {
        YamlConfiguration messageDefaults = defaults("messages.yml");
        YamlConfiguration messages = defaults("messages.yml");
        messages.set("messages.toggle-updated", List.of("good", 7));
        messages.set("messages.locked-by-permission", "&dCustom denial");
        messages.set("tokens.good", 4);
        messages.set("messages.state.enabled", List.of("invalid multiline state"));
        messages.set("messages.state.disabled", "Uit ✓");
        List<String> warnings = new ArrayList<>();

        LegacyPresentationMigration.repairMessages(messages, messageDefaults, warnings);
        assertEquals(messageDefaults.getString("messages.toggle-updated"),
                messages.getString("messages.toggle-updated"));
        assertEquals(messageDefaults.getString("tokens.good"), messages.getString("tokens.good"));
        assertEquals("&dCustom denial", messages.getString("messages.locked-by-permission"));
        assertEquals(messageDefaults.getString("messages.state.enabled"),
                messages.getString("messages.state.enabled"));
        assertEquals("Uit ✓", messages.getString("messages.state.disabled"));

        YamlConfiguration soundDefaults = defaults("sounds.yml");
        YamlConfiguration sounds = defaults("sounds.yml");
        sounds.set("mob-toggle.enabled.category", "BAD_CATEGORY");
        sounds.set("mob-toggle.enabled.volume", -1);
        sounds.set("mob-toggle.enabled.pitch", 5);
        sounds.set("mob-toggle.enabled.pitch-variation", -0.2);
        sounds.set("mob-toggle.enabled.sound", "bad sound key");
        sounds.set("mob-toggle.disabled.sound", "example:custom_sound");
        sounds.set("mob-toggle.disabled.enabled", false);
        sounds.set("gui.open", "broken section");

        LegacyPresentationMigration.repairSounds(sounds, soundDefaults, warnings);
        assertEquals(soundDefaults.getString("mob-toggle.enabled.category"),
                sounds.getString("mob-toggle.enabled.category"));
        assertEquals(soundDefaults.getDouble("mob-toggle.enabled.volume"),
                sounds.getDouble("mob-toggle.enabled.volume"));
        assertEquals(soundDefaults.getDouble("mob-toggle.enabled.pitch"),
                sounds.getDouble("mob-toggle.enabled.pitch"));
        assertEquals(soundDefaults.getDouble("mob-toggle.enabled.pitch-variation"),
                sounds.getDouble("mob-toggle.enabled.pitch-variation"));
        assertEquals(soundDefaults.getString("mob-toggle.enabled.sound"),
                sounds.getString("mob-toggle.enabled.sound"));
        assertEquals("example:custom_sound", sounds.getString("mob-toggle.disabled.sound"));
        assertFalse(sounds.getBoolean("mob-toggle.disabled.enabled"));
        assertEquals(soundDefaults.getString("gui.open.sound"), sounds.getString("gui.open.sound"));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("gui.open")));
    }

    @Test
    void releasedConfigSplitKeepsTranslationsAndRemovedOptionalValues() {
        YamlConfiguration old = new YamlConfiguration();
        old.set("tokens.prefix", "{theme}FoMobToggle &8» {muted}");
        old.set("messages.state.enabled", "&aAan Ω");
        old.set("messages.toggle-updated", "{state} ← {toggle}");
        YamlConfiguration target = defaults("messages.yml");

        LegacyPresentationMigration.splitReleasedConfig(old, target);

        assertEquals(defaults("messages.yml").getString("tokens.prefix"),
                target.getString("tokens.prefix"));
        assertEquals("&aAan Ω", target.getString("messages.state.enabled"));
        assertEquals("{state} ← {toggle}", target.getString("messages.toggle-updated"));
        assertFalse(target.contains("messages.state.disabled"));
        assertFalse(target.contains("messages.no-permission"));
        assertTrue(target.contains("messages.gui-unavailable"));
    }

    private static YamlConfiguration defaults(String name) {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/" + name));
    }
}
