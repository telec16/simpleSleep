package fr.telec.simpleSleep;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.connorlinfoot.actionbarapi.ActionBarAPI;

import fr.telec.utils.Language;

public class SimpleSleep extends JavaPlugin implements Listener {

	private static final long DAY_TICK = 0;
	private static final long NIGHT_TICK = 12010;
	
	private Language lg;
	private List<GameMode> countable;
	private Integer inBed = 0;
	private Integer total = 0;
	private Integer goal = 50;

	/*
	 * Plugin setup
	 */

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		saveDefaultConfig();
		reloadConfig();
		lg = new Language(this);

		refreshCountableGameMode();
		goal = getConfig().getInt("percentage");
	}

	@Override
	public void onDisable() {
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("update")) {
			reloadConfig();
			lg.reload();
			
			refreshCountableGameMode();
			goal = getConfig().getInt("percentage");
			
			sender.sendMessage(ChatColor.GRAY + lg.get("updated"));
			return true;
		}
		return false;
	}
	
	/*
	 * Events handlers
	 */

	@EventHandler
	public void onPlayerBedEnterEvent(PlayerBedEnterEvent evt) {
		refreshStats(evt);
		sendMessage(formatMessage(evt.getPlayer(), lg.get("enter_bed")));
        checkAndSleep();
	}
	
	@EventHandler
	public void onPlayerBedLeaveEvent(PlayerBedLeaveEvent evt) {
		//We don't want a message when the day comes up
		if(evt.getPlayer().getWorld().getTime() > NIGHT_TICK) {
			refreshStats(evt);
			sendMessage(formatMessage(evt.getPlayer(), lg.get("leave_bed")));
		}
	}
	
	@EventHandler
	public void onPlayerQuitEvent(PlayerQuitEvent evt) {
		refreshStats();
		checkAndSleep();
	}
	

	@EventHandler
	public void onPlayerTeleportEvent(PlayerTeleportEvent evt) {
		refreshStats();
		checkAndSleep();
	}

	/*
	 * Actions
	 */

	private void checkAndSleep() {
		if(total == 0 || (float) inBed / (float) total >= (goal/100.0)) {
			for(World w : Bukkit.getWorlds()){
				if(w.getEnvironment() == Environment.NORMAL) {
					w.setTime(DAY_TICK);
				}
			}
			sendMessage(lg.get("passing_night"));
		}
	}

	/*
	 * Helpers
	 */
	
	private void refreshStats() {
		refreshStats(null);
	}
	private void refreshStats(PlayerEvent evt) {
		int count = 0;
		int sleeping = 0;
		Player p = null;
		boolean enter = false;
		
		if(evt != null) {
			p = evt.getPlayer();
			enter = evt instanceof PlayerBedEnterEvent;
		}
		
		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
			if (isCountable(player)) {
				count++;
				//Special case to handle correctly events
				if(p != null && p.equals(player)) {
					if(enter) {
						sleeping++;
					}
				} else if (player.getSleepTicks() > 0) {
					sleeping++;
				}
			}
		}

		total = count;
		inBed = sleeping;
	}
	
	private void sendMessage(String msg) {
		if(getConfig().getBoolean("use_actionbar")) {
			for (Player player : Bukkit.getServer().getOnlinePlayers()) {
				if (isCountable(player)) {
					ActionBarAPI.sendActionBar(player, msg);
				}
			}
		} else {
			Bukkit.broadcastMessage(msg);
		}
	}

	private String formatMessage(Player player, String msg) {
		Integer percentage = (int) ((total == 0) ? 100 : Math.round(100.0 * (float) inBed / (float) total));

		msg = msg.replace("<player>", player.getDisplayName())
				.replace("<count>", inBed.toString())
				.replace("<total>", total.toString())
				.replace("<percentage>", percentage.toString())
		        .replace("<goal>", goal.toString());

		return msg;
	}

	private boolean isCountable(Player player) {
		return countable.contains(player.getGameMode()) &&
			   player.getWorld().getEnvironment() == Environment.NORMAL;
	}

	private void refreshCountableGameMode() {
		List<GameMode> gm = new ArrayList<GameMode>();

		for (String mode : getConfig().getStringList("count")) {
			gm.add(GameMode.valueOf(mode));
		}

		countable = gm;
	}

}
