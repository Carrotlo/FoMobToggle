package me.foesio.foMobToggle.listener;

import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.api.SpawnDecision;
import me.foesio.foMobToggle.service.SpawnPolicyService;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class SpawnListener implements Listener {

    private final SpawnPolicyService spawnPolicyService;

    public SpawnListener(FoMobToggle plugin, SpawnPolicyService spawnPolicyService) {
        this.spawnPolicyService = spawnPolicyService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!spawnPolicyService.shouldHandle(event.getSpawnReason())) {
            return;
        }

        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (spawnPolicyService.evaluateSpawn(event.getLocation(), mob, event.getSpawnReason()) == SpawnDecision.BLOCKED) {
            event.setCancelled(true);
        }
    }
}
