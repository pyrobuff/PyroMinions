package tr.pyro.minions;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.event.*;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;

/** Optional integration isolated so Paper can load without SuperiorSkyblock. */
final class IslandHook implements Listener {
    private final PyroMinions plugin;
    IslandHook(PyroMinions plugin) { this.plugin = plugin; }
    UUID islandAt(Location location) {
        var island = SuperiorSkyblockAPI.getIslandAt(location);
        return island == null ? null : island.getUniqueId();
    }
    boolean canBuild(UUID player, Location location) {
        var island = SuperiorSkyblockAPI.getIslandAt(location);
        if (island == null) return false;
        if (island.getOwner() != null && player.equals(island.getOwner().getUniqueId())) return true;
        return island.getIslandMembers(true).stream().anyMatch(member -> player.equals(member.getUniqueId()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDisband(IslandDisbandEvent event) {
        UUID id = event.getIsland().getUniqueId();
        // Run after the event completes; cancelled disbands must retain all records.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) return;
            plugin.minions.values().stream().filter(m -> id.equals(m.island)).toList().forEach(plugin::remove);
            plugin.saveNow();
        });
    }
}
