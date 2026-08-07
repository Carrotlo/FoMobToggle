package me.foesio.foMobToggle.data;

import java.util.EnumMap;
import java.util.Map;
import me.foesio.foMobToggle.model.ToggleCategory;

public final class PlayerSettings {

    private final EnumMap<ToggleCategory, Boolean> values = new EnumMap<>(ToggleCategory.class);

    public PlayerSettings() {
        for (ToggleCategory category : ToggleCategory.values()) {
            values.put(category, true);
        }
    }

    public boolean isEnabled(ToggleCategory category) {
        return values.getOrDefault(category, true);
    }

    public void setEnabled(ToggleCategory category, boolean enabled) {
        values.put(category, enabled);
    }

    public Map<ToggleCategory, Boolean> asMap() {
        return values;
    }
}
