package net.novauniverse.games.tntrun.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.zeeraa.novacore.spigot.module.modules.cooldown.CooldownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import net.novauniverse.games.tntrun.NovaTNTRun;
import net.novauniverse.games.tntrun.game.mapmodules.tntrun.TNTRunMapModule;
import net.novauniverse.games.tntrun.game.misc.DoubleJumpCharges;
import net.novauniverse.games.tntrun.game.misc.PlayerStandingStillCheck;
import net.zeeraa.novacore.commons.log.Log;
import net.zeeraa.novacore.commons.tasks.Task;
import net.zeeraa.novacore.spigot.NovaCore;
import net.zeeraa.novacore.spigot.abstraction.VersionIndependentUtils;
import net.zeeraa.novacore.spigot.abstraction.enums.VersionIndependentSound;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.GameEndReason;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.MapGame;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.elimination.PlayerQuitEliminationAction;
import net.zeeraa.novacore.spigot.tasks.SimpleTask;
import net.zeeraa.novacore.spigot.teams.Team;
import net.zeeraa.novacore.spigot.teams.TeamManager;
import net.zeeraa.novacore.spigot.utils.ChatColorRGBMapper;
import net.zeeraa.novacore.spigot.utils.ItemBuilder;
import net.zeeraa.novacore.spigot.utils.PlayerUtils;
import net.zeeraa.novacore.spigot.utils.RandomFireworkEffect;

public class TNTRun extends MapGame implements Listener {
	private boolean started;
	private boolean ended;

	public static final double DOUBLE_JUMP_POWER = 1.0D;
	public static final double DOUBLE_JUMP_Y = 1.25D;

	public static final int DOUBLE_JUMP_CHARGES = 5;

	public static final int DOUBLE_JUMP_COOLDOWN = 15;

	public static final String DOUBLE_JUMP_COOLDOWN_ID = "DOUBLE_JUMP_COOLDOWN";

	private TNTRunMapModule config;

	private Task moveCheckTask;

	private ItemStack doubleJumpItem;

	private Map<UUID, DoubleJumpCharges> doubleJumpCharges;

	private Map<UUID, PlayerStandingStillCheck> playerMovementCheck;

	private Task actionbarTask;

	public TNTRun() {
		super(NovaTNTRun.getInstance());
		this.started = false;
		this.ended = false;
		this.config = null;
		this.doubleJumpCharges = new HashMap<>();
		this.playerMovementCheck = new HashMap<>();
		this.doubleJumpItem = new ItemBuilder(Material.FEATHER).setName(ChatColor.GREEN + "Click to Double Jump").build();

		this.moveCheckTask = new SimpleTask(NovaTNTRun.getInstance(), () -> playerMovementCheck.values().forEach(PlayerStandingStillCheck::decrement), 1L);

		this.actionbarTask = new SimpleTask(NovaTNTRun.getInstance(), () -> Bukkit.getServer().getOnlinePlayers().forEach(player -> {
            if (players.contains(player.getUniqueId())) {
                if (doubleJumpCharges.containsKey(player.getUniqueId())) {
                    DoubleJumpCharges charges = doubleJumpCharges.get(player.getUniqueId());
                    String message = ChatColor.GOLD + "" + ChatColor.BOLD + "Double jump charges: " + (charges.hasCharges() ? ChatColor.AQUA : ChatColor.RED) + ChatColor.BOLD + charges.getCharges();
                    VersionIndependentUtils.get().sendActionBarMessage(player, message);
                }
            }
        }), 10L);
	}

