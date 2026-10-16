package me.foesio.foMobToggle.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.foesio.core.sound.SoundTypes;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Repairs malformed values in older owner presentation files during the versioned upgrade. */
public final class LegacyPresentationMigration {

    private static final Map<String, String> RELEASED_1_2_PRESENTATION = Map.ofEntries(
            Map.entry("tokens.theme", "#03fc88"),
            Map.entry("tokens.muted", "#a7b8b0"),
            Map.entry("tokens.white", "#ffffff"),
            Map.entry("tokens.good", "#3ecf8e"),
            Map.entry("tokens.bad", "#ff5d73"),
            Map.entry("tokens.prefix", "{theme}FoMobToggle &8» {muted}"),
            Map.entry("messages.player-only", "{prefix}{bad}Only players can use this command."),
            Map.entry("messages.no-permission", "{prefix}{bad}You do not have permission to use this."),
            Map.entry("messages.gui-disabled", "{prefix}{muted}The GUI is disabled. {theme}Only permission nodes are active."),
            Map.entry("messages.locked-by-permission", "{prefix}{bad}This toggle is locked by your permissions."),
            Map.entry("messages.toggle-updated", "{prefix}{muted}Updated {toggle}{muted} to {state}{muted}."),
            Map.entry("messages.state.enabled", "{good}Enabled"),
            Map.entry("messages.state.disabled", "{bad}Disabled"),
            Map.entry("messages.permission-mode.locked", "{bad}Permission override"),
            Map.entry("messages.permission-mode.gui", "{good}GUI toggle"));

    private LegacyPresentationMigration() {
    }

    /** Overlay only customized, surviving fields from the released 1.2 config. */
    public static void splitReleasedConfig(YamlConfiguration oldConfig, YamlConfiguration target) {
        for (Map.Entry<String, String> entry : RELEASED_1_2_PRESENTATION.entrySet()) {
            String path = entry.getKey();
            if (!oldConfig.isSet(path)) {
                if (target.isSet(path)) {
                    target.set(path, null);
                }
            } else if (!Objects.equals(oldConfig.get(path), entry.getValue())) {
                target.set(path, oldConfig.get(path));
            }
        }
    }

    public static void repairMessages(YamlConfiguration owner, YamlConfiguration defaults,
                                      List<String> warnings) {
        repairSections(owner, defaults, warnings, "messages.yml");
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || !owner.isSet(path)) {
                continue;
            }
            Object value = owner.get(path);
            boolean multilineMessage = path.startsWith("messages.")
                    && !path.startsWith("messages.state.")
                    && !path.startsWith("messages.gui-labels.");
            boolean valid = value instanceof String || multilineMessage
                    && value instanceof List<?> lines && lines.stream().allMatch(String.class::isInstance);
            if (!valid) {
                replace(owner, defaults, path, warnings, "messages.yml");
            }
        }
    }

    public static void repairSounds(YamlConfiguration owner, YamlConfiguration defaults,
                                    List<String> warnings) {
        repairSections(owner, defaults, warnings, "sounds.yml");
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || !owner.isSet(path)) {
                continue;
            }
            Object value = owner.get(path);
            String field = path.substring(path.lastIndexOf('.') + 1);
            boolean valid = switch (field) {
                case "enabled" -> value instanceof Boolean;
                case "sound" -> validSound(value);
                case "category" -> validCategory(value);
                case "volume" -> finiteInRange(value, 0, Double.MAX_VALUE);
                case "pitch" -> finiteInRange(value, 0.5, 2);
                case "pitch-variation" -> finiteInRange(value, 0, 1);
                default -> true;
            };
            if (!valid) {
                replace(owner, defaults, path, warnings, "sounds.yml");
            }
        }
    }

    private static boolean validSound(Object value) {
        if (!(value instanceof String name) || name.isBlank()) {
            return false;
        }
        if (name.contains(":")) {
            try {
                NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(java.util.Locale.ROOT));
                return key != null && (!key.getNamespace().equals(NamespacedKey.MINECRAFT)
                        || Bukkit.getServer() == null || SoundTypes.isValid(name));
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return name.matches("[A-Z0-9_]+")
                && (Bukkit.getServer() == null || SoundTypes.isValid(name));
    }

    private static boolean validCategory(Object value) {
        if (!(value instanceof String name)) {
            return false;
        }
        try {
            SoundCategory.valueOf(name);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean finiteInRange(Object value, double minimum, double maximum) {
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                && number.doubleValue() >= minimum && number.doubleValue() <= maximum;
    }

    private static void repairSections(YamlConfiguration owner, YamlConfiguration defaults,
                                       List<String> warnings, String file) {
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) && owner.isSet(path)
                    && !owner.isConfigurationSection(path)) {
                replace(owner, defaults, path, warnings, file);
            }
        }
    }

    private static void replace(YamlConfiguration owner, YamlConfiguration defaults, String path,
                                List<String> warnings, String file) {
        Object original = owner.get(path);
        Object fallback = defaults.get(path);
        if (fallback instanceof ConfigurationSection section) {
            owner.set(path, null);
            for (String key : section.getKeys(true)) {
                if (!section.isConfigurationSection(key)) {
                    owner.set(path + "." + key, section.get(key));
                }
            }
        } else {
            owner.set(path, fallback);
        }
        if (!Objects.equals(original, owner.get(path))) {
            warnings.add("Replaced invalid legacy " + file + " " + path + "=" + original
                    + " with bundled default " + defaults.get(path));
        }
    }
}
