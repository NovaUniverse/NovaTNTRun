package net.novauniverse.games.tntrun.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import net.novauniverse.games.tntrun.NovaTNTRun;
import net.novauniverse.games.tntrun.game.mapmodules.tntrun.TNTRunMapModule;
import net.zeeraa.novacore.commons.log.Log;
import net.zeeraa.novacore.commons.tasks.Task;
import net.zeeraa.novacore.spigot.NovaCore;
import net.zeeraa.novacore.spigot.abstraction.VersionIndependantUtils;
import net.zeeraa.novacore.spigot.abstraction.enums.VersionIndependantSound;
import net.zeeraa.novacore.spigot.module.modules.game.GameEndReason;
import net.zeeraa.novacore.spigot.module.modules.game.MapGame;
import net.zeeraa.novacore.spigot.module.modules.game.elimination.PlayerQuitEliminationAction;
import net.zeeraa.novacore.spigot.tasks.SimpleTask;
import net.zeeraa.novacore.spigot.utils.PlayerUtils;
import net.zeeraa.novacore.spigot.utils.RandomFireworkEffect;

public class TNTRun extends MapGame implements Listener {
	private boolean started;
	private boolean ended;

	public static final double DOUBLE_JUMP_POWER = 1.0D;
	public static final double DOUBLE_JUMP_Y = 0.75D;

	public static final int DOUBLE_JUMP_CHARGES = 3;

	private TNTRunMapModule config;

	private Task gameLoop;

	private List<UUID> doubleJumpInProgress;

	private Map<UUID, DoubleJumpCharges> doubleJumpCharges;

	private Task actionbarTask;

	public TNTRun() {
		super(NovaTNTRun.getInstance());
		this.started = false;
		this.ended = false;
		this.config = null;
		this.doubleJumpInProgress = new ArrayList<UUID>();
		this.doubleJumpCharges = new HashMap<>();

		this.actionbarTask = new SimpleTask(NovaTNTRun.getInstance(), new Runnable() {
			@Override
			public void run() {
				Bukkit.getServer().getOnlinePlayers().forEach(player -> {
					if (players.contains(player.getUniqueId())) {
						if (doubleJumpCharges.containsKey(player.getUniqueId())) {
							DoubleJumpCharges charges = doubleJumpCharges.get(player.getUniqueId());
							String message = ChatColor.GOLD + "" + ChatColor.BOLD + "Double jump charges: " + (charges.hasCharges() ? ChatColor.AQUA : ChatColor.RED) + ChatColor.BOLD + charges.getCharges();
							NovaCore.getInstance().getActionBar().sendMessage(player, message);
						}
					}
				});
			}
		}, 10L);
	}

	public TNTRunMapModule getConfig() {
		return config;
	}

	@Override
	public String getName() {
		return "tntrun";
	}

	@Override
	public String getDisplayName() {
		return "TNT Run";
	}

	@Override
	public PlayerQuitEliminationAction getPlayerQuitEliminationAction() {
		return NovaTNTRun.getInstance().isAllowReconnect() ? PlayerQuitEliminationAction.DELAYED : PlayerQuitEliminationAction.INSTANT;
	}

	@Override
	public boolean eliminatePlayerOnDeath(Player player) {
		return true;
	}

	@Override
	public boolean isPVPEnabled() {
		return true;
	}

	@Override
	public boolean autoEndGame() {
		return true;
	}

	@Override
	public boolean hasStarted() {
		return started;
	}

	@Override
	public boolean hasEnded() {
		return ended;
	}

	@Override
	public boolean isFriendlyFireAllowed() {
		return false;
	}

	@Override
	public boolean canAttack(LivingEntity attacker, LivingEntity target) {
		return true;
	}

	public void tpToSpectator(Player player) {
		NovaCore.getInstance().getVersionIndependentUtils().resetEntityMaxHealth(player);
		player.setHealth(20);
		player.setGameMode(GameMode.SPECTATOR);
		if (hasActiveMap()) {
			player.teleport(getActiveMap().getSpectatorLocation());
		}
	}

