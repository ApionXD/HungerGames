package tk.shanebee.hg.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import tk.shanebee.hg.game.Game;

public class FreeRoamEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Game game;

    public FreeRoamEvent(Game game) {
        this.game = game;
    }

    /** Get the game involved in this event
     * @return The game
     */
    public Game getGame() {
        return this.game;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
