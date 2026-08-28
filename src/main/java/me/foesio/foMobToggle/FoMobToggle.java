package me.foesio.foMobToggle;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.command.CommandVisibilityService;
import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.config.ResourceFiles;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foMobToggle.command.FoMobToggleCommand;
import me.foesio.foMobToggle.data.PlayerSettingsManager;
import me.foesio.foMobToggle.editor.FoMobToggleEditor;
import me.foesio.foMobToggle.gui.ToggleMenu;
import me.foesio.foMobToggle.listener.MenuListener;
import me.foesio.foMobToggle.listener.PlayerDataListener;
import me.foesio.foMobToggle.listener.SpawnListener;
import me.foesio.foMobToggle.service.SpawnPolicyService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoMobToggle extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 33182;

    private PlayerSettingsManager playerSettingsManager;
    private ToggleMenu toggleMenu;
    private FoMessageService messages;
    private SpawnPolicyService spawnPolicyService;
    private FoCoreContext core;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private FoGuiSounds guiSounds;
    private UpdateNoticeService updateNotices;
    private FoReloadRegistry reloads;
    private CommandVisibilityService commandVisibility;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        NativeDialogConfigDefaults.addDefaults(this);
        saveConfig();

        File userDataFolder = new File(getDataFolder(), "userdata");

        this.core = FoPluginCore.create(this);
        this.sounds = core.createSounds();
        this.adminSounds = FoAdminSounds.create(sounds);
        this.editorSounds = FoEditorSounds.create(sounds);
        this.guiSounds = FoGuiSounds.create(sounds);
        if (core.nativeDialogs().warnOnFallback()) {
            core.warnIfNativeDialogsUnavailable();
        }
        core.metrics(BSTATS_PLUGIN_ID)
                .togglePie("gui_enabled", () -> getConfig().getBoolean("gui.enabled", true));
        boolean guiFileExisted = new File(getDataFolder(), "guis/toggle-menu.yml").isFile();
        File guiFile = ResourceFiles.saveDefault(this, "guis/toggle-menu.yml");
        migrateLegacyGuiConfig(guiFileExisted, guiFile);
        boolean messagesFileExisted = new File(getDataFolder(), "messages.yml").isFile();
        this.messages = FoMessageService.load(this, messageMigrations(messagesFileExisted));
        this.updateNotices = core.createUpdateNotices(messages, "fomobtoggle", adminSounds).start();
        this.playerSettingsManager = new PlayerSettingsManager(this, userDataFolder, core);
        this.spawnPolicyService = new SpawnPolicyService(this, playerSettingsManager);
        this.toggleMenu = new ToggleMenu(this, messages, playerSettingsManager, spawnPolicyService, guiFile);
        FoMobToggleEditor editor = new FoMobToggleEditor(this, core, messages);
        this.reloads = FoReloadRegistry.create()
                .addConfig(this)
                .addMessages(messages)
                .add("sounds", sounds::reload)
                .add("guis", toggleMenu::reloadConfig);

        FoAdminCommand.builder(this, messages)
                .updates(updateNotices)
                .reloads(reloads)
                .editor(editor::open)
                .adminSounds(adminSounds)
                .versionCommand(false)
                .addSubcommand(FoAdminSubcommand.builder("version", context -> {
                    messages.send(context.sender(), "messages.version",
                            "{prefix}{muted}Author: {theme}Carrotio{muted} | Version: {theme}{version}",
                            Map.of("author", "Carrotio", "version", getDescription().getVersion()));
                    updateNotices.sendVersion(context.sender());
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
        FoMobToggleCommand commandExecutor = new FoMobToggleCommand(this, toggleMenu, messages);
        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);
        this.commandVisibility = CommandVisibilityService.builder(this)
                .hideWithout("fomobtoggle.admin", "fomobtoggleadmin", "mobtoggleadmin")
                .register();

        getServer().getPluginManager().registerEvents(new MenuListener(toggleMenu), this);
        getServer().getPluginManager().registerEvents(editor, this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(playerSettingsManager), this);
        getServer().getPluginManager().registerEvents(new SpawnListener(this, spawnPolicyService), this);
    }

    @Override
    public void onDisable() {
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

    public FoGuiSounds getGuiSounds() {
        return guiSounds;
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

    private FoMessageMigrations messageMigrations(boolean messagesFileExisted) {
        if (messagesFileExisted) {
            return FoMessageMigrations.none();
        }

        FileConfiguration legacyConfig = getConfig();
        if (!legacyConfig.contains("tokens") && !legacyConfig.contains("messages")) {
            return FoMessageMigrations.none();
        }

        return FoMessageMigrations.create()
                .add(messageConfig -> {
                    copyConfigSection(legacyConfig, messageConfig, "tokens", "tokens");
                    copyConfigSection(legacyConfig, messageConfig, "messages", "messages");
                    legacyConfig.set("tokens", null);
                    legacyConfig.set("messages", null);
                    saveConfig();
                    return true;
                })
                .build();
    }

    private void migrateLegacyGuiConfig(boolean guiFileExisted, File guiFile) {
        if (guiFileExisted) {
            return;
        }

        FileConfiguration legacyConfig = getConfig();
        if (!legacyConfig.contains("gui.title")
                && !legacyConfig.contains("gui.items")
                && !legacyConfig.contains("gui.close")) {
            return;
        }

        FileConfiguration guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        copyConfigSection(legacyConfig, guiConfig, "gui.title", "title");
        copyConfigSection(legacyConfig, guiConfig, "gui.items", "items");
        try {
            guiConfig.save(guiFile);
        } catch (IOException exception) {
            getLogger().warning("Could not migrate GUI configuration: " + exception.getMessage());
            return;
        }

        legacyConfig.set("gui.title", null);
        legacyConfig.set("gui.items", null);
        legacyConfig.set("gui.close", null);
        saveConfig();
    }

    private static void copyConfigSection(
            FileConfiguration source,
            FileConfiguration target,
            String sourcePath,
            String targetPath
    ) {
        ConfigurationSection section = source.getConfigurationSection(sourcePath);
        if (section == null) {
            if (source.contains(sourcePath)) {
                target.set(targetPath, source.get(sourcePath));
            }
            return;
        }

        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                target.set(targetPath + "." + key, section.get(key));
            }
        }
    }
}
