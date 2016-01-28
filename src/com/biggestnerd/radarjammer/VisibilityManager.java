package com.biggestnerd.radarjammer;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class VisibilityManager implements Listener, Runnable{
	
	private final int minCheck;
	private final int maxCheck;
	private final double maxFov;
	
	private ConcurrentHashMap<Player, PlayerLocation> lastLocations;
	private ConcurrentHashMap<UUID, ConcurrentHashMap<Boolean, UUID>> visibilityMap;
	private ConcurrentHashMap<UUID, HashSet<UUID>> currentVisibility;
	
	private VisibilityThread visThread;
	
	public VisibilityManager(RadarJammer plugin, int minCheck, int maxCheck, double maxFov) {
		lastLocations = new ConcurrentHashMap<Player, PlayerLocation>();
		visibilityMap = new ConcurrentHashMap<UUID, ConcurrentHashMap<Boolean, UUID>>();
		currentVisibility = new ConcurrentHashMap<UUID, HashSet<UUID>>();
		this.minCheck = minCheck;
		this.maxCheck = maxCheck;
		this.maxFov = maxFov;
		Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, 0L, 2L);
		visThread = new VisibilityThread();
		visThread.start();
	}

	private void updateVisible(Player player) {
		for(Entry<Boolean, UUID> entry : visibilityMap.get(player.getUniqueId()).entrySet()) {
			boolean alreadyHidden = currentVisibility.get(player.getUniqueId()).contains(entry.getValue());
			if((player.hasPermission("jammer.bypass") || entry.getKey()) && alreadyHidden) {
				player.showPlayer(Bukkit.getPlayer(entry.getValue()));
			} else if(!alreadyHidden) {
				player.hidePlayer(Bukkit.getPlayer(entry.getValue()));
				currentVisibility.get(player.getUniqueId()).add(entry.getValue());
			}
		}
	}
	
	@Override
	public void run() {
		for(Player player : Bukkit.getOnlinePlayers()) {
			updateVisible(player);
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		lastLocations.put(player, new PlayerLocation(player.getLocation(), player.getUniqueId()));
		visibilityMap.put(player.getUniqueId(), new ConcurrentHashMap<Boolean, UUID>());
		currentVisibility.put(player.getUniqueId(), new HashSet<UUID>());
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		lastLocations.remove(player);
		visibilityMap.remove(player.getUniqueId());
		currentVisibility.remove(player.getUniqueId());
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		lastLocations.put(player, new PlayerLocation(player.getLocation(), player.getUniqueId()));
	}
	
	class VisibilityThread extends Thread {
		
		private long lastRun = 0;
		
		public void run() {
			while(true) {
				long time = System.currentTimeMillis() - lastRun;
				if(time < 100L) {
					try {
						sleep(100L - time);
					} catch (InterruptedException e) {}
				}
				for(PlayerLocation loc : lastLocations.values()) {
					for(PlayerLocation other : lastLocations.values()) {
						if(!loc.equals(other)) {
							double dist = loc.getDistance(other);
							if(dist > minCheck) {
								if(dist < maxCheck) {
									visibilityMap.get(loc.getID()).put(loc.getAngle(other) < maxFov, other.getID());
								} else {
									visibilityMap.get(loc.getID()).put(false, other.getID());
								}
							} else {
								visibilityMap.get(loc.getID()).put(true, other.getID());
							}
							visibilityMap.get(loc.getID()).put(loc.getAngle(other) < maxFov, other.getID());
						}
					}
				}
			}
		}
	}
}