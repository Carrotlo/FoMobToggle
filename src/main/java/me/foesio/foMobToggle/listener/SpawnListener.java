package me.foesio.foMobToggle.listener;

import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.service.SpawnPolicyService;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class SpawnListener implements Listener {

    private final FoMobToggle plugin;
    private final SpawnPolicyService spawnPolicyService;

    public SpawnListener(FoMobToggle plugin, SpawnPolicyService spawnPolicyService) {
        this.plugin = plugin;
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

        Location location = event.getLocation();
        double radius = plugin.getConfig().getDouble("spawn-check-radius", 128.0D);
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius, candidate -> candidate instanceof Player)) {
            Player player = (Player) entity;
            if (spawnPolicyService.shouldBlockSpawn(player, mob)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
