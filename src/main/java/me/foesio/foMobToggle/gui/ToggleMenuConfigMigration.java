package me.foesio.foMobToggle.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.foesio.core.gui.publicgui.GuiDefinitionException;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Value-level migration from the 1.2 config.yml public GUI into its own file. */
public final class ToggleMenuConfigMigration {

    private static final Map<String, Object> LEGACY_DEFAULTS = legacyDefaults();
    private static final List<String> BUTTON_KEYS = List.of("mob", "monster", "passive");
    private static final List<String> ITEM_FIELDS = List.of("material", "name", "lore", "amount",
            "custom-model-data", "glow", "item-flags");
    private static final String PREVIOUS_MONSTER_DEFAULT_NAME =
            ":zombie_head: {monster_state_color}&lMONSTERS";

    private ToggleMenuConfigMigration() {
    }

    /** One-time default update; an owner name or removed sprite is never reconstructed. */
    public static boolean migrateOldDefaultSprite(YamlConfiguration gui, YamlConfiguration generatedDefaults) {
        String path = "buttons.monster.name";
        if (!PREVIOUS_MONSTER_DEFAULT_NAME.equals(gui.getString(path))) {
            return false;
        }
        String replacement = generatedDefaults.getString(path);
        if (replacement == null) {
            throw new IllegalArgumentException("Generated GUI has no default for " + path);
        }
        gui.set(path, replacement);
        return true;
    }

    public static boolean hasLegacyGui(FileConfiguration legacy) {
        return legacy.isSet("gui.title") || legacy.isConfigurationSection("gui.items")
                || legacy.isConfigurationSection("gui.close");
    }

    /**
     * Migrates owner values without replacing a newer customized target. Old
     * bundled 1.2 values intentionally become the current bundled style.
     */
    public static boolean migrateValues(
            FileConfiguration legacy,
            YamlConfiguration target,
            YamlConfiguration currentDefaults,
            boolean targetExisted
    ) {
        boolean changed = false;
        changed |= copyLeaf(legacy, "gui.title", target, currentDefaults, "title", targetExisted);
        changed |= copySection(legacy.getConfigurationSection("gui.items"), "gui.items",
                target, currentDefaults, "items", targetExisted);
        return changed;
    }

    public static boolean migrateVisibleLabels(
            FileConfiguration legacyMessages,
            YamlConfiguration target,
            YamlConfiguration currentDefaults,
            boolean targetExisted
    ) {
        boolean changed = false;
        changed |= copyMessageLabel(legacyMessages, "messages.state.enabled",
                target, currentDefaults, "labels.state.enabled", "{good}Enabled", targetExisted);
        changed |= copyMessageLabel(legacyMessages, "messages.state.disabled",
                target, currentDefaults, "labels.state.disabled", "{bad}Disabled", targetExisted);
        changed |= copyMessageLabel(legacyMessages, "messages.permission-mode.locked",
                target, currentDefaults, "labels.permission-mode.locked", "{bad}Permission override", targetExisted);
        changed |= copyMessageLabel(legacyMessages, "messages.permission-mode.gui",
                target, currentDefaults, "labels.permission-mode.gui", "{good}GUI toggle", targetExisted);
        return changed;
    }

    public static void removeLegacyGui(FileConfiguration legacy) {
        legacy.set("gui.title", null);
        legacy.set("gui.items", null);
        legacy.set("gui.close", null);
    }

    /** Start an old config.yml split from complete generated defaults. */
    public static void prepareLegacySplit(YamlConfiguration gui) {
        gui.set("size", gui.getInt("rows") * 9);
        gui.set("rows", null);
        for (String key : List.of("mob", "monster", "passive")) {
            String source = "buttons." + key;
            for (String leaf : gui.getConfigurationSection(source).getKeys(false)) {
                if (!leaf.equals("action")) {
                    gui.set("items." + key + "." + leaf, gui.get(source + "." + leaf));
                }
            }
        }
        gui.set("buttons", null);
        gui.set("filler.slots", "all");
        gui.set("labels.state.enabled", "{good}Enabled");
        gui.set("labels.state.disabled", "{bad}Disabled");
        gui.set("labels.permission-mode.locked", "{bad}Permission override");
        gui.set("labels.permission-mode.gui", "{good}GUI toggle");
    }

