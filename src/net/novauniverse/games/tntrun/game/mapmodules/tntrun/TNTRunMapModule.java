package net.novauniverse.games.tntrun.game.mapmodules.tntrun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONArray;
import org.json.JSONObject;

import net.novauniverse.games.tntrun.NovaTNTRun;
import net.novauniverse.games.tntrun.game.mapmodules.tntrun.material.ColoredDecayMaterial;
import net.novauniverse.games.tntrun.game.mapmodules.tntrun.material.IDecayMaterial;
import net.novauniverse.games.tntrun.game.mapmodules.tntrun.material.NormalDecayMaterial;
import net.zeeraa.novacore.spigot.abstraction.enums.ColoredBlockType;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.Game;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.GameManager;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.map.mapmodule.MapModule;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.DelayedGameTrigger;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.GameTrigger;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.RepeatingGameTrigger;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.TriggerCallback;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.TriggerFlag;
import net.zeeraa.novacore.spigot.language.LanguageManager;
import net.zeeraa.novacore.spigot.utils.LocationUtils;
import net.zeeraa.novacore.spigot.utils.VectorArea;

public class TNTRunMapModule extends MapModule {
	private int ticksBetweenDecay;
	private int ticksBetweenCheck;
	private int beginAfter;

	private List<VectorArea> floors;
	private List<IDecayMaterial> decaySteps;
	private Map<Location, DecayData> decayingBlocks;

	private DelayedGameTrigger beginTrigger;

	private RepeatingGameTrigger decayTrigger;
	private RepeatingGameTrigger checkTrigger;

	public TNTRunMapModule(JSONObject json) {
		super(json);

		this.floors = new ArrayList<>();
		this.decaySteps = new ArrayList<>();
		this.decayingBlocks = new HashMap<>();

		ticksBetweenDecay = json.getInt("ticks_between_decay");
		ticksBetweenCheck = json.getInt("ticks_between_check");
		beginAfter = json.getInt("begin_after");

		JSONArray floorsJson = json.getJSONArray("floors");
		for (int i = 0; i < floorsJson.length(); i++) {
			JSONObject floor = floorsJson.getJSONObject(i);

			VectorArea area = new VectorArea(floor.getInt("x1"), floor.getInt("y1"), floor.getInt("z1"), floor.getInt("x2"), floor.getInt("y2"), floor.getInt("z2"));

			floors.add(area);
		}

		JSONArray decayStepsJson = json.getJSONArray("dacay_steps");
		for (int i = 0; i < decayStepsJson.length(); i++) {
			String material = decayStepsJson.getString(i);
			if (material.startsWith("COLOREDBLOCK:")) {
				String[] data = material.split(":");
				ColoredBlockType type = ColoredBlockType.valueOf(data[1]);
				DyeColor color = DyeColor.valueOf(data[2]);

				decaySteps.add(new ColoredDecayMaterial(color, type));
			} else {
				decaySteps.add(new NormalDecayMaterial(Material.valueOf(material)));
			}
		}

		decayTrigger = new RepeatingGameTrigger("novauniverse.tntrun.decay", ticksBetweenDecay, ticksBetweenDecay, new TriggerCallback() {
			@Override
			public void run(GameTrigger trigger, TriggerFlag reason) {
				decay();
			}
		});
		decayTrigger.addFlag(TriggerFlag.STOP_ON_GAME_END);
		decayTrigger.addFlag(TriggerFlag.DISABLE_LOGGING);

		checkTrigger = new RepeatingGameTrigger("novauniverse.tntrun.check", ticksBetweenCheck, ticksBetweenCheck, new TriggerCallback() {
			@Override
			public void run(GameTrigger triggerr, TriggerFlag reason) {
				check();
			}
		});
		checkTrigger.addFlag(TriggerFlag.STOP_ON_GAME_END);
		checkTrigger.addFlag(TriggerFlag.DISABLE_LOGGING);

		beginTrigger = new DelayedGameTrigger("novauniverse.tntrun.start_decay", beginAfter * 20L, new TriggerCallback() {
			@Override
			public void run(GameTrigger trigger2, TriggerFlag reason) {
				decayTrigger.start();
				checkTrigger.start();
			}
		});

		beginTrigger.addFlag(TriggerFlag.STOP_ON_GAME_END);
		beginTrigger.addFlag(TriggerFlag.RUN_ONLY_ONCE);
	}

