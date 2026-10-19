package tr.pyro.minions;

import java.util.Collection;
import java.util.UUID;
import org.bukkit.permissions.Permissible;

/** Numeric permissions replace the default; the island cap remains independent. */
final class PlacementLimits {
    static int playerLimit(Permissible player, int fallback) {
        int highest = -1;
        for (var permission : player.getEffectivePermissions()) {
            String node = permission.getPermission();
            if (!permission.getValue() || !node.startsWith("minion.max.") || !player.hasPermission(node)) continue;
            String value = node.substring("minion.max.".length());
            if (!value.matches("[0-9]+")) continue;
            try { highest = Math.max(highest, Integer.parseInt(value)); }
            catch (NumberFormatException ignored) { /* Overflow is not an unlimited permission. */ }
        }
        return highest < 0 ? fallback : highest;
    }
    static long playerCount(Collection<MinionData> records, UUID owner) {
        return records.stream().filter(d -> d.owner.equals(owner)).count();
    }
    static long islandCount(Collection<MinionData> records, UUID island) {
        return island == null ? 0 : records.stream().filter(d -> island.equals(d.island)).count();
    }
}