    /** The released 1.2 GUI had these optional owner fields; omission is intentional. */
    public static void preserveLegacyConfigOmissions(FileConfiguration legacy, YamlConfiguration gui) {
        for (String key : BUTTON_KEYS) {
            String oldButton = "gui.items." + key;
            String target = "items." + key;
            if (!legacy.isSet(oldButton)) {
                gui.set(target, null);
                continue;
            }
            if (!legacy.isConfigurationSection(oldButton)) {
                gui.set(target, legacy.get(oldButton));
                continue;
            }
            for (String field : List.of("name", "lore")) {
                if (!legacy.isSet(oldButton + "." + field)) {
                    gui.set(target + "." + field, null);
                }
            }
        }
    }

    /** Older owner GUI -> current generated public-GUI schema. */
    public static boolean migrateSchema4(YamlConfiguration gui, YamlConfiguration messages,
                                         YamlConfiguration guiDefaults, YamlConfiguration messageDefaults,
                                         List<String> warnings) {
        if (!isLegacySchema(gui)) {
            return false;
        }
        boolean changed = false;
        if (gui.isSet("size")) {
            Object oldSize = gui.get("size");
            if (oldSize instanceof Integer size && size >= 9 && size <= 54 && size % 9 == 0) {
                if (!gui.isSet("rows")) {
                    gui.set("rows", size / 9);
                }
            } else if (!gui.isSet("rows")) {
                gui.set("rows", guiDefaults.getInt("rows"));
                warnings.add("Replaced invalid legacy GUI size=" + oldSize
                        + " with generated default rows=" + guiDefaults.getInt("rows"));
            }
            gui.set("size", null);
            changed = true;
        }
        if (gui.isString("title")) {
            gui.set("title", convertText(gui.getString("title"), null));
        }
        for (String key : BUTTON_KEYS) {
            String source = "items." + key;
            if (!gui.isConfigurationSection(source)) {
                if (gui.isSet(source)) {
                    copyGeneratedSection(gui, guiDefaults, "buttons." + key);
                    warnings.add("Replaced invalid legacy GUI " + source + "=" + gui.get(source)
                            + " with the generated default button");
                    changed = true;
                }
                continue;
            }
            for (String leaf : gui.getConfigurationSection(source).getKeys(false)) {
                Object value = gui.get(source + "." + leaf);
                if (leaf.equals("material")) {
                    value = migrateMaterial(value, guiDefaults.getString("buttons." + key + ".material"),
                            source + ".material", warnings);
                } else if (leaf.equals("custom-model-data")) {
                    value = migrateModel(value, guiDefaults.get("buttons." + key + ".custom-model-data"),
                            source + ".custom-model-data", warnings);
                } else if (leaf.equals("name") || leaf.equals("lore")) {
                    value = convertText(value, key);
                }
                if (value != null) {
                    gui.set("buttons." + key + "." + leaf, value);
                }
            }
            gui.set("buttons." + key + ".action", "toggle." + key);
            changed = true;
        }
        if (gui.isSet("items")) {
            gui.set("items", null);
            changed = true;
        }
        for (String path : List.of("state.enabled", "state.disabled",
                "permission-mode.locked", "permission-mode.gui")) {
            String source = "labels." + path;
            if (gui.isSet(source)) {
                if (messages == null) {
                    throw new IllegalArgumentException("Cannot move GUI labels: messages.yml is missing");
                }
                String target = "messages.gui-labels." + path;
                Object label = gui.get(source);
                if (!(label instanceof String)) {
                    label = messageDefaults.get(target);
                    warnings.add("Replaced invalid legacy GUI " + source + "=" + gui.get(source)
                            + " with generated default " + label);
                }
                if (!messages.isSet(target)
                        || Objects.equals(messages.get(target), messageDefaults.get(target))
                        || !(messages.get(target) instanceof String)) {
                    messages.set(target, label);
                }
            }
        }
        if (messages != null) {
            for (String path : List.of("permission-mode.locked", "permission-mode.gui")) {
                if (messages.isSet("messages.gui-labels." + path)) {
                    messages.set("messages." + path, null);
                }
            }
        }
        if (gui.isSet("labels")) {
            gui.set("labels", null);
            changed = true;
        }
        if (gui.isSet("filler.slots")) {
            Object slots = gui.get("filler.slots");
            if (!(slots instanceof String marker && marker.equalsIgnoreCase("all"))) {
                int size = gui.getInt("rows", guiDefaults.getInt("rows")) * 9;
                if (!(slots instanceof List<?> list) || list.stream().anyMatch(candidate ->
                        !(candidate instanceof Integer slot) || slot < 0 || slot >= size)
                        || new java.util.HashSet<>(list).size() != list.size()) {
                    warnings.add("Replaced invalid legacy GUI filler.slots=" + slots
                            + " with generated default all unused slots");
                } else {
                    List<Integer> visibleSlots = new java.util.ArrayList<>();
                    for (Object candidate : list) {
                        int slot = (Integer) candidate;
                        boolean occupied = false;
                        for (String key : BUTTON_KEYS) {
                            occupied |= gui.getBoolean("buttons." + key + ".enabled", false)
                                    && gui.getInt("buttons." + key + ".slot", -1) == slot;
                        }
                        if (!occupied) {
                            visibleSlots.add(slot);
                        }
                    }
                    if (gui.getBoolean("filler.enabled", false) && !visibleSlots.isEmpty()) {
                        for (String key : gui.getConfigurationSection("filler").getKeys(false)) {
                            if (!key.equals("slots") && !key.equals("enabled")) {
                                Object value = key.equals("custom-model-data")
                                        ? migrateModel(gui.get("filler." + key),
                                                guiDefaults.get("filler.custom-model-data"),
                                                "filler.custom-model-data", warnings)
                                        : gui.get("filler." + key);
                                if (key.equals("name") || key.equals("lore")) {
                                    value = convertText(value, null);
                                }
                                if (value != null) {
                                    gui.set("decorations.filler-region." + key, value);
                                }
                            }
                        }
                        gui.set("decorations.filler-region.slots", visibleSlots);
                    }
                    gui.set("filler.enabled", false);
                }
            }
            gui.set("filler.slots", null);
            changed = true;
        }
        if (gui.isSet("filler.material")) {
            gui.set("filler.material", migrateMaterial(gui.get("filler.material"),
                    guiDefaults.getString("filler.material"), "filler.material", warnings));
        }
        if (gui.isSet("decorations.filler-region.material")) {
            gui.set("decorations.filler-region.material", migrateMaterial(
                    gui.get("decorations.filler-region.material"), guiDefaults.getString("filler.material"),
                    "decorations.filler-region.material", warnings));
        }
        if (gui.isSet("filler.custom-model-data")) {
            gui.set("filler.custom-model-data", migrateModel(gui.get("filler.custom-model-data"),
                    guiDefaults.get("filler.custom-model-data"), "filler.custom-model-data", warnings));
        }
        for (String field : List.of("name", "lore")) {
            if (gui.isSet("filler." + field)) {
                gui.set("filler." + field, convertText(gui.get("filler." + field), null));
            }
        }
        return changed;
    }

