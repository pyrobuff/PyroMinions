package tr.pyro.minions;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

final class Menus implements Listener {
    enum Page { MAIN("main"), SETTINGS("settings"), SKINS("skins"), LIST("minions"), SHOP("shop"); final String file; Page(String file) { this.file = file; } }
    static final class Holder implements InventoryHolder {
        final Page page; final UUID minion;
        final Map<Integer, String> actions = new HashMap<>();
        final Map<String, Double> prices = new HashMap<>();
        Inventory inventory;
        Holder(Page page, UUID minion) { this.page = page; this.minion = minion; }
        public Inventory getInventory() { return inventory; }
    }
    private final PyroMinions plugin;
    MinionListener listener;
    private final Map<UUID, Long> clicks = new HashMap<>();
    boolean allowClick(UUID player, long now) {
        Long previous = clicks.get(player);
        if (previous != null && now - previous < 250_000_000L) return false;
        clicks.put(player, now); return true;
    }
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event) { clicks.remove(event.getPlayer().getUniqueId()); }
    Menus(PyroMinions plugin) { this.plugin = plugin; }
    private Map<String, String> values(MinionData d) {
        Map<String, String> v = new HashMap<>();
        if (d == null) return v;
        v.put("name", plugin.localizedName(d.type, d.name)); v.put("display_name", plugin.displayName(d)); v.put("type", plugin.typeName(d.type));
        v.put("level", "" + d.level); v.put("health", "" + d.hunger); v.put("max_health", "" + plugin.maxHealth(d));
        v.put("statistics", "" + d.statistics); v.put("statistics_unit", plugin.statisticsUnit(d.type));
        v.put("interval", number(plugin.interval(d) / 20.0)); v.put("double_chance", number(plugin.tier(d).getDouble("double-drop-chance")));
        v.put("chest_range", number(plugin.data().getDouble("chest-link-distance", 8)));
        v.put("chest", d.chestWorld == null ? plugin.lang("labels.no-chest", "Yok") : d.chestX + ", " + d.chestY + ", " + d.chestZ);
        v.put("feed_price", plugin.money(Rules.feedingPrice(plugin.maxHealth(d) - d.hunger, plugin.data().getDouble("feed-price-per-point", .5))));
        v.put("upgrade_price", plugin.money(plugin.tier(d).getDouble("upgrade-price")));
        v.put("upgrade_status", plugin.hasTier(d.type, d.level + 1)
            ? PluginFiles.format(plugin.lang("labels.upgrade-level-format", "%level%. Seviye"), Map.of("level", "" + (d.level + 1)))
            : plugin.lang("labels.max-level", "Son seviye"));
        v.put("hologram", plugin.lang(d.hologram ? "labels.enabled" : "labels.disabled", d.hologram ? "Açık" : "Kapalı"));
        v.put("skin_name", plugin.skinName(plugin.skins.key(d.skin)));
        v.put("x", "" + d.x); v.put("y", "" + d.y); v.put("z", "" + d.z);
        return v;
    }
    private String number(double value) { return String.format(Locale.ROOT, "%.2f", value).replaceFirst("\\.?0+$", ""); }
    private ItemStack item(ConfigurationSection section, MinionData d, String skin, Map<String, String> values) {
        String material = section.getString("material", "STONE");
        ItemStack stack = material.equals("MINION_HEAD") ? plugin.skins.head(skin, d == null ? MinionType.parse(values.getOrDefault("type_key", "miner")) : d.type) : new ItemStack(PluginFiles.material(material));
        var meta = stack.getItemMeta(); meta.setDisplayName(PyroMinions.text(PluginFiles.format(section.getString("name", " "), values)));
        meta.setLore(section.getStringList("lore").stream().map(s -> PyroMinions.text(PluginFiles.format(s, values))).toList());
        stack.setItemMeta(meta); return stack;
    }
    private Holder create(Page page, MinionData d, Map<String, String> values) {
        var config = plugin.files.menus.get(page.file);
        var h = new Holder(page, d == null ? null : d.id);
        h.inventory = Bukkit.createInventory(h, config.getInt("size"), PyroMinions.text(PluginFiles.format(config.getString("title", "PyroMinions"), values)));
        var filler = new ItemStack(PluginFiles.material(config.getString("filler", "BLACK_STAINED_GLASS_PANE")));
        var meta = filler.getItemMeta(); meta.setDisplayName(PyroMinions.text(" ")); filler.setItemMeta(meta);
        for (int i = 0; i < h.inventory.getSize(); i++) h.inventory.setItem(i, filler);
        var items = config.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            var section = Objects.requireNonNull(items.getConfigurationSection(key)); if (!section.getBoolean("enabled", true)) continue;
            String action = section.getString("action", key); var itemValues = new HashMap<>(values);
            if (d != null && d.type == MinionType.FEEDER && (action.equals("link") || action.equals("unlink"))) continue;
            if (page == Page.SHOP && action.startsWith("buy:")) {
                MinionType type = MinionType.parse(action.substring(4));
                if (!plugin.shop.enabled(type)) continue;
                itemValues.put("type", plugin.typeName(type)); itemValues.put("type_key", type.key());
                itemValues.put("price", plugin.money(plugin.shop.price(type))); itemValues.put("level", "" + plugin.shop.level(type));
                h.prices.put(action, plugin.shop.price(type));
            }
            int slot = section.getInt("slot");
            var stack = item(section, d, d == null ? "classic" : d.skin, itemValues);
            if (d != null && d.type == MinionType.FEEDER && action.equals("properties")) {
                var itemMeta = stack.getItemMeta();
                var lore = new ArrayList<>(Objects.requireNonNullElse(itemMeta.getLore(), List.of()));
                lore.add(PyroMinions.text(" "));
                var tiers = Objects.requireNonNull(plugin.data().getConfigurationSection("minions.feeder.tiers"));
                for (String level : tiers.getKeys(false).stream().sorted(Comparator.comparingInt(Integer::parseInt)).toList()) {
                    int radius = tiers.getInt(level + ".range");
                    var rangeValues = Map.of("level", level, "diameter", "" + (radius * 2 + 1), "radius", "" + radius);
                    lore.add(PyroMinions.text(PluginFiles.format(plugin.lang("labels.feeder-level-range", "&7%level%. Seviye: &b%diameter%x%diameter% blok &8(%radius% blok yarıçap)"), rangeValues)));
                }
                itemMeta.setLore(lore); stack.setItemMeta(itemMeta);
            }
            h.inventory.setItem(slot, stack);
            h.actions.put(slot, action);
        }
        return h;
    }
    void main(Player p, MinionData d) { p.openInventory(create(Page.MAIN, d, values(d)).inventory); }
    void settings(Player p, MinionData d) { p.openInventory(create(Page.SETTINGS, d, values(d)).inventory); }
    void shop(Player player) {
        if (!plugin.shop.canUse(player)) { plugin.message(player, "no-permission"); return; }
        if (!plugin.data().getBoolean("shop-enabled", true)) { plugin.message(player, "shop-disabled"); return; }
        player.openInventory(create(Page.SHOP, null, Map.of()).inventory);
    }
    void skins(Player p, MinionData d) {
        var base = values(d); var h = create(Page.SKINS, d, base); var config = plugin.files.menus.get("skins");
        var entries = Objects.requireNonNull(config.getConfigurationSection("entries")); var slots = entries.getIntegerList("slots"); int index = 0;
        for (String key : Objects.requireNonNull(plugin.data().getConfigurationSection("skins")).getKeys(false)) {
            var v = new HashMap<>(base); v.put("skin_name", plugin.skinName(key));
            v.put("selected", plugin.lang(key.equals(plugin.skins.key(d.skin)) ? "labels.selected" : "labels.select", "Seç"));
            int slot = slots.get(index++); h.inventory.setItem(slot, item(entries, d, key, v)); h.actions.put(slot, "skin:" + key);
        }
        p.openInventory(h.inventory);
    }
    void list(Player p, UUID owner) {
        var records = plugin.minions.values().stream().filter(d -> d.owner.equals(owner)).toList();
        var h = create(Page.LIST, null, Map.of("total", "" + records.size()));
        var entries = Objects.requireNonNull(plugin.files.menus.get("minions").getConfigurationSection("entries"));
        var slots = entries.getIntegerList("slots"); int count = Math.min(records.size(), slots.size());
        for (int i = 0; i < count; i++) {
            var d = records.get(i); int slot = slots.get(i); h.inventory.setItem(slot, item(entries, d, d.skin, values(d))); h.actions.put(slot, "minion:" + d.id);
        }
        p.openInventory(h.inventory);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getRawSlot() < 0 || event.getRawSlot() >= h.inventory.getSize()) return;
        String action = h.actions.get(event.getRawSlot()); if (action == null) return;
        if (!allowClick(p.getUniqueId(), System.nanoTime())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.getOpenInventory().getTopInventory() != h.inventory) return;
            if (action.equals("close")) { p.closeInventory(); return; }
            if (h.page == Page.SHOP) {
                if (!action.startsWith("buy:")) return;
                MinionType type = MinionType.parse(action.substring(4));
                if (Double.compare(h.prices.getOrDefault(action, -1.0), plugin.shop.price(type)) != 0) plugin.message(p, "shop-price-changed");
                else plugin.shop.buy(p, type);
                shop(p); return;
            }
            if (action.startsWith("minion:")) {
                var selected = plugin.minions.get(UUID.fromString(action.substring(7)));
                if (!plugin.canManage(p, selected)) { plugin.message(p, "too-far"); return; }
                main(p, selected); return;
            }
            var d = plugin.minions.get(h.minion);
            if (!plugin.canManage(p, d)) { p.closeInventory(); plugin.message(p, "unavailable"); return; }
            if (action.startsWith("skin:")) {
                String key = action.substring(5);
                if (!plugin.data().isConfigurationSection("skins." + key)) return;
                d.skin = key; plugin.update(d); skins(p, d); return;
            }
            switch (action) {
                case "north" -> d.facing = BlockFace.NORTH;
                case "south" -> d.facing = BlockFace.SOUTH;
                case "west" -> d.facing = BlockFace.WEST;
                case "east" -> d.facing = BlockFace.EAST;
                case "link" -> {
                    if (d.type == MinionType.FEEDER) return;
                    listener.links.put(p.getUniqueId(), new MinionListener.Pending(d.id, System.currentTimeMillis() + 30000));
                    p.closeInventory(); plugin.message(p, "link-prompt"); return;
                }
                case "unlink" -> { if (d.type == MinionType.FEEDER) return; d.chestWorld = null; plugin.message(p, "unlinked"); }
                case "feed" -> plugin.feed(p, d);
                case "upgrade" -> plugin.upgrade(p, d);
                case "settings" -> { settings(p, d); return; }
                case "skins" -> { skins(p, d); return; }
                case "hologram" -> { d.hologram = !d.hologram; plugin.update(d); settings(p, d); return; }
                case "rename" -> {
                    listener.renames.put(p.getUniqueId(), new MinionListener.Pending(d.id, System.currentTimeMillis() + 60000));
                    p.closeInventory(); plugin.message(p, "rename-prompt"); return;
                }
                case "back" -> { if (h.page == Page.SKINS) settings(p, d); else main(p, d); return; }
                default -> { return; } // Properties, overview and arbitrary decorative entries are read-only.
            }
            plugin.update(d); main(p, d);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }
}
