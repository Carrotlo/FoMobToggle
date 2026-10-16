package me.foesio.foMobToggle.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublicPresentationTest {

    @TempDir Path folder;

    @Test
    void bundledMessagesAndPublicSoundsParse() {
        PublicPresentation presentation = new PublicPresentation(new File("src/main/resources"));
        assertTrue(presentation.render("messages.toggle-updated").contains("Updated"));
    }

    @Test
    void configuredUtf8TextAndReorderedPlaceholdersReachFinalSender() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tokens.prefix", "&aΩ: ");
        yaml.set("messages.toggle-updated", "{state} ← {toggle} {prefix}");
        yaml.set("messages.state.enabled", "Aan ✓");
        yaml.save(new File(folder.toFile(), "messages.yml"));
        PublicPresentation presentation = new PublicPresentation(folder.toFile());
        List<String> sent = new ArrayList<>();
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage") && args.length == 1 && args[0] instanceof String text) {
                        sent.add(text);
                    }
                    return defaultValue(method.getReturnType());
                });
        presentation.send(sender, "messages.toggle-updated",
                Map.of("toggle", "Monsters", "state", presentation.render("messages.state.enabled")));
        assertEquals(1, sent.size());
        assertTrue(sent.getFirst().contains("Aan ✓ ← Monsters"));
        assertTrue(sent.getFirst().contains("Ω:"));
    }

    @Test
    void removedSpriteEmptyMessageAndDeletedKeyStayAbsentAfterReload() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("messages.optional", "");
        yaml.set("tokens.theme", "&a");
        File file = new File(folder.toFile(), "messages.yml");
        yaml.save(file);
        PublicPresentation presentation = new PublicPresentation(folder.toFile());
        assertEquals("", presentation.render("messages.optional"));
        assertEquals("", presentation.render("messages.missing"));
        assertFalse(presentation.renderTemplate("No icon here", Map.of()).contains(":spawner:"));
        presentation.reload();
        assertEquals("", presentation.render("messages.missing"));
        assertFalse(YamlConfiguration.loadConfiguration(file).contains("messages.missing"));
    }

    @Test
    void soundUsesOwnerCategoryVolumePitchAndDisable() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("gui.open.enabled", true);
        yaml.set("gui.open.sound", "minecraft:block.note_block.pling");
        yaml.set("gui.open.category", "PLAYERS");
        yaml.set("gui.open.volume", 0.37);
        yaml.set("gui.open.pitch", 1.25);
        yaml.set("gui.open.pitch-variation", 0.0);
        File file = new File(folder.toFile(), "sounds.yml");
        yaml.save(file);
        PublicPresentation presentation = new PublicPresentation(folder.toFile());
        List<Object[]> calls = new ArrayList<>();
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getLocation")) return new Location(null, 0, 0, 0);
                    if (method.getName().equals("playSound")) calls.add(args);
                    return defaultValue(method.getReturnType());
                });
        assertTrue(presentation.play(player, "gui.open"));
        assertEquals(1, calls.size());
        assertEquals("minecraft:block.note_block.pling", calls.getFirst()[1]);
        assertEquals(SoundCategory.PLAYERS, calls.getFirst()[2]);
        assertEquals(0.37f, (float) calls.getFirst()[3], 0.001f);
        assertEquals(1.25f, (float) calls.getFirst()[4], 0.001f);
        yaml.set("gui.open.enabled", false);
        yaml.save(file);
        presentation.reload();
        assertFalse(presentation.play(player, "gui.open"));
        yaml.set("gui.open", null);
        yaml.save(file);
        presentation.reload();
        assertFalse(presentation.play(player, "gui.open"));
        assertEquals(1, calls.size());
    }

    @Test
    void invalidSoundIsSuppressedWithoutRewritingOwnerFile() throws Exception {
        File file = new File(folder.toFile(), "sounds.yml");
        String owner = "gui.open:\n  enabled: true\n  sound: minecraft:block.note_block.pling\n"
                + "  category: INVALID\n  volume: 0.5\n  pitch: 1.0\n";
        java.nio.file.Files.writeString(file.toPath(), owner);
        PublicPresentation presentation = new PublicPresentation(folder.toFile());
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        assertFalse(presentation.play(player, "gui.open"));
        assertEquals(owner, java.nio.file.Files.readString(file.toPath()));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }
}
