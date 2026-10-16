package me.foesio.foMobToggle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.command.CommandVisibilityService;
import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foMobToggle.command.FoMobToggleCommand;
import me.foesio.foMobToggle.data.PlayerSettingsManager;
import me.foesio.foMobToggle.editor.FoMobToggleEditor;
import me.foesio.foMobToggle.gui.ToggleMenu;
import me.foesio.foMobToggle.gui.ToggleMenuConfigMigration;
import me.foesio.foMobToggle.gui.LegacyPresentationMigration;
import me.foesio.foMobToggle.gui.PublicPresentation;
import me.foesio.foMobToggle.gui.OwnerResourceFiles;
import me.foesio.foMobToggle.listener.PlayerDataListener;
import me.foesio.foMobToggle.listener.SpawnListener;
import me.foesio.foMobToggle.service.SpawnPolicyService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoMobToggle extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 33182;
    private static final int PUBLIC_GUI_MIGRATION_VERSION = 5;
    private static final String PUBLIC_GUI_RESOURCE = "guis/toggle-menu.yml";

    private PlayerSettingsManager playerSettingsManager;
    private ToggleMenu toggleMenu;
    private FoMessageService messages;
    private SpawnPolicyService spawnPolicyService;
    private FoCoreContext core;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private PublicPresentation presentation;
    private FoMobToggleEditor editor;
    private UpdateNoticeService updateNotices;
    private FoReloadRegistry reloads;
    private CommandVisibilityService commandVisibility;
    private Map<File, byte[]> publicMigrationOriginals;

    @Override
    public void onEnable() {
        File configFile = new File(getDataFolder(), "config.yml");
        boolean existingInstall = configFile.isFile();
        File[] priorFiles = getDataFolder().listFiles();
        boolean freshInstall = !existingInstall && (priorFiles == null || priorFiles.length == 0);
        byte[] originalConfigBytes;
        try {
            originalConfigBytes = existingInstall ? Files.readAllBytes(configFile.toPath()) : null;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not snapshot the existing configuration", exception);
        }
        saveDefaultConfig();
        NativeDialogConfigDefaults.addDefaults(this);
        saveConfig();

        File userDataFolder = new File(getDataFolder(), "userdata");

        this.core = FoPluginCore.create(this);
        File soundFile = new File(getDataFolder(), "sounds.yml");
        boolean soundFileExisted = soundFile.isFile();
        if (core.nativeDialogs().warnOnFallback()) {
            core.warnIfNativeDialogsUnavailable();
        }
        core.metrics(BSTATS_PLUGIN_ID)
                .togglePie("gui_enabled", () -> getConfig().getBoolean("gui.enabled", true));
        boolean guiFileExisted = new File(getDataFolder(), PUBLIC_GUI_RESOURCE).isFile();
        File guiFile = new File(getDataFolder(), PUBLIC_GUI_RESOURCE);
        File messagesFile = new File(getDataFolder(), "messages.yml");
        boolean messagesFileExisted = messagesFile.isFile();
        boolean publicGuiReady = true;
        if (core.migrations().version() < PUBLIC_GUI_MIGRATION_VERSION) {
            boolean advanced = core.migrations().runToVersion(PUBLIC_GUI_MIGRATION_VERSION,
                    () -> freshInstall ? installFreshPublicResources()
                            : migrateAllPublicResources(true, originalConfigBytes,
                                    guiFileExisted, messagesFileExisted, soundFileExisted));
            publicGuiReady = advanced && core.migrations().version() >= PUBLIC_GUI_MIGRATION_VERSION;
            if (!publicGuiReady) {
                restoreOriginalConfig(configFile, originalConfigBytes);
            }
        }
        boolean preserveSounds = !publicGuiReady || soundFileExisted
                || (existingInstall && core.migrations().version() >= 2);
        this.sounds = OwnerResourceFiles.preserve(soundFile, preserveSounds, core::createSounds);
        this.adminSounds = FoAdminSounds.create(sounds);
        this.editorSounds = FoEditorSounds.create(sounds);
        boolean preserveMissingMessages = !publicGuiReady || existingInstall
                && !getConfig().contains("messages") && !getConfig().contains("tokens");
        this.messages = OwnerResourceFiles.preserve(messagesFile, messagesFileExisted || preserveMissingMessages,
                () -> FoMessageService.load(this));
        if (!publicGuiReady) {
            getLogger().severe("Could not finish the public GUI migration. The menu will be unavailable; "
                    + "mob-spawn settings remain active. Correct the logged GUI error and restart to retry.");
        }
        this.presentation = new PublicPresentation(getDataFolder(), getLogger());
        this.updateNotices = core.createUpdateNotices(messages, "fomobtoggle", adminSounds).start();
        this.playerSettingsManager = new PlayerSettingsManager(this, userDataFolder, core);
        this.spawnPolicyService = new SpawnPolicyService(this, playerSettingsManager);
        this.toggleMenu = new ToggleMenu(this, presentation, playerSettingsManager,
                spawnPolicyService, guiFile, publicGuiReady);
        this.editor = new FoMobToggleEditor(this, core, messages);
        this.reloads = FoReloadRegistry.create()
                .addConfig(this)
                .add("messages", this::reloadPublicMessages)
                .add("sounds", this::reloadPublicSounds)
                .add("guis", toggleMenu::reloadConfig)
                .add("core", this::reloadCoreContext);

        FoAdminCommand.builder(this, messages)
                .updates(updateNotices)
                .reloads(reloads)
                .editor(editor::open)
                .adminSounds(adminSounds)
                .versionCommand(false)
                .addSubcommand(FoAdminSubcommand.builder("version", context -> {
                    updateNotices.checkAndSendVersion(context.sender());
                    return true;
                }).usage("version").build())
                .adminMessages(FoAdminMessages.builder()
                        .generalNoPermission("messages.no-permission", "{prefix}{bad}You do not have permission to use this.")
                        .generalPlayerOnly("messages.player-only", "{prefix}{bad}Only players can use this command.")
                        .usage("messages.admin-usage", "{prefix}{muted}Usage: {theme}/{label} {usage}")
                        .commandMissing("messages.admin-command-missing", "{prefix}{bad}Admin command is missing from plugin.yml: {theme}{command}")
                        .commandFailed("messages.admin-command-failed", "{prefix}{bad}Command failed. {muted}{error}")
                        .editorOpened("messages.editor-opened", "{prefix}{theme}Editor opened.")
                        .reloadSuccess("messages.reload-success", "{prefix}{good}Reloaded config, messages, and GUI.")
                        .reloadFailed("messages.reload-failed", "{prefix}{bad}Reload failed at {theme}{step}{bad}. {muted}{error}")
                        .build())
                .register();

        PluginCommand command = Objects.requireNonNull(getCommand("fomobtoggle"), "fomobtoggle command missing");
        FoMobToggleCommand commandExecutor = new FoMobToggleCommand(this, toggleMenu, presentation);
        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);
        this.commandVisibility = CommandVisibilityService.builder(this)
                .hideWithout("fomobtoggle.admin", "fomobtoggleadmin", "mobtoggleadmin")
                .register();

        getServer().getPluginManager().registerEvents(editor, this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(playerSettingsManager), this);
        getServer().getPluginManager().registerEvents(new SpawnListener(this, spawnPolicyService), this);
    }

    @Override
    public void onDisable() {
        if (toggleMenu != null) {
            toggleMenu.close();
            toggleMenu = null;
        }
        if (playerSettingsManager != null) {
            playerSettingsManager.close();
            playerSettingsManager = null;
        }
        if (commandVisibility != null) {
            commandVisibility.close();
            commandVisibility = null;
        }
        if (core != null) {
            core.close();
            core = null;
        }
    }

    public FoMessageService getMessageService() {
        return messages;
    }

    public FoEditorSounds getEditorSounds() {
        return editorSounds;
    }

    public FoSoundService getSounds() {
        return sounds;
    }

    public FoAdminSounds getAdminSounds() {
        return adminSounds;
    }

    public FoReloadResult reloadPluginData() {
        return reloads.reload();
    }

    /** Install complete bundled defaults when no predecessor owner source exists. */
    private boolean installFreshPublicResources() {
        File configFile = new File(getDataFolder(), "config.yml");
        File guiFile = new File(getDataFolder(), PUBLIC_GUI_RESOURCE);
        File messagesFile = new File(getDataFolder(), "messages.yml");
        File soundsFile = new File(getDataFolder(), "sounds.yml");
        Map<File, byte[]> originals = new LinkedHashMap<>();
        originals.put(configFile, null);
        originals.put(guiFile, null);
        originals.put(messagesFile, null);
        originals.put(soundsFile, null);
        publicMigrationOriginals = originals;
        try {
            ToggleMenu.validateMigrated(loadBundledGuiDefaults());
            loadBundledDefaults("messages.yml");
            loadBundledDefaults("sounds.yml");
            Map<File, byte[]> defaults = new LinkedHashMap<>();
            defaults.put(guiFile, bundledBytes(PUBLIC_GUI_RESOURCE));
            defaults.put(messagesFile, bundledBytes("messages.yml"));
            defaults.put(soundsFile, bundledBytes("sounds.yml"));
            OwnerResourceFiles.commitStaged(defaults, originals, backupSuffix(core.migrations().version()));
            return true;
        } catch (IOException | InvalidConfigurationException
                 | me.foesio.core.gui.publicgui.GuiDefinitionException | RuntimeException exception) {
            restoreMigrationFiles(originals);
            getLogger().warning("Could not install fresh public resources: " + exception.getMessage());
            return false;
        }
    }

    /** Stage every older public resource before replacing any installed owner file. */
    private boolean migrateAllPublicResources(boolean existingInstall, byte[] originalConfigBytes,
                                              boolean guiFileExisted, boolean messagesFileExisted,
                                              boolean soundFileExisted) {
        File configFile = new File(getDataFolder(), "config.yml");
        File guiFile = new File(getDataFolder(), PUBLIC_GUI_RESOURCE);
        File messagesFile = new File(getDataFolder(), "messages.yml");
        File soundsFile = new File(getDataFolder(), "sounds.yml");
        Map<File, byte[]> originals = new LinkedHashMap<>();
        Map<File, byte[]> replacements = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        try {
            originals.put(configFile, originalConfigBytes);
            originals.put(guiFile, guiFileExisted ? Files.readAllBytes(guiFile.toPath()) : null);
            originals.put(messagesFile, messagesFileExisted ? Files.readAllBytes(messagesFile.toPath()) : null);
            originals.put(soundsFile, soundFileExisted ? Files.readAllBytes(soundsFile.toPath()) : null);
            publicMigrationOriginals = originals;

            YamlConfiguration guiDefaults = loadBundledGuiDefaults();
            YamlConfiguration messageDefaults = loadBundledDefaults("messages.yml");
            YamlConfiguration soundDefaults = loadBundledDefaults("sounds.yml");
            FileConfiguration legacyConfig = getConfig();
            boolean hasLegacyGui = core.migrations().version() < 2
                    && ToggleMenuConfigMigration.hasLegacyGui(legacyConfig);
            boolean hasLegacyMessages = core.migrations().version() < 2
                    && (legacyConfig.contains("tokens") || legacyConfig.contains("messages"));

            if (!guiFileExisted && existingInstall && !hasLegacyGui) {
                throw new IOException("Installed public GUI is missing; preserving a possible owner deletion");
            }
            YamlConfiguration gui = guiFileExisted ? loadStrict(guiFile) : loadBundledGuiDefaults();
            String beforeGui = gui.saveToString();
            YamlConfiguration messagesStage = messagesFileExisted ? loadStrict(messagesFile)
                    : (!existingInstall || hasLegacyMessages) ? loadBundledDefaults("messages.yml") : null;
            YamlConfiguration soundsStage = soundFileExisted ? loadStrict(soundsFile)
                    : (!existingInstall || core.migrations().version() < 2)
                    ? loadBundledDefaults("sounds.yml") : null;
            String beforeMessages = messagesStage == null ? null : messagesStage.saveToString();
            String beforeSounds = soundsStage == null ? null : soundsStage.saveToString();
            boolean splitMessages = hasLegacyMessages && !messagesFileExisted && messagesStage != null;
            if (splitMessages) {
                YamlConfiguration oldPresentation = new YamlConfiguration();
                oldPresentation.loadFromString(legacyConfig.saveToString());
                LegacyPresentationMigration.splitReleasedConfig(oldPresentation, messagesStage);
            }
            if (hasLegacyGui && (!guiFileExisted || ToggleMenuConfigMigration.isLegacySchema(gui))) {
                if (!guiFileExisted) {
                    ToggleMenuConfigMigration.prepareLegacySplit(gui);
                    ToggleMenuConfigMigration.preserveLegacyConfigOmissions(legacyConfig, gui);
                }
                ToggleMenuConfigMigration.migrateValues(
                        legacyConfig, gui, guiDefaults, guiFileExisted);
                if (messagesStage != null) {
                    ToggleMenuConfigMigration.migrateVisibleLabels(
                            messagesStage, gui, guiDefaults, guiFileExisted);
                }
            }
            if (messagesStage != null) {
                LegacyPresentationMigration.repairMessages(messagesStage, messageDefaults, warnings);
            }
            if (soundsStage != null) {
                LegacyPresentationMigration.repairSounds(soundsStage, soundDefaults, warnings);
            }

            boolean legacySchema = ToggleMenuConfigMigration.isLegacySchema(gui);
            if (legacySchema) {
                YamlConfiguration oldGui = new YamlConfiguration();
                oldGui.loadFromString(gui.saveToString());
                ToggleMenuConfigMigration.migrateSchema4(gui, messagesStage,
                        guiDefaults, messageDefaults, warnings);
                gui = ToggleMenuConfigMigration.stageGeneratedGui(
                        oldGui, gui, guiDefaults, core.migrations().version());
                ToggleMenuConfigMigration.validateLegacyWithDefaults(gui, guiDefaults, warnings);
            } else {
                ToggleMenuConfigMigration.validateLegacyWithDefaults(gui, guiDefaults, warnings);
            }
            ToggleMenuConfigMigration.migrateOldDefaultSprite(gui, guiDefaults);
            ToggleMenu.validateMigrated(gui);

            YamlConfiguration configStage = new YamlConfiguration();
            configStage.loadFromString(legacyConfig.saveToString());
            if (hasLegacyGui) {
                ToggleMenuConfigMigration.removeLegacyGui(configStage);
            }
            if (splitMessages) {
                configStage.set("tokens", null);
                configStage.set("messages", null);
            }
            if (!guiFileExisted || !gui.saveToString().equals(beforeGui)) {
                replacements.put(guiFile, gui.saveToString().getBytes(StandardCharsets.UTF_8));
            }
            if (messagesStage != null && (!messagesFileExisted
                    || !messagesStage.saveToString().equals(beforeMessages))) {
                replacements.put(messagesFile, messagesStage.saveToString().getBytes(StandardCharsets.UTF_8));
            }
            if (soundsStage != null && (!soundFileExisted
                    || !soundsStage.saveToString().equals(beforeSounds))) {
                replacements.put(soundsFile, soundsStage.saveToString().getBytes(StandardCharsets.UTF_8));
            }
            if (hasLegacyGui || splitMessages) {
                replacements.put(configFile, configStage.saveToString().getBytes(StandardCharsets.UTF_8));
            }
            OwnerResourceFiles.commitStaged(replacements, originals, backupSuffix(core.migrations().version()));
            if (hasLegacyGui) {
                ToggleMenuConfigMigration.removeLegacyGui(legacyConfig);
            }
            if (splitMessages) {
                legacyConfig.set("tokens", null);
                legacyConfig.set("messages", null);
            }
            warnings.forEach(getLogger()::warning);
            return true;
        } catch (IOException | InvalidConfigurationException
                 | me.foesio.core.gui.publicgui.GuiDefinitionException | RuntimeException exception) {
            restoreMigrationFiles(originals);
            reloadConfig();
            getLogger().warning("Could not migrate the public GUI resources; kept the previous files: "
                    + exception.getMessage());
            return false;
        }
    }

    private void restoreOriginalConfig(File configFile, byte[] originalConfigBytes) {
        Map<File, byte[]> originals = publicMigrationOriginals;
        if (originals == null) {
            originals = new LinkedHashMap<>();
            originals.put(configFile, originalConfigBytes);
        }
        restoreMigrationFiles(originals);
        reloadConfig();
    }

    private void restoreMigrationFiles(Map<File, byte[]> originals) {
        for (Map.Entry<File, byte[]> entry : originals.entrySet()) {
            try {
                if (entry.getValue() == null) {
                    Files.deleteIfExists(entry.getKey().toPath());
                } else {
                    Files.createDirectories(entry.getKey().toPath().getParent());
                    Files.write(entry.getKey().toPath(), entry.getValue());
                }
            } catch (IOException exception) {
                getLogger().warning("Could not restore " + entry.getKey().getName()
                        + " after a failed public GUI migration: " + exception.getMessage());
            }
        }
    }

    private void reloadCoreContext() {
        FoCoreContext previous = core;
        if (previous != null) {
            previous.close();
        }
        core = FoPluginCore.create(this);
        core.metrics(BSTATS_PLUGIN_ID)
                .togglePie("gui_enabled", () -> getConfig().getBoolean("gui.enabled", true));
        core.warnIfNativeDialogsUnavailable();
        if (editor != null) {
            editor.setCore(core);
        }
    }

    private void reloadPublicMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        OwnerResourceFiles.preserve(file, true, () -> {
            messages.reload();
            return null;
        });
        presentation.reload();
    }

    private void reloadPublicSounds() {
        File file = new File(getDataFolder(), "sounds.yml");
        OwnerResourceFiles.preserve(file, true, () -> {
            sounds.reload();
            return null;
        });
        presentation.reload();
    }

    private YamlConfiguration loadBundledGuiDefaults() throws IOException, InvalidConfigurationException {
        return loadBundledDefaults(PUBLIC_GUI_RESOURCE);
    }

    private YamlConfiguration loadBundledDefaults(String resource) throws IOException, InvalidConfigurationException {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream input = getResource(resource)) {
            if (input == null) {
                throw new IOException("Bundled resource is missing: " + resource);
            }
            defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return defaults;
    }

    private byte[] bundledBytes(String resource) throws IOException {
        try (InputStream input = getResource(resource)) {
            if (input == null) {
                throw new IOException("Bundled resource is missing: " + resource);
            }
            return input.readAllBytes();
        }
    }

    private static String backupSuffix(int sourceVersion) {
        return ".gui-v" + sourceVersion + "-backup";
    }

    private static YamlConfiguration loadStrict(File file) throws IOException, InvalidConfigurationException {
        return OwnerResourceFiles.loadStrict(file);
    }

}
