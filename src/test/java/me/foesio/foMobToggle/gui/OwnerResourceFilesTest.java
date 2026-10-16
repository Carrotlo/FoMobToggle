package me.foesio.foMobToggle.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OwnerResourceFilesTest {

    @TempDir Path folder;

    @Test
    void oneUpgradeKeepsOnlyTheOriginalV3BackupAcrossInternalSteps() throws Exception {
        File file = folder.resolve("toggle-menu.yml").toFile();
        byte[] original = "size: 27\nitems:\n  mob:\n    material: SPAWNER2\n"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(file.toPath(), original);

        File first = OwnerResourceFiles.backupOnce(file, ".gui-v3-backup");
        Files.writeString(file.toPath(), "rows: 3\n");
        File second = OwnerResourceFiles.backupOnce(file, ".gui-v3-backup");
        assertEquals(first, second);
        assertEquals("toggle-menu.yml.gui-v3-backup", first.getName());
        assertArrayEquals(original, Files.readAllBytes(first.toPath()));
        try (var files = Files.list(folder)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void currentOwnerDeletionSurvivesCoreStyleBackfillAndReload() throws Exception {
        File file = folder.resolve("messages.yml").toFile();
        byte[] owner = "messages:\n  toggle-updated: ''\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file.toPath(), owner);

        for (int pass = 0; pass < 2; pass++) {
            OwnerResourceFiles.preserve(file, true, () -> {
                write(file, "messages:\n  toggle-updated: English default\n  deleted: restored\n");
                return null;
            });
            assertArrayEquals(owner, Files.readAllBytes(file.toPath()));
        }
    }

    @Test
    void missingCurrentFileIsNotRecreatedButFreshInstallCanCreateIt() {
        File file = folder.resolve("sounds.yml").toFile();
        OwnerResourceFiles.preserve(file, true, () -> {
            write(file, "gui.open: default");
            return null;
        });
        assertFalse(file.exists());
        OwnerResourceFiles.preserve(file, false, () -> {
            write(file, "gui.open: default");
            return null;
        });
        assertTrue(file.isFile());
    }

    @Test
    void unparseableLegacyFileRemainsUntouchedForMigrationRetry() throws Exception {
        File file = folder.resolve("toggle-menu.yml").toFile();
        byte[] original = "items: [broken\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file.toPath(), original);

        assertThrows(InvalidConfigurationException.class, () -> OwnerResourceFiles.loadStrict(file));
        assertArrayEquals(original, Files.readAllBytes(file.toPath()));
    }

    @Test
    void stagedUpgradeKeepsOneByteExactBackupForEachChangedOwnerFile() throws Exception {
        File gui = folder.resolve("toggle-menu.yml").toFile();
        File messages = folder.resolve("messages.yml").toFile();
        byte[] oldGui = "size: 27\n".getBytes(StandardCharsets.UTF_8);
        byte[] oldMessages = "messages:\n  state: Aan\n".getBytes(StandardCharsets.UTF_8);
        Files.write(gui.toPath(), oldGui);
        Files.write(messages.toPath(), oldMessages);
        Map<File, byte[]> originals = new LinkedHashMap<>();
        originals.put(gui, oldGui);
        originals.put(messages, oldMessages);
        Map<File, byte[]> replacements = new LinkedHashMap<>();
        replacements.put(gui, "rows: 3\n".getBytes(StandardCharsets.UTF_8));
        replacements.put(messages, "messages:\n  state: Aktief\n".getBytes(StandardCharsets.UTF_8));

        OwnerResourceFiles.commitStaged(replacements, originals, ".gui-v3-backup");

        assertEquals("rows: 3\n", Files.readString(gui.toPath()));
        assertEquals("messages:\n  state: Aktief\n", Files.readString(messages.toPath()));
        assertArrayEquals(oldGui, Files.readAllBytes(folder.resolve("toggle-menu.yml.gui-v3-backup")));
        assertArrayEquals(oldMessages, Files.readAllBytes(folder.resolve("messages.yml.gui-v3-backup")));
        try (var files = Files.list(folder)) {
            assertEquals(4, files.count());
        }
    }

    @Test
    void freshDefaultInstallationUsesNormalGuiPathAndCreatesNoBackup() throws Exception {
        File gui = folder.resolve("guis/toggle-menu.yml").toFile();
        byte[] generated = Files.readAllBytes(Path.of("src/main/resources/guis/toggle-menu.yml"));
        OwnerResourceFiles.commitStaged(Map.of(gui, generated), Map.of(), ".gui-v3-backup");

        assertArrayEquals(generated, Files.readAllBytes(gui.toPath()));
        assertFalse(folder.resolve("guis/toggle-menu.yml.gui-v3-backup").toFile().exists());
        ToggleMenu.validateMigrated(OwnerResourceFiles.loadStrict(gui));
    }

    private static void write(File file, String text) {
        try {
            Files.writeString(file.toPath(), text);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
