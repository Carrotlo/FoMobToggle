package me.foesio.foMobToggle.command;

import java.util.Collections;
import java.util.List;
import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.gui.PublicPresentation;
import me.foesio.foMobToggle.gui.ToggleMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class FoMobToggleCommand implements CommandExecutor, TabCompleter {

    private final FoMobToggle plugin;
    private final ToggleMenu toggleMenu;
    private final PublicPresentation presentation;

    public FoMobToggleCommand(FoMobToggle plugin, ToggleMenu toggleMenu, PublicPresentation presentation) {
        this.plugin = plugin;
        this.toggleMenu = toggleMenu;
        this.presentation = presentation;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            presentation.send(sender, "messages.player-only");
            return true;
        }

        if (!player.hasPermission("fomobtoggle.use")) {
            presentation.send(player, "messages.no-permission");
            presentation.play(player, "gui.error");
            return true;
        }

        if (!plugin.getConfig().getBoolean("gui.enabled", true)) {
            presentation.send(player, "messages.gui-disabled");
            presentation.play(player, "gui.error");
            return true;
        }

        if (!toggleMenu.isAvailable()) {
            presentation.send(player, "messages.gui-unavailable");
            presentation.play(player, "gui.error");
            return true;
        }

        toggleMenu.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
