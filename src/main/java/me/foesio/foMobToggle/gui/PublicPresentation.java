package me.foesio.foMobToggle.gui;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.sound.SoundTypes;
import me.foesio.core.text.FoText;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Strict, server-wide public presentation; missing owner keys stay missing. */
public final class PublicPresentation {

    private final File messagesFile;
    private final File soundsFile;
    private final Logger logger;
    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration sounds = new YamlConfiguration();

    public PublicPresentation(File dataFolder) {
        this(dataFolder, Logger.getLogger(PublicPresentation.class.getName()));
    }

    public PublicPresentation(File dataFolder, Logger logger) {
        messagesFile = new File(dataFolder, "messages.yml");
        soundsFile = new File(dataFolder, "sounds.yml");
        this.logger = logger;
        try {
            reload();
        } catch (IllegalStateException exception) {
            logger.warning(exception.getMessage() + "; public messages and sounds are suppressed.");
        }
    }

    public void reload() {
        YamlConfiguration nextMessages = load(messagesFile);
        YamlConfiguration nextSounds = load(soundsFile);
        for (String path : List.of("gui.open", "gui.error", "mob-toggle.enabled", "mob-toggle.disabled")) {
            try {
                validateSound(nextSounds, path);
            } catch (IllegalStateException exception) {
                logger.warning(exception.getMessage() + "; this sound is suppressed.");
                nextSounds.set(path, null);
            }
        }
        messages = nextMessages;
        sounds = nextSounds;
    }

    private static void validateSound(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            return;
        }
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null || !section.isBoolean("enabled")) {
            throw new IllegalStateException("Invalid public sound at " + path + ": expected enabled flag");
        }
        if (!section.getBoolean("enabled")) {
            return;
        }
        String sound = section.getString("sound");
        if (sound == null || sound.isBlank() || !section.isString("category")
                || !(section.get("volume") instanceof Number) || !(section.get("pitch") instanceof Number)) {
            throw new IllegalStateException("Incomplete public sound at " + path);
        }
        try {
            SoundCategory.valueOf(section.getString("category"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid sound category at " + path, exception);
        }
        double volume = section.getDouble("volume");
        double pitch = section.getDouble("pitch");
        double variation = section.getDouble("pitch-variation", 0.0);
        if (!Double.isFinite(volume) || volume < 0 || !Double.isFinite(pitch)
                || pitch < 0.5 || pitch > 2.0 || !Double.isFinite(variation)
                || variation < 0 || variation > 1.0) {
            throw new IllegalStateException("Invalid public sound values at " + path);
        }
    }

    private static YamlConfiguration load(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.isFile()) {
            return yaml;
        }
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load public presentation from " + file, exception);
        }
        return yaml;
    }

    public String render(String path) {
        return renderTemplate(messages.getString(path), Map.of());
    }

    public String renderTemplate(String template, Map<String, String> placeholders) {
        String result = Objects.toString(template, "");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", Objects.toString(entry.getValue(), ""));
        }
        ConfigurationSection tokens = messages.getConfigurationSection("tokens");
        if (tokens != null) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String key : tokens.getKeys(false)) {
                if (tokens.isString(key)) {
                    values.put(key, tokens.getString(key));
                }
            }
            for (int pass = 0; pass < 8; pass++) {
                String before = result;
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    result = result.replace("{" + entry.getKey() + "}", entry.getValue());
                }
                if (before.equals(result)) {
                    break;
                }
            }
        }
        for (String key : List.of("prefix", "theme", "muted", "white", "good", "bad")) {
            result = result.replace("{" + key + "}", "");
        }
        return FoText.color(result);
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        List<String> lines = messages.isList(path) ? messages.getStringList(path)
                : messages.isString(path) ? List.of(messages.getString(path)) : List.of();
        for (String line : lines) {
            String rendered = renderTemplate(line, placeholders);
            if (rendered.isEmpty()) {
                continue;
            }
            if (sender instanceof Player player) {
                sendPlayer(player, rendered);
            } else {
                sender.sendMessage(DialogIcons.fallbackText(rendered));
            }
        }
    }

    private static void sendPlayer(Player player, String rendered) {
        try {
            Class<?> component = Class.forName("net.kyori.adventure.text.Component");
            player.getClass().getMethod("sendMessage", component)
                    .invoke(player, DialogIcons.render(player, rendered));
        } catch (ReflectiveOperationException | LinkageError exception) {
            player.sendMessage(DialogIcons.fallbackText(rendered));
        }
    }

    public boolean play(Player player, String path) {
        ConfigurationSection section = sounds.getConfigurationSection(path);
        if (section == null || !section.getBoolean("enabled", false)) {
            return false;
        }
        String sound = section.getString("sound");
        SoundCategory category = SoundCategory.valueOf(section.getString("category"));
        float volume = (float) section.getDouble("volume");
        float configuredPitch = (float) section.getDouble("pitch");
        double variation = section.getDouble("pitch-variation", 0.0);
        float offset = variation == 0 ? 0 : (float) ((ThreadLocalRandom.current().nextDouble() * 2 - 1) * variation);
        float pitch = Math.max(0.5f, Math.min(2.0f, configuredPitch + offset));
        String soundKey = sound.contains(":") ? sound
                : SoundTypes.resolve(sound).map(known -> known.getKey().toString()).orElse(null);
        if (soundKey == null) {
            return false;
        }
        player.playSound(player.getLocation(), soundKey, category, volume, pitch);
        return true;
    }
}
