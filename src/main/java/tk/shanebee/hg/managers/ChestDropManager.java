package tk.shanebee.hg.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import tk.shanebee.hg.HG;
import tk.shanebee.hg.data.ChestDrop;
import tk.shanebee.hg.game.Game;
import tk.shanebee.hg.listeners.ChestDropListener;
import tk.shanebee.hg.tasks.StartChestDropTask;
import tk.shanebee.hg.util.Util;

import java.util.ArrayList;
import java.util.List;

public class ChestDropManager {
    private List<ChestDrop> chestDrops;
    private Game game;
    private Material chestDropType;
    public ChestDropManager(Game game) {
        chestDrops = new ArrayList<>();
        this.game = game;
        this.chestDropType = Material.ENDER_CHEST;
    }
    public void startChestDrop() {
        chestDrops.add(new ChestDrop(game, chestDropType));
    }
    public Material getChestDropType() {
        return chestDropType;
    }
    public List<ChestDrop> getChestDrops() {
        return chestDrops;
    }
    public void shutdown() {
        for (ChestDrop chestDrop : chestDrops) {
            chestDrop.shutdown();
        }
    }

}
