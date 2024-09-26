package net.novauniverse.games.tntrun;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import net.novauniverse.games.tntrun.game.misc.DoubleJumpCharges;
import net.zeeraa.novacore.spigot.module.modules.cooldown.CooldownManager;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONException;
import org.json.JSONObject;

import net.novauniverse.games.tntrun.commands.AggressiveDecayCommand;
import net.novauniverse.games.tntrun.commands.StartDecayCommand;
import net.novauniverse.games.tntrun.game.TNTRun;
import net.novauniverse.games.tntrun.game.mapmodules.tntrun.TNTRunMapModule;
import net.novauniverse.games.tntrun.game.mapmodules.tntrun.giveprojectiles.TNTRunGiveProjectiles;
import net.novauniverse.games.tntrun.modules.snowballvote.SnowballVoteSelectorItem;
import net.novauniverse.games.tntrun.modules.snowballvote.TNTRunSnowballVoteManager;
import net.zeeraa.novacore.commons.log.Log;
import net.zeeraa.novacore.commons.utils.JSONFileUtils;
import net.zeeraa.novacore.spigot.abstraction.events.VersionIndependentPlayerAchievementAwardedEvent;
import net.zeeraa.novacore.spigot.command.CommandRegistry;
import net.zeeraa.novacore.spigot.gameengine.NovaCoreGameEngine;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.GameManager;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.map.mapmodule.MapModuleManager;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.mapselector.selectors.guivoteselector.GUIMapVote;
import net.zeeraa.novacore.spigot.gameengine.module.modules.gamelobby.GameLobby;
import net.zeeraa.novacore.spigot.language.LanguageReader;
import net.zeeraa.novacore.spigot.module.ModuleManager;
import net.zeeraa.novacore.spigot.module.modules.customitems.CustomItemManager;
import net.zeeraa.novacore.spigot.module.modules.gui.GUIManager;

public class NovaTNTRun extends JavaPlugin implements Listener {
	private static NovaTNTRun instance;

	private boolean aggressiveDecay;
	private boolean autoStartDecay;
	private boolean snowballVotingEnabled;
	private boolean allowReconnect;
	private int reconnectTime;
	private boolean disableDefaultEndSound;
	private boolean shouldGiveSnowballs;

	private TNTRun game;

	public boolean isAggressiveDecay() {
		return aggressiveDecay;
	}

	public void setAggressiveDecay(boolean aggressiveDecay) {
		this.aggressiveDecay = aggressiveDecay;
	}

	public static NovaTNTRun getInstance() {
		return instance;
	}

	public boolean isSnowballVotingEnabled() {
		return snowballVotingEnabled;
	}

	public boolean isAllowReconnect() {
		return allowReconnect;
	}

	public int getReconnectTime() {
		return reconnectTime;
	}

	public TNTRun getGame() {
		return game;
	}

	public boolean isAutoStartDecay() {
		return autoStartDecay;
	}

	public boolean isDisableDefaultEndSound() {
		return disableDefaultEndSound;
	}

	public void setDisableDefaultEndSound(boolean disableDefaultEndSound) {
		this.disableDefaultEndSound = disableDefaultEndSound;
	}

	public boolean isShouldGiveSnowballs() {
		return shouldGiveSnowballs;
	}

	public void doubleJump(Player player) {
			player.setVelocity(player.getLocation().getDirection().multiply(TNTRun.DOUBLE_JUMP_POWER).setY(TNTRun.DOUBLE_JUMP_Y));
			player.playSound(player.getLocation(), Sound.GHAST_FIREBALL, 1F, 1F);
			Location pLocation = player.getLocation();
			pLocation.add(0.0, 1.5, 0.0);
			for (int i = 0; i <= 2; i++) {
				player.getWorld().playEffect(pLocation.clone().add(0, -1, 0), Effect.SMOKE, i);
			}
			CooldownManager.get().set(player, TNTRun.DOUBLE_JUMP_COOLDOWN_ID, TNTRun.DOUBLE_JUMP_COOLDOWN);
	}