	/**
	 * Teleport a player to a provided start location
	 * 
	 * @param player   {@link Player} to teleport
	 * @param location {@link Location} to teleport the player to
	 */
	protected void tpToArena(Player player, Location location) {
		player.teleport(location.getWorld().getSpawnLocation());
		PlayerUtils.clearPlayerInventory(player);
		PlayerUtils.clearPotionEffects(player);
		PlayerUtils.resetPlayerXP(player);
		player.setHealth(player.getMaxHealth());
		player.setSaturation(20);
		player.setFoodLevel(20);
		player.setGameMode(GameMode.SURVIVAL);
		player.teleport(location);

		doubleJumpCharges.put(player.getUniqueId(), new DoubleJumpCharges(DOUBLE_JUMP_CHARGES));
		
		new BukkitRunnable() {
			@Override
			public void run() {
				player.teleport(location);
				new BukkitRunnable() {
					@Override
					public void run() {
						player.teleport(location);
						new BukkitRunnable() {
							@Override
							public void run() {
								player.teleport(location);
							}
						}.runTaskLater(NovaTNTRun.getInstance(), 10L);
					}
				}.runTaskLater(NovaTNTRun.getInstance(), 10L);
			}
		}.runTaskLater(NovaTNTRun.getInstance(), 10L);
	}

	@Override
	public void onStart() {
		if (started) {
			return;
		}
		started = true;

		world.setDifficulty(Difficulty.PEACEFUL);

		TNTRunMapModule cfg = (TNTRunMapModule) this.getActiveMap().getMapData().getMapModule(TNTRunMapModule.class);
		if (cfg == null) {
			Log.fatal("TNTRun", "The map " + this.getActiveMap().getMapData().getMapName() + " has no tntrun config map module");
			Bukkit.getServer().broadcastMessage(ChatColor.RED + "TNTRun has run into an uncorrectable error and has to be ended");
			this.endGame(GameEndReason.ERROR);
			return;
		}
		this.config = cfg;

		List<Player> toTeleport = new ArrayList<Player>();

		Bukkit.getServer().getOnlinePlayers().forEach(player -> {
			if (players.contains(player.getUniqueId())) {
				toTeleport.add(player);
			} else {
				tpToSpectator(player);
			}
		});

		Collections.shuffle(toTeleport);

		List<Location> toUse = new ArrayList<Location>();
		while (toTeleport.size() > 0) {
			if (toUse.size() == 0) {
				for (Location location : getActiveMap().getStarterLocations()) {
					toUse.add(location);
				}

				Collections.shuffle(toUse);
			}

			if (toUse.size() == 0) {
				// Could not load spawn locations. break out to prevent server from crashing
				Log.fatal("TNTRun", "The map " + this.getActiveMap().getMapData().getMapName() + " has no spawn locations. Ending game to prevent crash");
				Bukkit.getServer().broadcastMessage(ChatColor.RED + "TNTRun has run into an uncorrectable error and has to be ended");
				this.endGame(GameEndReason.ERROR);
				return;
			}

			tpToArena(toTeleport.remove(0), toUse.remove(0));
		}

		// Disable drops
		this.getActiveMap().getWorld().setGameRuleValue("doTileDrops", "false");

		gameLoop = new SimpleTask(new Runnable() {
			@Override
			public void run() {
				Bukkit.getServer().getOnlinePlayers().forEach(player -> {
					player.setFoodLevel(20);
					player.setSaturation(20);
				});
			}
		}, 20L);
		gameLoop.start();

		Task.tryStartTask(actionbarTask);

		this.sendBeginEvent();
	}

	@Override
	public void onEnd(GameEndReason reason) {
		if (ended) {
			return;
		}

		Task.tryStopTask(gameLoop);

		getActiveMap().getStarterLocations().forEach(location -> {
			Firework fw = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK);
			FireworkMeta fwm = fw.getFireworkMeta();

			fwm.setPower(2);
			fwm.addEffect(RandomFireworkEffect.randomFireworkEffect());

			fw.setFireworkMeta(fwm);
		});

