package tr.pyro.minions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Durable intent before ownership transfer; replay a single minion and the affected player's inventory. */
final class TransferJournal {
    private final PyroMinions plugin;
    private final Path root;
    private final Map<UUID, Path> pending = new HashMap<>();
    TransferJournal(PyroMinions plugin) {
        this.plugin = plugin; root = plugin.getDataFolder().toPath().resolve("transactions");
    }
    boolean pending(UUID player) { return pending.containsKey(player); }
    static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (var channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            var bytes = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        // Fail closed if this filesystem cannot perform an atomic replacement.
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    private static YamlConfiguration read(Path path) throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(path.toFile()); return yaml;
    }
    void recoverWorld() throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.list(root)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                var journal = read(path);
                UUID owner = UUID.fromString(journal.getString("player", ""));
                String id = UUID.fromString(journal.getString("minion", "")).toString();
                Path dataPath = plugin.getDataFolder().toPath().resolve("minions.yml");
                var data = Files.exists(dataPath) ? read(dataPath) : new YamlConfiguration();
                data.set("schema", 2);
                if (!data.isConfigurationSection("minions")) data.createSection("minions");
                data.set("minions." + id, null);
                var record = journal.getConfigurationSection("record");
                if (record != null) for (String key : record.getKeys(true)) {
                    if (!record.isConfigurationSection(key)) data.set("minions." + id + "." + key, record.get(key));
                }
                write(dataPath, data.saveToString());
                pending.put(owner, path);
            }
        }
    }
    boolean place(Player player, MinionData d, ItemStack source) {
        return transfer(player, d, true, source);
    }
    boolean pickup(Player player, MinionData d, ItemStack token) {
        return transfer(player, d, false, token);
    }
    private boolean transfer(Player player, MinionData d, boolean place, ItemStack item) {
        if (pending(player.getUniqueId()) || !d.owner.equals(player.getUniqueId())) return false;
        var inventory = player.getInventory();
        ItemStack[] before = Arrays.stream(inventory.getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        ItemStack beforeHand = inventory.getItemInMainHand().clone();
        boolean durable = false;
        Path path = root.resolve(player.getUniqueId() + ".yml");
        try {
            if (place) {
                var remaining = item.clone(); remaining.setAmount(remaining.getAmount() - 1); inventory.setItemInMainHand(remaining);
            } else {
                if (!inventory.addItem(item.clone()).isEmpty()) {
                    inventory.setContents(before); inventory.setItemInMainHand(beforeHand); return false;
                }
                plugin.minions.remove(d.id);
            }
            var journal = new YamlConfiguration();
            journal.set("player", player.getUniqueId().toString()); journal.set("minion", d.id.toString());
            journal.set("inventory", Arrays.asList(inventory.getContents()));
            journal.set("main-hand", inventory.getItemInMainHand());
            if (place) {
                var record = MinionStore.snapshot(List.of(d)).getConfigurationSection("minions." + d.id);
                for (String key : Objects.requireNonNull(record).getKeys(true)) {
                    if (!record.isConfigurationSection(key)) journal.set("record." + key, record.get(key));
                }
            }
            write(path, journal.saveToString()); durable = true; pending.put(player.getUniqueId(), path);
            if (!place && d.stand != null && d.stand.isValid()) d.stand.remove();
            if (!plugin.saveNow()) throw new IOException("Minyon kaydı yazılamadı");
            savePlayer(player);
            Files.delete(path); pending.remove(player.getUniqueId());
            return true;
        } catch (Exception failure) {
            if (!durable) {
                inventory.setContents(before); inventory.setItemInMainHand(beforeHand);
                if (place) plugin.minions.remove(d.id); else plugin.minions.put(d.id, d);
                plugin.message(player, "transfer-failed");
            } else {
                // No retry can issue a second token. On next login replay the committed inventory.
                player.kickPlayer(PyroMinions.text(plugin.lang("messages.transfer-rejoin", "&cRejoin to recover the saved minion transaction.")));
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Minyon aktarım kaydı tamamlanamadı: " + player.getUniqueId(), failure);
            return durable;
        }
    }
    static Path playerDataFile(UUID player) {
        Path world = org.bukkit.Bukkit.getWorlds().getFirst().getWorldFolder().toPath().toAbsolutePath().normalize();
        // Since 26.1 Paper may expose a dimension folder instead of the level root.
        Path root = world;
        for (int i = 0; i < 5 && root != null; i++, root = root.getParent()) {
            if (Files.isRegularFile(root.resolve("level.dat"))) break;
        }
        if (root == null || !Files.isRegularFile(root.resolve("level.dat"))) root = world;
        String version = org.bukkit.Bukkit.getBukkitVersion().split("-", 2)[0];
        boolean modern = Integer.parseInt(version.split("\\.")[0]) >= 26;
        return root.resolve(modern ? "players/data" : "playerdata").resolve(player + ".dat");
    }
    private static void savePlayer(Player player) throws IOException {
        Path data = playerDataFile(player.getUniqueId());
        var previousTime = Files.exists(data) ? Files.getLastModifiedTime(data) : null;
        byte[] previous = Files.exists(data) ? Files.readAllBytes(data) : null;
        player.saveData();
        // Player.saveData has no success result. Do not clear the journal if no new file was written.
        if (!Files.isRegularFile(data) || (previousTime != null && previousTime.equals(Files.getLastModifiedTime(data))
            && Arrays.equals(previous, Files.readAllBytes(data)))) throw new IOException("Oyuncu dosyası güncellenemedi: " + player.getUniqueId());
        try (var channel = FileChannel.open(data, StandardOpenOption.WRITE)) { channel.force(true); }
    }
    void recoverPlayer(Player player) {
        Path path = pending.get(player.getUniqueId()); if (path == null) return;
        try {
            var journal = read(path); var items = journal.getList("inventory");
            if (items == null) throw new IOException("İşlem günlüğünde envanter eksik");
            player.getInventory().setContents(items.toArray(ItemStack[]::new));
            player.getInventory().setItemInMainHand(journal.getItemStack("main-hand", new ItemStack(org.bukkit.Material.AIR)));
            if (!plugin.saveNow()) throw new IOException("Minyon kaydı yazılamadı");
            savePlayer(player); Files.delete(path); pending.remove(player.getUniqueId());
        } catch (Exception failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Bekleyen minyon işlemi kurtarılamadı", failure);
            player.kickPlayer(PyroMinions.text(plugin.lang("messages.recovery-failed", "&cMinion recovery failed. Contact an administrator.")));
        }
    }
}