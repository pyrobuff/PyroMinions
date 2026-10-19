package tr.pyro.minions;

import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class ShopService {
    private final PyroMinions plugin;
    ShopService(PyroMinions plugin) { this.plugin = plugin; }
    boolean enabled(MinionType type) { return plugin.data().getBoolean("shop-enabled", true) && plugin.data().getBoolean("minions." + type.key() + ".shop.enabled", true); }
    double price(MinionType type) { return plugin.data().getDouble("minions." + type.key() + ".shop.price"); }
    int level(MinionType type) { return plugin.data().getInt("minions." + type.key() + ".shop.level", 1); }
    boolean canUse(Player player) { return player.hasPermission("pyrominions.use") && player.hasPermission("pyrominions.shop"); }
    boolean buy(Player player, MinionType type) {
        if (!canUse(player)) { plugin.message(player, "no-permission"); return false; }
        if (!enabled(type)) { plugin.message(player, "shop-disabled"); return false; }
        double cost = price(type); int level = level(type);
        if (!Double.isFinite(cost) || cost < 0 || !plugin.hasTier(type, level)) { plugin.message(player, "shop-disabled"); return false; }
        // Construct the ownerless item before charging. All purchases are one minion per click.
        ItemStack token = plugin.token(type, level, "classic", null);
        var simulation = Bukkit.createInventory(null, 36);
        simulation.setContents(Arrays.stream(player.getInventory().getStorageContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new));
        if (!simulation.addItem(token.clone()).isEmpty()) { plugin.message(player, "inventory-full"); return false; }
        if (!plugin.charge(player, cost)) return false;
        try {
            if (!player.getInventory().addItem(token).isEmpty()) { refund(player, cost); return false; }
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Satın alınan minyon envantere eklenemedi", e);
            refund(player, cost); return false;
        }
        plugin.message(player, "shop-purchased", "type", plugin.typeName(type), "level", level, "price", plugin.money(cost));
        return true;
    }
    private void refund(Player player, double price) {
        if (price == 0 || plugin.economy.depositPlayer(player, price).transactionSuccess()) plugin.message(player, "shop-refunded");
        else {
            plugin.getLogger().severe("Market iadesi başarısız: " + player.getUniqueId() + " miktar=" + price);
            plugin.message(player, "shop-refund-failed");
        }
    }
}