		Bukkit.getServer().getOnlinePlayers().forEach(player -> {
			player.setHealth(player.getMaxHealth());
			player.setFoodLevel(20);
			PlayerUtils.clearPlayerInventory(player);
			PlayerUtils.resetPlayerXP(player);
			player.setGameMode(GameMode.SPECTATOR);
			VersionIndependantUtils.get().playSound(player, player.getLocation(), VersionIndependantSound.WITHER_DEATH, 1F, 1F);
		});

		Task.tryStopTask(actionbarTask);

		ended = true;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
		if (e.getEntity() instanceof Player) {

			if (e.getDamager().getType() == EntityType.SNOWBALL || e.getEntityType() == EntityType.EGG) {
				return;
			}
		}

		e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player) {

			if (e.getCause() == DamageCause.FALL) {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onBlockFade(BlockFadeEvent e) {
		if (hasStarted()) {
			e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent e) {
		if (hasStarted()) {
			if (e.getWhoClicked().getGameMode() != GameMode.CREATIVE) {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
		Player player = event.getPlayer();
		if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL) {
			player.setFlying(false);
			if (!doubleJumpInProgress.contains(player.getUniqueId())) {
				boolean allow = false;
				if (started) {
					if (doubleJumpCharges.containsKey(player.getUniqueId())) {
						DoubleJumpCharges charges = doubleJumpCharges.get(player.getUniqueId());
						if (charges.hasCharges()) {
							charges.decrement();
							allow = true;
						}
					}
				} else {
					allow = true;
				}

				if (allow) {
					player.setVelocity(player.getLocation().getDirection().multiply(DOUBLE_JUMP_POWER).setY(DOUBLE_JUMP_Y));
					player.playSound(player.getLocation(), Sound.GHAST_FIREBALL, 1F, 1F);
					Location pLocation = player.getLocation();
					pLocation.add(0.0, 1.5, 0.0);
					for (int i = 0; i <= 2; i++) {
						player.getWorld().playEffect(pLocation.clone().add(0, -1, 0), Effect.SMOKE, i);
					}
				}
			}
			player.setAllowFlight(false);
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL) {
			if (!player.getAllowFlight()) {
				Location loc = player.getLocation();
				Block block = loc.getBlock().getRelative(BlockFace.DOWN);
				if (block.getType() != Material.AIR && block.getType().isSolid()) {
					if (!doubleJumpInProgress.contains(player.getUniqueId())) {
						boolean allow = false;

						if (started) {
							if (doubleJumpCharges.containsKey(player.getUniqueId())) {
								DoubleJumpCharges charges = doubleJumpCharges.get(player.getUniqueId());
								if (charges.hasCharges()) {
									allow = true;
								}
							}
						} else {
							allow = true;
						}

						if (allow) {
							player.setAllowFlight(true);
						}
					}
				}
			}
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e) {
		if (doubleJumpInProgress.contains(e.getPlayer().getUniqueId())) {
			doubleJumpInProgress.remove(e.getPlayer().getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerJoin(PlayerJoinEvent e) {
		if (hasStarted()) {
			if (!players.contains(e.getPlayer().getUniqueId())) {
				tpToSpectator(e.getPlayer());
			}
		} else {
			e.getPlayer().sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "You can practise double jumping while you wait for the game to start");
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerDeath(PlayerDeathEvent e) {
		if (hasStarted()) {
			e.setKeepInventory(true);
			e.getEntity().getInventory().clear();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerRespawn(PlayerRespawnEvent e) {
		if (hasStarted()) {
			e.setRespawnLocation(getActiveMap().getSpectatorLocation());

			new BukkitRunnable() {
				@Override
				public void run() {
					if (e.getPlayer().isOnline()) {
						tpToSpectator(e.getPlayer());
					}
				}
			}.runTaskLater(NovaTNTRun.getInstance(), 1L);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerDropItem(PlayerDropItemEvent e) {
		if (hasStarted()) {
			if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent e) {
		if (hasStarted()) {
			if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent e) {
		if (hasStarted()) {
			if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
				e.setCancelled(true);
			}
		}
	}
}