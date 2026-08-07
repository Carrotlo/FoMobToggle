package me.foesio.foMobToggle.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.foesio.core.FoCoreContext;
import me.foesio.core.storage.WriteBehindStore;
import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.model.ToggleCategory;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PlayerSettingsManager implements AutoCloseable {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS player_settings (
                player_uuid TEXT PRIMARY KEY,
                mob_enabled INTEGER NOT NULL DEFAULT 1,
                monster_enabled INTEGER NOT NULL DEFAULT 1,
                passive_enabled INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """;
    private static final String UPSERT = """
            INSERT INTO player_settings (
                player_uuid, mob_enabled, monster_enabled, passive_enabled, updated_at
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                mob_enabled = excluded.mob_enabled,
                monster_enabled = excluded.monster_enabled,
                passive_enabled = excluded.passive_enabled,
                updated_at = excluded.updated_at
            """;
    private static final String INSERT_LEGACY = """
            INSERT OR IGNORE INTO player_settings (
                player_uuid, mob_enabled, monster_enabled, passive_enabled, updated_at
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private final FoMobToggle plugin;
    private final File legacyUserDataFolder;
    private final File databaseFile;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();
    private final Connection connection;
    private final WriteBehindStore<UUID, SettingsSnapshot> writes;
    private boolean closed;

    public PlayerSettingsManager(FoMobToggle plugin, File legacyUserDataFolder, FoCoreContext core) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.legacyUserDataFolder = Objects.requireNonNull(legacyUserDataFolder, "legacyUserDataFolder");
        this.databaseFile = new File(plugin.getDataFolder(), "userdata.db");
        this.connection = openDatabase();
        try {
            initializeSchema();
            migrateLegacyYaml();
        } catch (SQLException exception) {
            closeConnectionQuietly();
            throw new IllegalStateException("Could not initialize userdata.db", exception);
        }
        this.writes = core.writeBehindStore(10L, this::snapshot, this::writeSnapshot);
    }

    public synchronized PlayerSettings get(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return cache.computeIfAbsent(uuid, this::load);
    }

    public synchronized void set(UUID uuid, ToggleCategory category, boolean enabled) {
        PlayerSettings settings = get(uuid);
        settings.setEnabled(category, enabled);
        writes.markDirty(uuid);
    }

    public synchronized void save(UUID uuid) {
        if (cache.containsKey(uuid)) {
            writes.snapshotAndWriteAsync(uuid, false);
        }
    }

    public synchronized void unload(UUID uuid) {
        if (!cache.containsKey(uuid)) {
            return;
        }
        writes.flushSynchronously(java.util.List.of(uuid), true);
        cache.remove(uuid);
    }

    public synchronized void saveAll() {
        writes.flushSynchronously(new ArrayList<>(cache.keySet()), false);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        saveAll();
        closed = true;
        closeConnectionQuietly();
    }

    private Connection openDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection opened = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            try (Statement statement = opened.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA journal_mode = WAL");
            }
            return opened;
        } catch (ClassNotFoundException | SQLException exception) {
            throw new IllegalStateException("Could not open " + databaseFile.getName(), exception);
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE);
        }
    }

    private void migrateLegacyYaml() throws SQLException {
        File[] files = legacyUserDataFolder.listFiles((directory, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }

        int migrated = 0;
        for (File file : files) {
            UUID uuid = parseUuid(file);
            if (uuid == null) {
                plugin.getLogger().warning("Skipping legacy userdata file with invalid UUID name: " + file.getName());
                continue;
            }
            PlayerSettings settings = readLegacyYaml(file);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_LEGACY)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, settings.isEnabled(ToggleCategory.ALL_MOBS) ? 1 : 0);
                statement.setInt(3, settings.isEnabled(ToggleCategory.MONSTERS) ? 1 : 0);
                statement.setInt(4, settings.isEnabled(ToggleCategory.PASSIVE) ? 1 : 0);
                statement.setLong(5, file.lastModified());
                migrated += statement.executeUpdate();
            }
        }
        if (migrated > 0) {
            plugin.getLogger().info("Migrated " + migrated + " legacy player setting file(s) into " + databaseFile.getName() + ".");
        }
    }

    private PlayerSettings load(UUID uuid) {
        PlayerSettings settings = new PlayerSettings();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT mob_enabled, monster_enabled, passive_enabled FROM player_settings WHERE player_uuid = ?"
        )) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    settings.setEnabled(ToggleCategory.ALL_MOBS, result.getInt("mob_enabled") != 0);
                    settings.setEnabled(ToggleCategory.MONSTERS, result.getInt("monster_enabled") != 0);
                    settings.setEnabled(ToggleCategory.PASSIVE, result.getInt("passive_enabled") != 0);
                }
            }
        } catch (SQLException exception) {
            logDatabaseFailure("read settings for " + uuid, exception);
        }
        return settings;
    }

    private synchronized SettingsSnapshot snapshot(UUID uuid, boolean unload) {
        PlayerSettings settings = cache.get(uuid);
        if (settings == null) {
            return null;
        }
        return new SettingsSnapshot(
                settings.isEnabled(ToggleCategory.ALL_MOBS),
                settings.isEnabled(ToggleCategory.MONSTERS),
                settings.isEnabled(ToggleCategory.PASSIVE)
        );
    }

    private synchronized boolean writeSnapshot(UUID uuid, SettingsSnapshot snapshot) {
        if (closed) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, snapshot.mobEnabled() ? 1 : 0);
            statement.setInt(3, snapshot.monsterEnabled() ? 1 : 0);
            statement.setInt(4, snapshot.passiveEnabled() ? 1 : 0);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            logDatabaseFailure("write settings for " + uuid, exception);
            return false;
        }
    }

    private PlayerSettings readLegacyYaml(File file) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        PlayerSettings settings = new PlayerSettings();
        for (ToggleCategory category : ToggleCategory.values()) {
            settings.setEnabled(category, configuration.getBoolean("spawn." + category.getConfigKey(), true));
        }
        return settings;
    }

    private static UUID parseUuid(File file) {
        String name = file.getName();
        String uuidText = name.substring(0, name.length() - 4);
        try {
            return UUID.fromString(uuidText);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void logDatabaseFailure(String operation, SQLException exception) {
        plugin.getLogger().warning("Could not " + operation + " in userdata.db: " + exception.getMessage());
    }

    private void closeConnectionQuietly() {
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not close userdata.db: " + exception.getMessage());
        }
    }

    private record SettingsSnapshot(boolean mobEnabled, boolean monsterEnabled, boolean passiveEnabled) {
    }
}
