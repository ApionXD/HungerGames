package tk.shanebee.hg.game;

import io.papermc.lib.PaperLib;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;
import tk.shanebee.hg.HG;
import tk.shanebee.hg.Status;
import tk.shanebee.hg.data.Config;
import tk.shanebee.hg.data.PlayerData;
import tk.shanebee.hg.events.PlayerJoinGameEvent;
import tk.shanebee.hg.events.PlayerLeaveGameEvent;
import tk.shanebee.hg.game.GameCommandData.CommandType;
import tk.shanebee.hg.gui.SpectatorGUI;
import tk.shanebee.hg.managers.PlayerManager;
import tk.shanebee.hg.util.Util;
import tk.shanebee.hg.util.Vault;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Data class for holding a {@link Game Game's} players
 */
public class GamePlayerData extends Data {

    private final PlayerManager playerManager;
    private final SpectatorGUI spectatorGUI;

    // Player Lists
    final List<UUID> players = new ArrayList<>();
    final List<UUID> spectators = new ArrayList<>();
    // This list contains all players who have joined the arena
    // Will be used to broadcast messages even if a player is no longer in the game
    @Getter
    final List<UUID> allPlayers = new ArrayList<>();

    // Data lists
    final Map<Player, Integer> kills = new HashMap<>();
    final Map<String, Team> teams = new HashMap<>();
    final HashSet<UUID> frozenPlayers = new HashSet<>();

    protected GamePlayerData(Game game) {
        super(game);
        this.playerManager = plugin.getPlayerManager();
        this.spectatorGUI = new SpectatorGUI(game);
    }

    // TODO Data methods

    /**
     * Get a list of all players in the game
     *
     * @return UUID list of all players in game
     */
    public List<UUID> getPlayers() {
        return players;
    }

    /**
     * Returns if a player is in the frozenPlayers set
     * @return true if player should be frozen, false if not
     */
    public boolean playerIsFrozen(final Player player) {
        return this.frozenPlayers.contains(player.getUniqueId());
    }

    void clearPlayers() {
        players.clear();
        allPlayers.clear();
    }

    /**
     * Get a list of all players currently spectating the game
     *
     * @return List of spectators
     */
    public List<UUID> getSpectators() {
        return new ArrayList<>(this.spectators);
    }

    void clearSpectators() {
        spectators.clear();
    }

    public SpectatorGUI getSpectatorGUI() {
        return spectatorGUI;
    }

    // Utility methods

    private void kitHelp(Player player) {
        // Clear the chat a little, making this message easier to see
        for (int i = 0; i < 20; ++i)
            Util.scm(player, " ");
        String kit = game.kitManager.getKitListString();
        Util.scm(player, " ");
        Util.scm(player, lang.kit_join_header);
        Util.scm(player, " ");
        if (player.hasPermission("hg.kit") && game.kitManager.hasKits()) {
            Util.scm(player, lang.kit_join_msg);
            Util.scm(player, " ");
            Util.scm(player, lang.kit_join_avail + kit);
            Util.scm(player, " ");
        }
        Util.scm(player, lang.kit_join_footer);
        Util.scm(player, " ");
    }

    /**
     * Respawn all players in the game back to spawn points
     */
    public void respawnAll() {
        for (UUID u : players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null)
                PaperLib.teleportAsync(p, pickSpawnAndPlaceInPlayerSpawnMap(u));
        }
    }

    void heal(Player player) {
        for (PotionEffect ef : player.getActivePotionEffects()) {
            player.removePotionEffect(ef.getType());
        }
        player.closeInventory();
        player.setHealth(20);
        player.setFoodLevel(20);
        try {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> player.setFireTicks(0), 1);
        } catch (IllegalPluginAccessException ignore) {
        }
    }

    /**
     * Freeze a player
     *
     * @param player Player to freeze
     */
    public void freeze(Player player) {
        frozenPlayers.add(player.getUniqueId());
    }

    /**
     * Unfreeze a player
     *
     * @param player Player to unfreeze
     */
    public void unFreeze(Player player) {
        frozenPlayers.remove(player.getUniqueId());
    }

    /**
     * Send a message to all players/spectators in the game
     *
     * @param message Message to send
     */
    public void msgAll(String message) {
        List<UUID> allPlayers = new ArrayList<>();
        allPlayers.addAll(players);
        allPlayers.addAll(spectators);
        for (UUID u : allPlayers) {
            Player p = Bukkit.getPlayer(u);
            if (p != null)
                Util.scm(p, message);
        }
    }

    /**
     * Sends a message to all players/spectators
     * <b>Includes players who have died and left the game.
     * Used for broadcasting win messages</b>
     *
     * @param message Message to send
     */
    public void msgAllPlayers(String message) {
        List<UUID> allPlayers = new ArrayList<>(this.allPlayers);
        allPlayers.addAll(this.spectators);
        for (UUID u : allPlayers) {
            Player p = Bukkit.getPlayer(u);
            if (p != null)
                Util.scm(p, lang.prefix + message);
        }
    }

    synchronized Location pickSpawnAndPlaceInPlayerSpawnMap(final UUID forPlayer) {
        GameArenaData gameArenaData = game.getGameArenaData();
        final List<Location> availableLocations = game.gameArenaData.spawns
                .stream()
                .filter(loc -> !gameArenaData.playerSpawnMap.containsKey(loc))
                .collect(Collectors.toList());
        final int maxBound = availableLocations.size();
        int spawn = getRandomIntegerBetweenRange(maxBound - 1);
        final Location location = availableLocations.get(spawn);
        gameArenaData.playerSpawnMap.put(location, forPlayer);
        return location;
    }

    boolean containsPlayer(Location location) {
        if (location == null) return false;

        for (UUID u : players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.getLocation().getBlock().equals(location.getBlock()))
                return true;
        }
        return false;
    }

    boolean vaultCheck(Player player) {
        if (Config.economy) {
            int cost = game.gameArenaData.cost;
            if (Vault.economy.getBalance(player) >= cost) {
                Vault.economy.withdrawPlayer(player, cost);
                return true;
            } else {
                Util.scm(player, lang.prefix + lang.cmd_join_no_money.replace("<cost>", String.valueOf(cost)));
                return false;
            }
        }
        return true;
    }

    /**
     * Add a kill to a player
     *
     * @param player The player to add a kill to
     */
    public void addKill(Player player) {
        this.kills.put(player, this.kills.get(player) + 1);
    }

    // TODO Game methods

    /**
     * Join a player to the game
     *
     * @param player Player to join the game
     */
    public void join(Player player) {
        join(player, false);
    }

    /**
     * Join a player to the game
     *
     * @param player  Player to join the game
     * @param command Whether joined using by using a command
     */
    public void join(Player player, boolean command) {
        GameArenaData gameArenaData = game.getGameArenaData();
        Status status = gameArenaData.getStatus();
        if (status != Status.WAITING && status != Status.STOPPED && status != Status.COUNTDOWN && status != Status.READY) {
            Util.scm(player, lang.arena_not_ready);
            if ((status == Status.RUNNING || status == Status.BEGINNING) && Config.spectateEnabled) {
                Util.scm(player, lang.arena_spectate.replace("<arena>", game.gameArenaData.getName()));
            }
        } else if (gameArenaData.maxPlayers <= players.size()) {
            Util.scm(player, "&c" + gameArenaData.getName() + " " + lang.game_full);
        } else if (!players.contains(player.getUniqueId())) {
            if (!vaultCheck(player)) {
                return;
            }
            // Call PlayerJoinGameEvent
            PlayerJoinGameEvent event = new PlayerJoinGameEvent(game, player);
            Bukkit.getPluginManager().callEvent(event);
            // If cancelled, stop the player from joining the game
            if (event.isCancelled()) return;

            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }

            UUID uuid = player.getUniqueId();
            players.add(uuid);
            allPlayers.add(uuid);

            Location loc = pickSpawnAndPlaceInPlayerSpawnMap(uuid);
            if (loc.getBlock().getRelative(BlockFace.DOWN).getType() == Material.AIR) {
                while (loc.getBlock().getRelative(BlockFace.DOWN).getType() == Material.AIR) {
                    loc.setY(loc.getY() - 1);
                }
            }

            Location previousLocation = player.getLocation();
            boolean teleportSuccess = player.teleport(loc);

            if (!teleportSuccess) {
                Bukkit.getLogger().severe("Player " + player.getName() + " teleport failed, setting task to try again every second.");
                new BukkitRunnable() {
                    final Player player = Bukkit.getPlayer(uuid);
                    boolean teleportSuccess = false;
                    @Override
                    public void run() {
                        if (player == null) {
                            this.cancel();
                            return;
                        }

                        teleportSuccess = player.teleport(loc);
                        if (teleportSuccess) {
                            this.cancel();
                        }
                    }
                }.runTaskTimer(HG.getPlugin(), 0, 20L);
            }

            PlayerData playerData = new PlayerData(player, game);
            if (command && Config.savePreviousLocation) {
                playerData.setPreviousLocation(previousLocation);
            }
            playerManager.addPlayerData(playerData);
            gameArenaData.board.setBoard(player);

            heal(player);
            freeze(player);
            kills.put(player, 0);

            if (Config.enableleaveitem){
                ItemStack leaveitem = new ItemStack(Objects.requireNonNull(Material.getMaterial(Config.leaveitemtype)), 1);
                ItemMeta commeta = leaveitem.getItemMeta();
                assert commeta != null;
                commeta.setDisplayName(lang.leave_game);
                leaveitem.setItemMeta(commeta);
                player.getInventory().setItem(8, leaveitem);
            }

            if (Config.enableforcestartitem && player.hasPermission("hg.forcestart")) {
                ItemStack start = new ItemStack(Objects.requireNonNull(Material.getMaterial(Config.forcestartitem)), 1);
                ItemMeta meta = start.getItemMeta();
                assert meta != null;
                meta.setDisplayName(lang.force_start);
                start.setItemMeta(meta);
                player.getInventory().setItem(0, start);
            }

            if (players.size() == 1 && status == Status.READY)
                gameArenaData.setStatus(Status.WAITING);
            if (players.size() >= game.gameArenaData.minPlayers && (status == Status.WAITING || status == Status.READY)) {
                game.startPreGame();
            } else if (status == Status.WAITING) {
                String broadcast = lang.player_joined_game
                        .replace("<arena>", gameArenaData.getName())
                        .replace("<player>", player.getName()) + (gameArenaData.minPlayers - players.size() <= 0 ? "!" : ":" +
                        lang.players_to_start.replace("<amount>", String.valueOf((gameArenaData.minPlayers - players.size()))));
                if (Config.broadcastJoinMessages) {
                    Util.broadcast(broadcast);
                } else {
                    msgAll(broadcast);
                }
            }
            kitHelp(player);

            game.gameBlockData.updateLobbyBlock();
            game.gameArenaData.updateBoards();
            game.gameCommandData.runCommands(CommandType.JOIN, player);
        }
    }

    /**
     * Make a player leave the game
     *
     * @param player Player to leave the game
     * @param death  Whether the player has died or not (Generally should be false)
     */
    public void leave(Player player, Boolean death) {
        Bukkit.getPluginManager().callEvent(new PlayerLeaveGameEvent(game, player, death));
        Status status = game.getGameArenaData().getStatus();
        if (status.equals(Status.BEGINNING) || status.equals(Status.WAITING)) {
            //Makes the spawn that the player is in available again
            game.gameArenaData.playerSpawnMap.entrySet().stream()
                    .filter(playerUuidSpawn -> Objects.equals(playerUuidSpawn.getValue(), player.getUniqueId()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(loc -> game.gameArenaData.playerSpawnMap.remove(loc));
        }
        UUID uuid = player.getUniqueId();
        players.remove(uuid);
        if (!death) allPlayers.remove(uuid); // Only remove the player if they voluntarily left the game
        unFreeze(player);
        if (death) {
            if (Config.spectateEnabled && Config.spectateOnDeath && !game.isGameOver()) {
                spectate(player);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 5, 1);
                player.sendTitle(game.gameArenaData.getName(), Util.getColString(lang.spectator_start_title), 10, 100, 10);
                game.updateAfterDeath(player, true);
                return;
            } else if (game.gameArenaData.getStatus() == Status.RUNNING)
                game.getGameBarData().removePlayer(player);
        }
        heal(player);
        PlayerData playerData = playerManager.getPlayerData(uuid);
        assert playerData != null;
        Location previousLocation = playerData.getPreviousLocation();

        playerData.restore(player);
        exit(player, previousLocation);
        playerManager.removePlayerData(player);
        if (death) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 5, 1);
        }
        game.updateAfterDeath(player, death);
    }

    void exit(Player player, @Nullable Location exitLocation) {
        GameArenaData gameArenaData = game.getGameArenaData();
        player.setInvulnerable(false);
        if (gameArenaData.getStatus() == Status.RUNNING)
            game.getGameBarData().removePlayer(player);
        Location loc;
        if (exitLocation != null) {
            loc = exitLocation;
        } else if (gameArenaData.exit != null && gameArenaData.exit.getWorld() != null) {
            loc = gameArenaData.exit;
        } else {
            Location worldSpawn = player.getWorld().getSpawnLocation();
            Location bedLocation = player.getBedSpawnLocation();
            loc = bedLocation != null ? bedLocation : worldSpawn;
        }
        PlayerData playerData = playerManager.getData(player);
        if (playerData == null || playerData.isOnline()) {
            PaperLib.teleportAsync(player, loc);
        } else {
            PaperLib.teleportAsync(player, loc);
        }
    }

    /**
     * Put a player into spectator for this game
     *
     * @param spectator The player to spectate
     */
    public void spectate(Player spectator) {
        UUID uuid = spectator.getUniqueId();
        PaperLib.teleportAsync(spectator, game.gameArenaData.getSpawns().get(0));
        if (playerManager.hasPlayerData(uuid)) {
            playerManager.transferPlayerDataToSpectator(uuid);
        } else {
            playerManager.addSpectatorData(new PlayerData(spectator, game));
        }
        this.spectators.add(uuid);
        spectator.setGameMode(GameMode.SURVIVAL);
        spectator.setCollidable(false);
        if (Config.spectateFly)
            spectator.setAllowFlight(true);

        if (Config.spectateHide) {
            for (UUID u : players) {
                Player player = Bukkit.getPlayer(u);
                if (player == null) continue;
                player.hidePlayer(plugin, spectator);
            }
            for (UUID u : spectators) {
                Player player = Bukkit.getPlayer(u);
                if (player == null) continue;
                player.hidePlayer(plugin, spectator);
            }
        }
        game.getGameBarData().addPlayer(spectator);
        game.gameArenaData.board.setBoard(spectator);
        spectator.getInventory().setItem(0, plugin.getItemStackManager().getSpectatorCompass());
    }

    /**
     * Remove a player from spectator of this game
     *
     * @param spectator The player to remove
     */
    public void leaveSpectate(Player spectator) {
        UUID uuid = spectator.getUniqueId();
        PlayerData playerData = playerManager.getSpectatorData(uuid);
        assert playerData != null;
        Location previousLocation = playerData.getPreviousLocation();

        playerData.restore(spectator);
        spectators.remove(spectator.getUniqueId());
        spectator.setCollidable(true);
        if (Config.spectateFly) {
            GameMode mode = spectator.getGameMode();
            if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                spectator.setAllowFlight(false);
        }
        if (Config.spectateHide)
            revealPlayer(spectator);
        exit(spectator, previousLocation);
        playerManager.removeSpectatorData(uuid);
    }

    void revealPlayer(Player hidden) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showPlayer(plugin, hidden);
        }
    }

    public void addTeam(Team team) {
        teams.put(team.getName(), team);
    }

    public void clearTeams() {
        teams.clear();
    }

    public boolean hasTeam(String name) {
        return teams.containsKey(name);
    }

    // UTIL
    private static int getRandomIntegerBetweenRange(double max) {
        return (int) ((Math.random() * ((max - (double) 0) + 1)) + (double) 0);
    }

}
