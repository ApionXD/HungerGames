package tk.shanebee.hg.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import tk.shanebee.hg.*;
import tk.shanebee.hg.game.Bound;
import tk.shanebee.hg.game.Game;
import tk.shanebee.hg.game.GameArenaData;
import tk.shanebee.hg.managers.KitManager;
import tk.shanebee.hg.tasks.CompassTask;
import tk.shanebee.hg.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * General data handler for the plugin
 */
public class ArenaConfig {

	private FileConfiguration arenadat = null;
	private File customConfigFile = null;
	private final HG plugin;

	public ArenaConfig(HG plugin) {
		this.plugin = plugin;
		reloadCustomConfig();
		load();
	}

	/** Get arena data file
	 * @return Arena data file
	 */
	public FileConfiguration getConfig() {
		return arenadat;
	}

	public void reloadCustomConfig() {
		if (customConfigFile == null) {
			customConfigFile = new File(plugin.getDataFolder(), "arenas.yml");
		}
		if (!customConfigFile.exists()) {
			try {
				//noinspection ResultOfMethodCallIgnored
				customConfigFile.createNewFile();
			}
			catch (IOException e) {
				Bukkit.getServer().getLogger().severe(ChatColor.RED + "Could not create arena.yml!");
			}
			arenadat = YamlConfiguration.loadConfiguration(customConfigFile);
			saveCustomConfig();
			Util.log("New arenas.yml file has been successfully generated!");
		} else {
			arenadat = YamlConfiguration.loadConfiguration(customConfigFile);
		}
	}

	public FileConfiguration getCustomConfig() {
		if (arenadat == null) {
			this.reloadCustomConfig();
		}
		return arenadat;
	}

	public void saveCustomConfig() {
		if (arenadat == null || customConfigFile == null) {
			return;
		}
		try {
			getCustomConfig().save(customConfigFile);
		} catch (IOException ex) {
			Util.log("Could not save config to " + customConfigFile);
		}
	}