    public static boolean isLegacySchema(YamlConfiguration gui) {
        return !gui.isConfigurationSection("buttons") && (gui.isSet("size")
                || gui.isConfigurationSection("items") || gui.isConfigurationSection("labels")
                || gui.isSet("filler.slots"));
    }

    /** Replace only present, invalid legacy fields diagnosed by Core's target schema. */
    public static void validateLegacyWithDefaults(YamlConfiguration gui, YamlConfiguration defaults,
                                                   List<String> warnings) throws GuiDefinitionException {
        for (int attempt = 0; attempt < 64; attempt++) {
            try {
                ToggleMenu.validateMigrated(gui);
                return;
            } catch (GuiDefinitionException failure) {
                String path = failure.key().replaceFirst("\\[\\d+].*$", "");
                if (path.equals("decorations.filler-region.slots")
                        && gui.isConfigurationSection("decorations.filler-region")) {
                    gui.set("decorations.filler-region", null);
                    gui.set("filler.enabled", defaults.get("filler.enabled"));
                    warnings.add("Replaced invalid legacy GUI decoration slots with generated filler layout");
                    continue;
                }
                if (path.endsWith(".slot") && failure.getMessage().contains("overlaps ")
                        && Objects.equals(gui.get(path), defaults.get(path))) {
                    String previous = failure.getMessage().substring(
                            failure.getMessage().indexOf("overlaps ") + "overlaps ".length());
                    if (recoverableLegacyPath(previous) && gui.isSet(previous)
                            && !Objects.equals(gui.get(previous), defaults.get(previous))) {
                        path = previous;
                    }
                }
                String fallbackPath = path;
                if (path.startsWith("decorations.filler-region.")) {
                    fallbackPath = "filler." + path.substring("decorations.filler-region.".length());
                }
                boolean missingRequired = !gui.isSet(path) && requiredGeneratedPath(path);
                if (!recoverableLegacyPath(path) || (!gui.isSet(path) && !missingRequired)) {
                    throw failure;
                }
                Object original = gui.get(path);
                Object fallback = defaults.get(fallbackPath);
                if (fallback == null && missingRequired) {
                    throw failure;
                }
                if (Objects.equals(original, fallback)) {
                    throw failure;
                }
                if (fallback instanceof ConfigurationSection) {
                    copyGeneratedSection(gui, defaults, path);
                } else {
                    gui.set(path, fallback);
                }
                warnings.add("Replaced invalid legacy GUI " + path + "=" + original
                        + " (" + failure.getMessage() + ")"
                        + " with generated default " + fallback);
            }
        }
        throw new GuiDefinitionException("guis/toggle-menu.yml", "too many invalid legacy GUI fields");
    }