	@Override
	public void onEnable() {
		NovaTNTRun.instance = this;

		this.saveDefaultConfig();

		snowballVotingEnabled = false;

		this.aggressiveDecay = this.getConfig().getBoolean("aggressive_decay");
		this.autoStartDecay = this.getConfig().getBoolean("auto_start_decay");

		disableDefaultEndSound = getConfig().getBoolean("disable_default_end_sound");

		shouldGiveSnowballs = getConfig().getBoolean("give_snowballs");

		Log.info(getName(), "Loading language files...");
		try {
			LanguageReader.readFromJar(this.getClass(), "/lang/en-us.json");
		} catch (Exception e) {
			e.printStackTrace();
		}

		allowReconnect = getConfig().getBoolean("allow_reconnect");
		reconnectTime = getConfig().getInt("player_elimination_delay");

		// Create files and folders
		File mapFolder = new File(this.getDataFolder().getPath() + File.separator + "Maps");
		File worldFolder = new File(this.getDataFolder().getPath() + File.separator + "Worlds");

		if (NovaCoreGameEngine.getInstance().getRequestedGameDataDirectory() != null) {
			mapFolder = new File(NovaCoreGameEngine.getInstance().getRequestedGameDataDirectory().getAbsolutePath() + File.separator + "TNTRun" + File.separator + "Maps");
			worldFolder = new File(NovaCoreGameEngine.getInstance().getRequestedGameDataDirectory().getAbsolutePath() + File.separator + "TNTRun" + File.separator + "Worlds");
		}

		File mapOverrides = new File(this.getDataFolder().getPath() + File.separator + "map_overrides.json");
		if (mapOverrides.exists()) {
			Log.info(getName(), "Trying to read map overrides file");
			try {
				JSONObject mapFiles = JSONFileUtils.readJSONObjectFromFile(mapOverrides);

				boolean relative = mapFiles.getBoolean("relative");

				mapFolder = new File((relative ? this.getDataFolder().getPath() + File.separator : "") + mapFiles.getString("maps_folder"));
				worldFolder = new File((relative ? this.getDataFolder().getPath() + File.separator : "") + mapFiles.getString("worlds_folder"));

				Log.info(getName(), "New paths:");
				Log.info(getName(), "Map folder: " + mapFolder.getAbsolutePath());
				Log.info(getName(), "World folder: " + worldFolder.getAbsolutePath());
			} catch (JSONException | IOException e) {
				e.printStackTrace();
				Log.error(getName(), "Failed to read map overrides from file " + mapOverrides.getAbsolutePath());
			}
		}

		try {
			FileUtils.forceMkdir(getDataFolder());
			FileUtils.forceMkdir(mapFolder);
			FileUtils.forceMkdir(worldFolder);
		} catch (IOException e1) {
			e1.printStackTrace();
			Log.fatal(getName(), "Failed to setup data directory");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		// Load modules
		ModuleManager.loadModule(this, TNTRunSnowballVoteManager.class, false);

		// Enable required modules
		ModuleManager.enable(GameManager.class);
		ModuleManager.enable(GameLobby.class);
		ModuleManager.enable(GUIManager.class);
		ModuleManager.enable(CustomItemManager.class);

		// Custom items
		try {
			CustomItemManager.getInstance().addCustomItem(SnowballVoteSelectorItem.class);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}

		// Register map modules
		MapModuleManager.addMapModule("tntrun.tntrun", TNTRunMapModule.class);
		MapModuleManager.addMapModule("tntrun.add_projectiles", TNTRunGiveProjectiles.class);

		if (getConfig().getBoolean("snowball_voting")) {
			ModuleManager.enable(TNTRunSnowballVoteManager.class);
			snowballVotingEnabled = true;
		}

		// Init game and maps
		this.game = new TNTRun();

		GameManager.getInstance().loadGame(game);

		GUIMapVote mapSelector = new GUIMapVote();

		GameManager.getInstance().setMapSelector(mapSelector);

		// Register events
		Bukkit.getServer().getPluginManager().registerEvents(this, this);
		Bukkit.getServer().getPluginManager().registerEvents(mapSelector, this);

		// Read maps
		Log.info(getName(), "Scheduled loading maps from " + mapFolder.getPath());
		GameManager.getInstance().readMapsFromFolderDelayed(mapFolder, worldFolder);

		// Register commands
		CommandRegistry.registerCommand(new AggressiveDecayCommand());
		CommandRegistry.registerCommand(new StartDecayCommand());
	}

	@Override
	public void onDisable() {
		Bukkit.getScheduler().cancelTasks(this);
		HandlerList.unregisterAll((Plugin) this);
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onVersionIndependantPlayerAchievementAwarded(VersionIndependentPlayerAchievementAwardedEvent e) {
		e.setCancelled(true);
	}
}