	public boolean isStandingStill(Player player) {
		if (playerMovementCheck.containsKey(player.getUniqueId())) {
			return playerMovementCheck.get(player.getUniqueId()).isExpired();
		}
		return false;
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
		player.getInventory().clear();
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
		
		Color color = Color.WHITE;
		if (TeamManager.hasTeamManager()) {
			Team team = TeamManager.getTeamManager().getPlayerTeam(player);
			if (team != null) {
				color = ChatColorRGBMapper.chatColorToRGBColorData(team.getTeamColor()).toBukkitColor();
			}
		}

		player.getInventory().setChestplate(new ItemBuilder(Material.LEATHER_CHESTPLATE).setLeatherArmorColor(color).build());
		player.getInventory().setLeggings(new ItemBuilder(Material.LEATHER_LEGGINGS).setLeatherArmorColor(color).build());
		player.getInventory().setBoots(new ItemBuilder(Material.LEATHER_BOOTS).setLeatherArmorColor(color).build());

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

	public void tryStartDecay() {
		if (!started) {
			return;
		}
		TNTRunMapModule module = this.getActiveMap().getMapData().getMapModule(TNTRunMapModule.class);
		module.startDecay();
	}

	@Override
	public void onStart() {
		if (started) {
			return;
		}
		started = true;

		world.setDifficulty(Difficulty.PEACEFUL);

		TNTRunMapModule cfg = this.getActiveMap().getMapData().getMapModule(TNTRunMapModule.class);
		if (cfg == null) {
			Log.fatal("TNTRun", "The map " + this.getActiveMap().getMapData().getMapName() + " has no tntrun config map module");
			Bukkit.getServer().broadcastMessage(ChatColor.RED + "TNTRun has run into an uncorrectable error and has to be ended");
			this.endGame(GameEndReason.ERROR);
			return;
		}
		this.config = cfg;

		List<Player> toTeleport = new ArrayList<>();

		Bukkit.getServer().getOnlinePlayers().forEach(player -> {
			if (players.contains(player.getUniqueId())) {
				toTeleport.add(player);
			} else {
				tpToSpectator(player);
			}
		});

		Collections.shuffle(toTeleport, getRandom());

		List<Location> toUse = new ArrayList<>();
		while (!toTeleport.isEmpty()) {
			if (toUse.isEmpty()) {
				for (Location location : getActiveMap().getStarterLocations()) {
					toUse.add(location);
				}
				Collections.shuffle(toUse, getRandom());
			}

			if (toUse.isEmpty()) {
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

		// Peaceful Mode prevents hunger
		this.getActiveMap().getWorld().setDifficulty(Difficulty.PEACEFUL);


		Task.tryStartTask(actionbarTask);
		Task.tryStartTask(moveCheckTask);

		this.sendBeginEvent();
	}

	@Override
	public void onEnd(GameEndReason reason) {
		if (ended) {
			return;
		}

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
			if (!NovaTNTRun.getInstance().isDisableDefaultEndSound()) {
				VersionIndependentUtils.get().playSound(player, player.getLocation(), VersionIndependentSound.WITHER_DEATH, 1F, 1F);
			}
		});

		Task.tryStopTask(actionbarTask);
		Task.tryStopTask(moveCheckTask);

		ended = true;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
		if (e.getEntity() instanceof Player) {

			if (e.getDamager().getType() == EntityType.SNOWBALL || e.getDamager().getType() == EntityType.EGG) {
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
			if (!CooldownManager.get().isActive(player.getUniqueId(), DOUBLE_JUMP_COOLDOWN_ID)) {
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
					// allow double jumping in lobby
					allow = true;
				}
				if (allow) {
					NovaTNTRun.getInstance().doubleJump(player);
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
					if (!CooldownManager.get().isActive(player.getUniqueId(), DOUBLE_JUMP_COOLDOWN_ID)) {
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
		if (playerMovementCheck.containsKey(e.getPlayer().getUniqueId())) {
			this.playerMovementCheck.remove(e.getPlayer().getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerJoin(PlayerJoinEvent e) {
		playerMovementCheck.put(e.getPlayer().getUniqueId(), new PlayerStandingStillCheck());
		if (hasStarted()) {
			if (!players.contains(e.getPlayer().getUniqueId())) {
				tpToSpectator(e.getPlayer());
			} else {
				CooldownManager.get().setupPlayer(e.getPlayer());
				e.getPlayer().getInventory().setItem(0, doubleJumpItem);
			}
		} else {
			CooldownManager.get().setupPlayer(e.getPlayer());
			e.getPlayer().getInventory().setItem(0, doubleJumpItem);
			e.getPlayer().sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "You can practise double jumping while you wait for the game to start");
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerMoveMonitor(PlayerMoveEvent e) {
		if (e.getFrom().getBlockX() != e.getTo().getBlockX() || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
			if (playerMovementCheck.containsKey(e.getPlayer().getUniqueId())) {
				playerMovementCheck.get(e.getPlayer().getUniqueId()).reset();
			}
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
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onInteract(PlayerInteractEvent e) {
		if (hasStarted()) {
			if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
				if (e.getItem().isSimilar(doubleJumpItem)) {
					if (!CooldownManager.get().isActive(e.getPlayer().getUniqueId(), DOUBLE_JUMP_COOLDOWN_ID)) {
						boolean allow = false;
						if (started) {
							if (doubleJumpCharges.containsKey(e.getPlayer().getUniqueId())) {
								DoubleJumpCharges charges = doubleJumpCharges.get(e.getPlayer().getUniqueId());
								if (charges.hasCharges()) {
									charges.decrement();
									allow = true;
								}
							}
						} else {
							// allow double jumping in lobby
							allow = true;
						}
						if (allow) {
							NovaTNTRun.getInstance().doubleJump(e.getPlayer());
						}
					}
				}
			}
		}
	}
}