    /** Assemble the final old-schema upgrade from the newest generated GUI, not old YAML sections. */
    public static YamlConfiguration stageGeneratedGui(YamlConfiguration old, YamlConfiguration migrated,
                                                      YamlConfiguration defaults)
            throws org.bukkit.configuration.InvalidConfigurationException {
        return stageGeneratedGui(old, migrated, defaults, 3);
    }

    public static YamlConfiguration stageGeneratedGui(YamlConfiguration old, YamlConfiguration migrated,
                                                      YamlConfiguration defaults, int sourceVersion)
            throws org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration staged = new YamlConfiguration();
        staged.loadFromString(defaults.saveToString());
        for (String path : List.of("title", "rows")) {
            if (migrated.isSet(path)) {
                staged.set(path, migrated.get(path));
            }
        }
        for (String field : List.of("enabled", "material", "name", "lore", "amount",
                "custom-model-data", "glow", "item-flags")) {
            String path = "filler." + field;
            if (migrated.isSet(path)) {
                staged.set(path, migrated.get(path));
            } else if ((field.equals("name") || field.equals("lore")
                    || sourceVersion >= 3 && (field.equals("amount") || field.equals("item-flags")))
                    && old.isConfigurationSection("filler") && !old.isSet(path)) {
                staged.set(path, null);
            }
        }
        for (String key : BUTTON_KEYS) {
            String oldButton = "items." + key;
            String button = "buttons." + key;
            if (!old.isSet(oldButton)) {
                staged.set(button, null);
                continue;
            }
            for (String field : List.of("enabled", "slot", "material", "name", "lore", "amount",
                    "custom-model-data", "glow", "item-flags")) {
                String path = button + "." + field;
                if (migrated.isSet(path)) {
                    staged.set(path, migrated.get(path));
                } else if ((field.equals("name") || field.equals("lore")
                        || sourceVersion >= 3 && (field.equals("amount") || field.equals("item-flags")))
                        && old.isConfigurationSection(oldButton) && !old.isSet(oldButton + "." + field)) {
                    staged.set(path, null);
                }
            }
        }
        if (migrated.isConfigurationSection("decorations.filler-region")) {
            for (String field : List.of("slots", "material", "name", "lore", "amount",
                    "custom-model-data", "glow", "item-flags")) {
                String path = "decorations.filler-region." + field;
                if (migrated.isSet(path)) {
                    staged.set(path, migrated.get(path));
                }
            }
        }
        return staged;
    }

    private static boolean recoverableLegacyPath(String path) {
        if (path.equals("title") || path.equals("rows")) {
            return true;
        }
        if (path.equals("filler")) {
            return true;
        }
        if (path.startsWith("filler.")) {
            return ITEM_FIELDS.contains(path.substring("filler.".length()))
                    || path.equals("filler.enabled");
        }
        if (path.startsWith("decorations.filler-region.")) {
            return ITEM_FIELDS.contains(path.substring("decorations.filler-region.".length()));
        }
        for (String key : BUTTON_KEYS) {
            String prefix = "buttons." + key + ".";
            if (path.equals("buttons." + key)) {
                return true;
            }
            if (path.startsWith(prefix)) {
                String leaf = path.substring(prefix.length());
                return ITEM_FIELDS.contains(leaf) || leaf.equals("slot")
                        || leaf.equals("enabled") || leaf.equals("action");
            }
        }
        return false;
    }

    private static boolean requiredGeneratedPath(String path) {
        if (path.equals("title") || path.equals("rows") || path.equals("filler")
                || path.equals("filler.enabled") || path.equals("filler.material")) {
            return true;
        }
        for (String key : BUTTON_KEYS) {
            if (path.equals("buttons." + key + ".slot")
                    || path.equals("buttons." + key + ".enabled")
                    || path.equals("buttons." + key + ".material")
                    || path.equals("buttons." + key + ".action")) {
                return true;
            }
        }
        return false;
    }

    private static void copyGeneratedSection(YamlConfiguration gui, YamlConfiguration defaults, String path) {
        ConfigurationSection section = defaults.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("No generated GUI section default for " + path);
        }
        gui.set(path, null);
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                gui.set(path + "." + key, section.get(key));
            }
        }
    }

    private static Object migrateModel(Object value, Object fallback, String path, List<String> warnings) {
        try {
            return modelValue(value);
        } catch (IllegalArgumentException exception) {
            warnings.add("Replaced invalid legacy GUI " + path + "=" + value
                    + " with generated default " + fallback);
            return fallback;
        }
    }

    private static String migrateMaterial(Object value, String fallback, String path, List<String> warnings) {
        if (value instanceof String name && validItemMaterial(name)) {
            return name;
        }
        if (fallback == null || !validItemMaterial(fallback)) {
            throw new IllegalArgumentException("No valid generated material default for " + path);
        }
        warnings.add("Replaced invalid legacy GUI material " + path + "=" + value
                + " with generated default " + fallback);
        return fallback;
    }

    private static boolean validItemMaterial(String name) {
        Material material = Material.matchMaterial(name);
        return material != null && (Bukkit.getServer() == null || material.isItem());
    }

    private static Object convertText(Object value, String category) {
        if (value instanceof List<?> lines) {
            return lines.stream().map(line -> line instanceof String text
                    ? convertString(text, category) : line).toList();
        }
        return value instanceof String text ? convertString(text, category) : value;
    }

    private static String convertString(String text, String category) {
        String result = text;
        if (category != null) {
            for (String token : List.of("state_color", "state", "saved_state", "permission_mode")) {
                result = result.replace("{" + token + "}", "{" + category + "_" + token + "}");
            }
        }
        for (String token : List.of("theme", "muted", "white", "yellow", "good", "bad")) {
            result = result.replace("{" + token + "}", "{gui_" + token + "}");
        }
        return result;
    }

    private static Integer modelValue(Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            return null;
        }
        if (value instanceof Integer number && number >= 0) {
            return number;
        }
        if (value instanceof String text && text.matches("[0-9]+")) {
            return Integer.valueOf(text);
        }
        throw new IllegalArgumentException("Invalid legacy custom-model-data: " + value);
    }

    private static boolean copySection(
            ConfigurationSection source,
            String sourcePrefix,
            YamlConfiguration target,
            YamlConfiguration currentDefaults,
            String targetPrefix,
            boolean targetExisted
    ) {
        if (source == null) {
            return false;
        }
        boolean changed = false;
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key)) {
                continue;
            }
            changed |= copyValue(source.get(key), sourcePrefix + "." + key,
                    target, currentDefaults, targetPrefix + "." + key, targetExisted);
        }
        return changed;
    }

    private static boolean copyLeaf(
            FileConfiguration source,
            String sourcePath,
            YamlConfiguration target,
            YamlConfiguration currentDefaults,
            String targetPath,
            boolean targetExisted
    ) {
        if (!source.isSet(sourcePath)) {
            return false;
        }
        return copyValue(source.get(sourcePath), sourcePath, target, currentDefaults, targetPath, targetExisted);
    }

    private static boolean copyValue(
            Object sourceValue,
            String sourcePath,
            YamlConfiguration target,
            YamlConfiguration currentDefaults,
            String targetPath,
            boolean targetExisted
    ) {
        if (Objects.deepEquals(sourceValue, LEGACY_DEFAULTS.get(sourcePath))) {
            return false;
        }
        String defaultPath = targetPath.startsWith("items.")
                ? "buttons." + targetPath.substring("items.".length()) : targetPath;
        Object bundledDefault = currentDefaults.get(defaultPath);
        if (bundledDefault == null && targetPath.endsWith(".custom-model-data")) {
            bundledDefault = "";
        }
        String category = targetPath.startsWith("items.")
                ? targetPath.substring("items.".length()).split("\\.")[0] : null;
        if (targetExisted && (!target.isSet(targetPath)
                || !Objects.deepEquals(convertText(target.get(targetPath), category), bundledDefault))) {
            return false;
        }
        if (Objects.deepEquals(target.get(targetPath), sourceValue)) {
            return false;
        }
        target.set(targetPath, sourceValue);
        return true;
    }

    private static boolean copyMessageLabel(
            FileConfiguration source,
            String sourcePath,
            YamlConfiguration target,
            YamlConfiguration currentDefaults,
            String targetPath,
            Object oldDefault,
            boolean targetExisted
    ) {
        if (!source.isSet(sourcePath) || Objects.deepEquals(source.get(sourcePath), oldDefault)) {
            return false;
        }
        if (targetExisted && (!target.isSet(targetPath)
                || !Objects.deepEquals(target.get(targetPath), oldDefault))) {
            return false;
        }
        if (Objects.deepEquals(source.get(sourcePath), target.get(targetPath))) {
            return false;
        }
        target.set(targetPath, source.get(sourcePath));
        return true;
    }

    private static Map<String, Object> legacyDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("gui.title", "&8Mob Toggle");
        defaults.put("gui.items.mob.material", "SPAWNER");
        defaults.put("gui.items.mob.name", "{state_color}All Mobs");
        defaults.put("gui.items.mob.lore", List.of(
                "{white}Master toggle for every mob type.",
                "{white}Current result: {state}",
                "{white}Saved GUI state: {saved_state}",
                "{white}Mode: {permission_mode}",
                "",
                "{white}Click to switch this setting."
        ));
        defaults.put("gui.items.monster.material", "ZOMBIE_HEAD");
        defaults.put("gui.items.monster.name", "{state_color}Monsters");
        defaults.put("gui.items.monster.lore", List.of(
                "{white}Blocks natural hostile mob spawns.",
                "{white}Current result: {state}",
                "{white}Saved GUI state: {saved_state}",
                "{white}Mode: {permission_mode}",
                "",
                "{white}Click to switch this setting."
        ));
        defaults.put("gui.items.passive.material", "WHEAT");
        defaults.put("gui.items.passive.name", "{state_color}Passive Mobs");
        defaults.put("gui.items.passive.lore", List.of(
                "{white}Blocks natural passive mob spawns.",
                "{white}Current result: {state}",
                "{white}Saved GUI state: {saved_state}",
                "{white}Mode: {permission_mode}",
                "",
                "{white}Click to switch this setting."
        ));
        return Map.copyOf(defaults);
    }
}
