package me.foesio.foMobToggle.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Shields current owner files from FoPluginCore's unconditional default backfill. */
public final class OwnerResourceFiles {

    private OwnerResourceFiles() {
    }

    /** Keep one byte-for-byte copy of the original file across all steps of an upgrade. */
    public static File backupOnce(File file, String suffix) throws IOException {
        File backup = new File(file.getParentFile(), file.getName() + suffix);
        if (!backup.exists()) {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        }
        return backup;
    }

    public static File backupBytesOnce(File file, String suffix, byte[] original) throws IOException {
        File backup = new File(file.getParentFile(), file.getName() + suffix);
        if (original != null) {
            if (backup.exists() && !backup.isFile()) {
                throw new IOException("Migration backup path is not a regular file: " + backup);
            }
            if (!backup.exists()) {
                Files.write(backup.toPath(), original);
            }
        }
        return backup;
    }

    /** Reject unparseable owner YAML without replacing or editing its bytes. */
    public static YamlConfiguration loadStrict(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    /** Prepare complete files, retain original backups, then replace installed files. */
    public static void commitStaged(Map<File, byte[]> replacements, Map<File, byte[]> originals,
                                    String backupSuffix) throws IOException {
        Map<File, Path> staged = new LinkedHashMap<>();
        try {
            for (Map.Entry<File, byte[]> entry : replacements.entrySet()) {
                File target = entry.getKey();
                if (Arrays.equals(originals.get(target), entry.getValue())) {
                    continue;
                }
                Files.createDirectories(target.toPath().getParent());
                Path temporary = Files.createTempFile(target.toPath().getParent(), ".public-gui-", ".staged");
                Files.write(temporary, entry.getValue());
                staged.put(target, temporary);
            }
            for (File target : staged.keySet()) {
                backupBytesOnce(target, backupSuffix, originals.get(target));
            }
            for (Map.Entry<File, Path> entry : staged.entrySet()) {
                try {
                    Files.move(entry.getValue(), entry.getKey().toPath(),
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                    Files.move(entry.getValue(), entry.getKey().toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException | RuntimeException exception) {
            for (File target : staged.keySet()) {
                byte[] original = originals.get(target);
                try {
                    if (original == null) {
                        Files.deleteIfExists(target.toPath());
                    } else {
                        Files.write(target.toPath(), original);
                    }
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            throw exception;
        } finally {
            for (Path temporary : staged.values()) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static <T> T preserve(File file, boolean preserve, Supplier<T> operation) {
        if (!preserve) {
            return operation.get();
        }
        boolean existed = file.isFile();
        byte[] original;
        try {
            original = existed ? Files.readAllBytes(file.toPath()) : null;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not snapshot " + file, exception);
        }
        try {
            return operation.get();
        } finally {
            try {
                if (existed) {
                    Files.write(file.toPath(), original);
                } else {
                    Files.deleteIfExists(file.toPath());
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not preserve owner file " + file, exception);
            }
        }
    }
}