	private final double RELATIVE_BLOCK_THRESHOLD = 0.321;
	private final double RELATIVE_BLOCK_THRESHOLD_MIN = RELATIVE_BLOCK_THRESHOLD;
	private final double RELATIVE_BLOCK_THRESHOLD_MAX = 1 - RELATIVE_BLOCK_THRESHOLD; // 729

	public Location getRelativeLocationToDecay(Location location) {
		double xDecimal = location.getX() - location.getBlockX();

		if (xDecimal <= RELATIVE_BLOCK_THRESHOLD_MIN) {
			return location.clone().add(-1, 0, 0);
		}

		if (xDecimal >= RELATIVE_BLOCK_THRESHOLD_MAX) {
			return location.clone().add(1, 0, 0);
		}

		double zDecimal = location.getZ() - location.getBlockZ();

		if (zDecimal <= RELATIVE_BLOCK_THRESHOLD_MIN) {
			return location.clone().add(0, 0, -1);
		}

		if (zDecimal >= RELATIVE_BLOCK_THRESHOLD_MAX) {
			return location.clone().add(0, 0, 1);
		}

		return null;
	}

	public void check() {
		World world = GameManager.getInstance().getActiveGame().getWorld();
		Bukkit.getServer().getOnlinePlayers().forEach(player -> {
			if (player.getWorld() != world) {
				return;
			}

			if (player.getGameMode() == GameMode.SPECTATOR) {
				return;
			}

			Location floor = player.getLocation().clone().add(0, -1, 0);
			
			if(NovaTNTRun.getInstance().isAggressiveDecay()) {
				if (!this.isFloor(floor)) { floor = floor.add(0, -1, 0); }
			} else {
				if (player.getLocation().getY() - player.getLocation().getBlockY() > 0.2) {
					return;
				}
			}

			if (NovaTNTRun.getInstance().getGame().isStandingStill(player)) {
				// Big decay if player is not moving
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						this.tryDecay(floor.clone().add(x, 0, z));
					}
				}
				return;
			}

			this.tryDecay(floor);
			Location nextBlock = this.getRelativeLocationToDecay(floor);
			if (nextBlock != null) {
				this.tryDecay(nextBlock);
			}
		});
	}

	public void tryDecay(Location location) {
		if (location.getBlock().getType() != Material.AIR) {
			if (this.isFloor(location)) {
				for (Location alreadyDecaying : decayingBlocks.keySet()) {
					if (LocationUtils.isSameVector(alreadyDecaying, location)) {
						return;
					}
				}

				decayingBlocks.put(location, new DecayData());
			}
		}
	}

	public boolean isFloor(Location location) {
		for (VectorArea floor : floors) {
			if (floor.isInsideBlock(location.toVector())) {
				return true;
			}
		}
		return false;
	}

	public void decay() {
		List<Location> toRemove = new ArrayList<Location>();

		final int stageSize = decaySteps.size();

		decayingBlocks.keySet().forEach(location -> {
			DecayData data = decayingBlocks.get(location);

			if (data.getStage() >= stageSize) {
				toRemove.add(location);
				location.getBlock().setType(Material.AIR);
				return;
			}

			decaySteps.get(data.getStage()).setBlock(location.getBlock());

			data.setStage(data.getStage() + 1);
		});

		toRemove.forEach(location -> decayingBlocks.remove(location));
	}

	@Override
	public void onGameStart(Game game) {
		game.addTrigger(checkTrigger);
		game.addTrigger(decayTrigger);
		game.addTrigger(beginTrigger);

		beginTrigger.start();

		new BukkitRunnable() {
			@Override
			public void run() {
				LanguageManager.broadcast("nova.tntrun.decay_starts_in", beginAfter);
			}
		}.runTaskLater(NovaTNTRun.getInstance(), 1L);
	}
}