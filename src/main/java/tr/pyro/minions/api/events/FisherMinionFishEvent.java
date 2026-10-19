package tr.pyro.minions.api.events;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Synchronous catch event, before the minion's drops enter a chest or the world. */
public final class FisherMinionFishEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID minionId, ownerUUID;
    private final Location location;
    private final List<ItemStack> drops;
    private boolean cancelled;
    public FisherMinionFishEvent(UUID minionId, UUID ownerUUID, Location location, List<ItemStack> drops) {
        this.minionId = minionId; this.ownerUUID = ownerUUID; this.location = location.clone();
        this.drops = new ArrayList<>(drops.stream().map(ItemStack::clone).toList());
    }
    public UUID getMinionId() { return minionId; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public Location getLocation() { return location.clone(); }
    /** Mutable list; listeners may append custom rewards. Do not modify it after the event returns. */
    public List<ItemStack> getDrops() { return drops; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
