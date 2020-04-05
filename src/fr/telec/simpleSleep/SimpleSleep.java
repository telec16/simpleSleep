package fr.telec.simpleSleep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.connorlinfoot.actionbarapi.ActionBarAPI;

import fr.telec.simpleCore.Language;
import fr.telec.simpleCore.SoundHelper;
import fr.telec.simpleCore.StringHandler;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class SimpleSleep extends JavaPlugin implements Listener {

	private static final int TICK_BEFORE_CHECK = 100;
	private static final long DAY_TICK = 0;
	private static final long NIGHT_TICK = 12010;
	private static final long FULL_DAY_TICK = 24000;
	private static final long MAX_WEATHER_TICK = (long) (7.5*FULL_DAY_TICK);
	private static final long MIN_WEATHER_TICK = (long) (0.5*FULL_DAY_TICK);
	
	private Language lg;
	private SoundHelper sh;
	
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
		
		sh = new SoundHelper(this);

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

			sender.sendMessage(ChatColor.GRAY + "[" + getName() + "] " + lg.get("updated"));
			return true;
		}
		return false;
	}
	
	/*
	 * Events handlers
	 */

	@EventHandler
	public void onPlayerBedEnterEvent(PlayerBedEnterEvent evt) {
		Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override
			public void run() {
				if (evt.getPlayer().getSleepTicks() > 0) {

					sh.playFromConfig(evt.getPlayer(), "sound.sleep");

					refreshStats();
					sendMessage(formatMessage(evt.getPlayer(), lg.get("enter_bed")));
					checkAndSleep();
				} else {
					sh.playFromConfig(evt.getPlayer(), "sound.cant_sleep");
				}
			}
		}, TICK_BEFORE_CHECK);
	}

	@EventHandler
	public void onPlayerBedLeaveEvent(PlayerBedLeaveEvent evt) {
		//We don't want a message when the day comes up
		if(evt.getPlayer().getWorld().getTime() > NIGHT_TICK) {
			refreshStats();
			sendMessage(formatMessage(evt.getPlayer(), lg.get("leave_bed")));
		} else {
			sh.playFromConfig(evt.getPlayer(), "sound.wakeup");
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
		if ((total == 0 && inBed > 0) || (total != 0 && (float) inBed / (float) total >= (goal / 100.0))) {
			for (World w : Bukkit.getWorlds()) {
				if (w.getEnvironment() == Environment.NORMAL) {
					w.setTime(DAY_TICK);
					w.setThundering(false);
					w.setStorm(false);
					w.setWeatherDuration(getWeatherDuration());
				}
			}
			sendMessage(lg.get("passing_night"));
		}
	}

	/*
	 * Helpers
	 */
	
	private void refreshStats() {
		int count = 0;
		int sleeping = 0;
		
		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
			if (isCountable(player)) {
				count++;
				if (player.getSleepTicks() > 0) {
					sleeping++;
				}
			}
		}

		total = count;
		inBed = sleeping;
	}
	
	private void sendMessage(String msg) {
		msg = StringHandler.colorize(msg);
		
		if(getConfig().getBoolean("use_actionbar")) {
			for (Player player : Bukkit.getServer().getOnlinePlayers()) {
				if (isCountable(player)) {
					if(Bukkit.getVersion().toLowerCase().contains("spigot")) {
						player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
					} /*else if(Bukkit.getVersion().toLowerCase().contains("papermc")) {
						player.sendActionBar(TextComponent.fromLegacyText(msg));
					}*/ else { //Bukkit.getVersion().toLowerCase().contains("bukkit")
						try {
							ActionBarAPI.sendActionBar(player, msg);
						} catch (NoClassDefFoundError e) {
							getLogger().log(Level.SEVERE, "Please install the ActionBar API (https://www.spigotmc.org/resources/actionbarapi-1-8-1-14-2.1315/)");
						}
					}
				}
			}
		} else {
			Bukkit.broadcastMessage(msg);
		}
	}

	private String formatMessage(Player player, String msg) {
		Integer percentage = (int) ((total == 0) ? 100 : Math.round(100.0 * (float) inBed / (float) total));

		Map<String, String> values = new HashMap<String, String>();
		values.put("player", player.getDisplayName());
		values.put("count", inBed.toString());
		values.put("total", total.toString());
		values.put("percentage", percentage.toString());
		values.put("goal", goal.toString());

		return StringHandler.translate(msg, values);
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

	private int getWeatherDuration() {
		Random r = new Random();
		return (int) (r.nextInt((int) (MAX_WEATHER_TICK - MIN_WEATHER_TICK)) + MIN_WEATHER_TICK);
	}

}
