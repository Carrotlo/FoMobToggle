package me.foesio.foMobToggle.listener;

import me.foesio.foMobToggle.data.PlayerSettingsManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataListener implements Listener {

    private final PlayerSettingsManager playerSettingsManager;

    public PlayerDataListener(PlayerSettingsManager playerSettingsManager) {
        this.playerSettingsManager = playerSettingsManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerSettingsManager.unload(event.getPlayer().getUniqueId());
    }
}
