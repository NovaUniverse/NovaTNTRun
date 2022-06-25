package net.novauniverse.games.tntrun.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

import net.novauniverse.games.tntrun.NovaTNTRun;
import net.zeeraa.novacore.spigot.command.AllowedSenders;
import net.zeeraa.novacore.spigot.command.NovaCommand;

public class StartDecayCommand extends NovaCommand {
	public StartDecayCommand() {
		super("startdecay", NovaTNTRun.getInstance());

		setAllowedSenders(AllowedSenders.ALL);
		setPermission("novauniverse.tntrun.command.startdecay");
		setPermissionDefaultValue(PermissionDefault.OP);
	}

	@Override
	public boolean execute(CommandSender sender, String commandLabel, String[] args) {
		if (!NovaTNTRun.getInstance().getGame().hasStarted()) {
			sender.sendMessage(ChatColor.RED + "Game has not started yet");
			return false;
		}

		NovaTNTRun.getInstance().getGame().tryStartDecay();
		sender.sendMessage(ChatColor.GREEN + "Trying to start countdown");

		return true;
	}
}