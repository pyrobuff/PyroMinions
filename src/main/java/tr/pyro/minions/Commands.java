package tr.pyro.minions;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

final class Commands implements CommandExecutor, TabCompleter {
    private final PyroMinions plugin;
    Commands(PyroMinions plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("pyrominionsadmin") && !sender.hasPermission("pyrominions.admin")) { plugin.message(sender, "no-permission"); return true; }
        if (args.length == 0) {
            if (sender instanceof Player p && p.hasPermission("pyrominions.use")) plugin.menus.list(p, p.getUniqueId());
            else help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help") || sub.equals("about")) { help(sender); return true; }
        if (sub.equals("shop") || sub.equals("market")) {
            if (sender instanceof Player player) plugin.menus.shop(player); else plugin.message(sender, "player-only");
            return true;
        }
        if (sub.equals("list") || sub.equals("minions") || sub.equals("menu")) {
            if (!(sender instanceof Player p)) { plugin.message(sender, "player-only"); return true; }
            if (!p.hasPermission("pyrominions.use")) { plugin.message(sender, "no-permission"); return true; }
            UUID target = p.getUniqueId();
            if (args.length > 1) {
                if (!sender.hasPermission("pyrominions.admin")) { plugin.message(sender, "no-permission"); return true; }
                var other = Bukkit.getPlayerExact(args[1]);
                if (other == null) { plugin.message(sender, "offline"); return true; }
                target = other.getUniqueId();
            }
            plugin.menus.list(p, target); return true;
        }
        if (!sender.hasPermission("pyrominions.admin")) { plugin.message(sender, "no-permission"); return true; }
        try {
            switch (sub) {
                case "get" -> {
                    if (!(sender instanceof Player p)) throw new IllegalArgumentException("get-player-only");
                    if (args.length < 2 || args.length > 4) throw new IllegalArgumentException("usage-get");
                    give(sender, p, parseType(args[1]), number(args, 2, 1), number(args, 3, 1));
                }
                case "give" -> {
                    if (args.length < 3 || args.length > 5) throw new IllegalArgumentException("usage-give");
                    var player = Bukkit.getPlayerExact(args[2]);
                    if (player == null) throw new IllegalArgumentException("offline");
                    give(sender, player, parseType(args[1]), number(args, 3, 1), number(args, 4, 1));
                }
                case "reload" -> reload(sender);
                case "stats" -> plugin.message(sender, "stats", "total", plugin.minions.size(), "loaded", plugin.minions.values().stream().filter(MinionData::chunkLoaded).count());
                case "remove" -> {
                    if (!sender.hasPermission("pyrominions.admin.remove")) { plugin.message(sender, "no-permission"); return true; }
                    if (args.length > 2) throw new IllegalArgumentException("usage-remove");
                    MinionData d;
                    if (args.length == 2) d = plugin.minions.get(parseId(args[1]));
                    else if (sender instanceof Player player) {
                        var hit = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 5, entity -> entity instanceof org.bukkit.entity.LivingEntity && entity != player);
                        d = hit == null ? null : plugin.fromEntity(hit.getHitEntity());
                    }
                    else throw new IllegalArgumentException("usage-remove");
                    if (d == null) throw new IllegalArgumentException("not-found");
                    plugin.remove(d); plugin.saveNow(); plugin.message(sender, "removed");
                }
                case "info" -> {
                    if (args.length != 2) throw new IllegalArgumentException("usage-info");
                    var d = plugin.minions.get(parseId(args[1]));
                    if (d == null) throw new IllegalArgumentException("not-found");
                    plugin.message(sender, "info", "id", d.id, "type", plugin.typeName(d.type), "owner", Bukkit.getOfflinePlayer(d.owner).getName(), "level", d.level, "health", d.hunger, "max_health", plugin.maxHealth(d));
                }
                default -> help(sender);
            }
        } catch (IllegalArgumentException e) { plugin.message(sender, "error", "reason", plugin.lang("messages." + e.getMessage(), String.valueOf(e.getMessage()))); }
        return true;
    }
    private MinionType parseType(String value) {
        try { return MinionType.parse(value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("invalid-type"); }
    }
    private UUID parseId(String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("invalid-uuid"); }
    }
    private int number(String[] args, int index, int fallback) {
        if (args.length <= index) return fallback;
        try { return Integer.parseInt(args[index]); } catch (NumberFormatException e) { throw new IllegalArgumentException("invalid-number"); }
    }
    private void give(CommandSender sender, Player player, MinionType type, int level, int amount) {
        if (!plugin.hasTier(type, level)) throw new IllegalArgumentException("invalid-level");
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("invalid-amount");
        var token = plugin.token(type, level, "classic", null); token.setAmount(amount);
        // Simulate insertion first; never drop admin grants into the world or partially give them.
        var simulation = Bukkit.createInventory(null, 36); simulation.setContents(player.getInventory().getStorageContents());
        if (!simulation.addItem(token.clone()).isEmpty()) throw new IllegalArgumentException("grant-inventory-full");
        player.getInventory().addItem(token); plugin.message(sender, "granted", "player", player.getName(), "amount", amount, "type", plugin.typeName(type));
    }
    private void reload(CommandSender sender) {
        PluginFiles before = plugin.files;
        try {
            plugin.files = PluginFiles.load(plugin);
            plugin.validateConfig();
            for (var d : plugin.minions.values()) if (!plugin.hasTier(d.type, d.level)) throw new IllegalArgumentException("Kullanılan seviye silinemez: " + d.type + " " + d.level);
            for (var d : plugin.minions.values()) { d.hunger = Math.min(d.hunger, plugin.maxHealth(d)); d.skin = plugin.skins.key(d.skin); plugin.update(d); }
            plugin.saveNow(); plugin.message(sender, "reloaded");
        } catch (Exception e) {
            try {
                plugin.files = before; plugin.validateConfig();
            } catch (Exception restore) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Ayar geri yükleme başarısız", restore); }
            plugin.message(sender, "reload-failed", "reason", e.getMessage());
        }
    }
    private void help(CommandSender sender) {
        for (String line : plugin.files.lang.getStringList("help.player")) sender.sendMessage(PyroMinions.text(line));
        if (!sender.hasPermission("pyrominions.admin")) return;
        for (String line : plugin.files.lang.getStringList("help.admin")) sender.sendMessage(PyroMinions.text(line));
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = new ArrayList<>(); boolean admin = sender.hasPermission("pyrominions.admin");
        if (args.length == 1) choices.addAll(admin ? List.of("help", "about", "shop", "get", "give", "minions", "list", "reload", "stats", "remove", "info") : List.of("help", "shop", "list", "minions"));
        if (args.length == 2 && admin && Set.of("get", "give").contains(args[0].toLowerCase(Locale.ROOT))) choices.addAll(List.of("feeder", "miner", "fisher"));
        if (admin && (args.length == 3 && args[0].equalsIgnoreCase("give") || args.length == 2 && Set.of("minions", "list").contains(args[0].toLowerCase(Locale.ROOT)))) Bukkit.getOnlinePlayers().forEach(p -> choices.add(p.getName()));
        if (args.length == 2 && admin && Set.of("info", "remove").contains(args[0].toLowerCase(Locale.ROOT))) plugin.minions.keySet().forEach(id -> choices.add(id.toString()));
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial)).toList();
    }
}
