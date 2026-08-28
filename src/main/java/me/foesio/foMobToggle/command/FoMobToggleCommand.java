package me.foesio.foMobToggle.command;

import java.util.Collections;
import java.util.List;
import me.foesio.core.message.FoMessageService;
import me.foesio.foMobToggle.FoMobToggle;
import me.foesio.foMobToggle.gui.ToggleMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class FoMobToggleCommand implements CommandExecutor, TabCompleter {

    private final FoMobToggle plugin;
    private final ToggleMenu toggleMenu;
    private final FoMessageService messages;

    public FoMobToggleCommand(FoMobToggle plugin, ToggleMenu toggleMenu, FoMessageService messages) {
        this.plugin = plugin;
        this.toggleMenu = toggleMenu;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "messages.player-only",
                    "{prefix}{bad}Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("fomobtoggle.use")) {
            messages.send(player, "messages.no-permission",
                    "{prefix}{bad}You do not have permission to use this.");
            plugin.getAdminSounds().updateError(sender);
            return true;
        }

        if (!plugin.getConfig().getBoolean("gui.enabled", true)) {
            messages.send(player, "messages.gui-disabled",
                    "{prefix}{muted}The GUI is disabled. {theme}Only permission nodes are active.");
            plugin.getAdminSounds().updateError(sender);
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
