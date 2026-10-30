package me.foesio.foMobToggle.service;

import java.util.Objects;
import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.api.FoMobToggleApi;
import me.foesio.foMobToggle.api.SetPreferenceResult;
import me.foesio.foMobToggle.api.SpawnDecision;
import me.foesio.foMobToggle.api.ToggleState;
import me.foesio.foMobToggle.api.event.MobTogglePreferenceChangedEvent;
import me.foesio.foMobToggle.data.PlayerSettingsManager;
import me.foesio.foMobToggle.model.ToggleCategory;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

/** Internal implementation of the public service contract. */
public final class FoMobToggleApiService implements FoMobToggleApi {

    private final FoMobToggle plugin;
    private final PlayerSettingsManager settings;
    private final SpawnPolicyService policy;
    private boolean closed;

    public FoMobToggleApiService(FoMobToggle plugin, PlayerSettingsManager settings, SpawnPolicyService policy) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public ToggleState getState(Player player, ToggleCategory category) {
        checkActive();
        requireOnline(player);
        Objects.requireNonNull(category, "category");
        return new ToggleState(
                settings.get(player.getUniqueId()).isEnabled(category),
                policy.isEffectiveCategoryEnabled(player, category),
                player.isPermissionSet(category.getPermission()));
    }

    @Override
    public SetPreferenceResult setPreference(Player player, ToggleCategory category, boolean enabled) {
        return setPreference(player, category, enabled, MobTogglePreferenceChangedEvent.Cause.API);
    }

    public SetPreferenceResult setPreferenceFromMenu(Player player, ToggleCategory category, boolean enabled) {
        return setPreference(player, category, enabled, MobTogglePreferenceChangedEvent.Cause.GUI);
    }

    private SetPreferenceResult setPreference(Player player, ToggleCategory category, boolean enabled,
                                              MobTogglePreferenceChangedEvent.Cause cause) {
        checkActive();
        requireOnline(player);
        Objects.requireNonNull(category, "category");
        if (player.isPermissionSet(category.getPermission())) {
            return SetPreferenceResult.LOCKED;
        }
        if (!settings.setIfChanged(player.getUniqueId(), category, enabled)) {
            return SetPreferenceResult.UNCHANGED;
        }
        plugin.getServer().getPluginManager().callEvent(new MobTogglePreferenceChangedEvent(
                player.getUniqueId(), category, !enabled, enabled, cause));
        return SetPreferenceResult.CHANGED;
    }

    @Override
    public SpawnDecision evaluateSpawn(Location location, EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        checkActive();
        return policy.evaluateSpawn(location, type, reason);
    }

    public void close() {
        closed = true;
    }

    private void checkActive() {
        if (closed) {
            throw new IllegalStateException("FoMobToggle API is no longer available");
        }
    }

    private static void requireOnline(Player player) {
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) {
            throw new IllegalArgumentException("Player must be online");
        }
    }
}
