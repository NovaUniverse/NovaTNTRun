package net.novauniverse.games.tntrun.commands;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

import net.novauniverse.games.tntrun.NovaTNTRun;
import net.zeeraa.novacore.spigot.abstraction.VersionIndependentUtils;
import net.zeeraa.novacore.spigot.abstraction.enums.VersionIndependentSound;
import net.zeeraa.novacore.spigot.command.AllowedSenders;
import net.zeeraa.novacore.spigot.command.NovaCommand;

public class AggressiveDecayCommand extends NovaCommand {
	public AggressiveDecayCommand() {
		super("aggressivedecay", NovaTNTRun.getInstance());

		setAllowedSenders(AllowedSenders.ALL);
		setPermission("novauniverse.tntrun.aggressivedecay");
		setPermissionDefaultValue(PermissionDefault.OP);
		addHelpSubCommand();
		setDescription("Enable or disable aggressive decay");

		setFilterAutocomplete(true);
	}

	@Override
	public boolean execute(CommandSender sender, String commandLabel, String[] args) {
		boolean requestedState = false;
		if (args.length == 0) {
			sender.sendMessage(ChatColor.RED + "Please provide the new state of aggressive decay, valid states are on and off");
		} else {
			switch (args[0].toLowerCase()) {
			case "on":
				requestedState = true;
				break;

			case "off":
				requestedState = false;
				break;

			default:
				sender.sendMessage(ChatColor.RED + "Please provide the new state of aggressive decay, valid states are on and off");
				return true;
			}
		}

		if (requestedState == NovaTNTRun.getInstance().isAggressiveDecay()) {
			sender.sendMessage(ChatColor.RED + "Aggressive decay is already " + (NovaTNTRun.getInstance().isAggressiveDecay() ? "on" : "off"));
		} else {
			NovaTNTRun.getInstance().setAggressiveDecay(requestedState);
			Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Decay speed changed");
			Bukkit.getServer().getOnlinePlayers().forEach(player -> VersionIndependentUtils.get().sendTitle(player, "", ChatColor.GREEN + "" + ChatColor.BOLD + "Decay speed changed", 10, 30, 10));
			Bukkit.getServer().getOnlinePlayers().forEach(player -> VersionIndependentSound.NOTE_PLING.play(player));
		}

		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
		// We can use NovaCommand#generateAliasList() since it returs a list with the
		// provided strings
		return NovaCommand.generateAliasList("on", "off");
	}
}
