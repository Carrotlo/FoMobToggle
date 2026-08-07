package me.foesio.foMobToggle.listener;

import me.foesio.foMobToggle.gui.ToggleMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    private final ToggleMenu toggleMenu;

    public MenuListener(ToggleMenu toggleMenu) {
        this.toggleMenu = toggleMenu;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!toggleMenu.isMenu(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        toggleMenu.handleClick(player, event.getSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (toggleMenu.isMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }
}