	@SuppressWarnings("ConstantConditions")
	public void load() {
		Util.log("Loading arenas...");
		Configuration pluginConfig = plugin.getHGConfig().getConfig();
		int freeroam = pluginConfig.getInt("settings.free-roam");

		if (customConfigFile.exists()) {
			// TODO remove after a while (aug 30/2020)
			// Move global exit from config.yml to arenas.yml
			if (pluginConfig.isSet("settings.globalexit")) {
				String globalExit = pluginConfig.getString("settings.globalexit");
				pluginConfig.set("settings.globalexit", null);
				plugin.getHGConfig().save();
				arenadat.set("global-exit-location", globalExit);
				saveCustomConfig();
			}

			new CompassTask(plugin);

			boolean hasData = arenadat.getConfigurationSection("arenas") != null;
			
			if (hasData) {
				for (String s : arenadat.getConfigurationSection("arenas").getKeys(false)) {
					boolean isReady = true;
					List<Location> spawns = new ArrayList<>();
					Sign lobbysign = null;
					int timer = 0;
					int cost = 0;
					int minplayers = 0;
					int maxplayers = 0;
					Bound b = null;
					List<String> commands;

					String path = "arenas." + s;
					try {
						timer = arenadat.getInt(path + ".info." + "timer");
						minplayers = arenadat.getInt(path + ".info." + "min-players");
						maxplayers = arenadat.getInt(path + ".info." + "max-players");
					} catch (Exception e) {
						Util.warning("Unable to load information for arena " + s + "!");
						isReady = false;
					}
					try {
						cost = arenadat.getInt(path + ".info." + "cost");
					} catch (Exception ignore) {
					}

					try {
						lobbysign = (Sign) getSLoc(arenadat.getString(path + "." + "lobbysign")).getBlock().getState();
					} catch (Exception e) { 
						Util.warning("Unable to load lobby sign for arena " + s + "!");
						isReady = false;
					}

					try {
						for (String l : arenadat.getStringList(path + "." + "spawns")) {
							spawns.add(getLocFromString(l));
						}
					} catch (Exception e) { 
						Util.warning("Unable to load random spawns for arena " + s + "!"); 
						isReady = false;
					}

					try {
						b = new Bound(arenadat.getString(path + ".bound." + "world"), BC(s, "x"), BC(s, "y"), BC(s, "z"), BC(s, "x2"), BC(s, "y2"), BC(s, "z2"));
					} catch (Exception e) { 
						Util.warning("Unable to load region bounds for arena " + s + "!"); 
						isReady = false;
					}

					Game game = new Game(s, b, spawns, lobbysign, timer, minplayers, maxplayers, freeroam, isReady, cost);
					plugin.getGames().add(game);

					KitManager kit = plugin.getItemStackManager().setGameKits(s, arenadat);
					if (kit != null)
						game.setKitManager(kit);

					if (!arenadat.getStringList(path + ".items").isEmpty()) {
						HashMap<Integer, ItemStack> items = new HashMap<>();
						for (String itemString : arenadat.getStringList(path + ".items")) {
							HG.getPlugin().getRandomItems().loadItems(itemString, items);
						}
						game.getGameItemData().setItems(items);
						Util.log(items.size() + " Random items have been loaded for arena: &b" + s);
					}
					if (!arenadat.getStringList(path + ".bonus").isEmpty()) {
						HashMap<Integer, ItemStack> bonusItems = new HashMap<>();
						for (String itemString : arenadat.getStringList(path + ".bonus")) {
							HG.getPlugin().getRandomItems().loadItems(itemString, bonusItems);
						}
						game.getGameItemData().setBonusItems(bonusItems);
						Util.log(bonusItems.size() + " Random bonus items have been loaded for arena: &b" + s);
					}

					if (arenadat.isSet(path + ".border.center")) {
						Location borderCenter = getSLoc(arenadat.getString(path + ".border.center"));
						game.getGameBorderData().setBorderCenter(borderCenter);
					}
					if (arenadat.isSet(path + ".border.size")) {
						int borderSize = arenadat.getInt(path + ".border.size");
						game.getGameBorderData().setBorderSize(borderSize);
					}
					if (arenadat.isSet(path + ".border.countdown-start") &&
							arenadat.isSet(path + ".border.countdown-end")) {
						int borderCountdownStart = arenadat.getInt(path + ".border.countdown-start");
						int borderCountdownEnd = arenadat.getInt(path + ".border.countdown-end");
						game.getGameBorderData().setBorderTimer(borderCountdownStart, borderCountdownEnd);
					}
					if (arenadat.isList(path + ".commands")) {
						commands = arenadat.getStringList(path + ".commands");
					} else {
						arenadat.set(path + ".commands", Collections.singletonList("none"));
						saveCustomConfig();
						commands = Collections.singletonList("none");
					}
					game.getGameCommandData().setCommands(commands);
					GameArenaData gameArenaData = game.getGameArenaData();
					if (arenadat.isSet(path + ".chest-refill")) {
						int chestRefill = arenadat.getInt(path + ".chest-refill");
						gameArenaData.setChestRefillTime(chestRefill);
					}
					if (arenadat.isSet(path + ".chest-refill-repeat")) {
						int chestRefillRepeat = arenadat.getInt(path + ".chest-refill-repeat");
						gameArenaData.setChestRefillRepeat(chestRefillRepeat);
					}
					try {
						String exitPath = "arenas." + gameArenaData.getName() + ".exit-location";
						String[] locString;
						if (arenadat.isSet(exitPath)) {
							locString = arenadat.getString(exitPath).split(":");
						} else {
							locString = arenadat.getString("global-exit-location").split(":");
						}
						Location location = new Location(Bukkit.getServer().getWorld(locString[0]), Integer.parseInt(locString[1]) + 0.5,
								Integer.parseInt(locString[2]) + 0.1, Integer.parseInt(locString[3]) + 0.5, Float.parseFloat(locString[4]), Float.parseFloat(locString[5]));
						gameArenaData.setExit(location);
					} catch (Exception ignore) {
						gameArenaData.setExit(game.getLobbyLocation().getWorld().getSpawnLocation());
					}
					Util.log("Arena &b" + s + "&7 has been &aloaded!");

				}
			} else {
				Util.log("&cNo Arenas to load.");
			}
		}
	}
	
	public int BC(String s, String st) {
		return arenadat.getInt("arenas." + s + ".bound." + st);
	}

	public Location getLocFromString(String s) {
		String[] h = s.split(":");
		return new Location(Bukkit.getServer().getWorld(h[0]), Integer.parseInt(h[1]) + 0.5, Integer.parseInt(h[2]), Integer.parseInt(h[3]) + 0.5, Float.parseFloat(h[4]), Float.parseFloat(h[5]));
	}

	public Location getSLoc(String s) {
		String[] h = s.split(":");
		return new Location(Bukkit.getServer().getWorld(h[0]), Integer.parseInt(h[1]), Integer.parseInt(h[2]), Integer.parseInt(h[3]));
	}

}
