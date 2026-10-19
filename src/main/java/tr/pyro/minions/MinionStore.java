package tr.pyro.minions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Fails closed on invalid data; writes through a temporary file and keeps a backup. */
public final class MinionStore {
    private final PyroMinions plugin;
    private final Path file;
    private final java.util.concurrent.atomic.AtomicLong saveGeneration = new java.util.concurrent.atomic.AtomicLong();
    public MinionStore(PyroMinions plugin) { this.plugin = plugin; file = plugin.getDataFolder().toPath().resolve("minions.yml"); }
    public Map<UUID, MinionData> load() throws IOException, InvalidConfigurationException {
        Map<UUID, MinionData> result = new LinkedHashMap<>();
        if (!Files.exists(file)) return result;
        var yaml = new YamlConfiguration(); yaml.load(file.toFile());
        var root = yaml.getConfigurationSection("minions");
        if (root == null) throw new IOException("minions.yml: minions bölümü eksik.");
        for (String key : root.getKeys(false)) {
            var s = Objects.requireNonNull(root.getConfigurationSection(key));
            var world = UUID.fromString(s.getString("world", ""));
            // Unloaded/temporarily unavailable worlds retain their records without loading chunks.
            var d = new MinionData(UUID.fromString(key), UUID.fromString(s.getString("owner", "")),
                MinionType.parse(s.getString("type", "")), world, s.getInt("x"), s.getInt("y"), s.getInt("z"));
            d.level = s.getInt("level", 1);
            d.statistics = s.getLong("statistics", 0);
            if (d.statistics < 0) throw new IOException("Negatif minyon istatistiği: " + key);
            if (!plugin.hasTier(d.type, d.level)) throw new IOException("Geçersiz minyon seviyesi: " + key);
            d.hunger = Math.max(0, Math.min(plugin.maxHealth(d), s.getInt("health")));
            d.name = s.getString("name", d.type.title);
            d.skin = s.getString("skin", "classic");
            d.hologram = s.getBoolean("hologram", true);
            d.facing = BlockFace.valueOf(s.getString("facing", "NORTH"));
            if (!List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST).contains(d.facing)) throw new IOException("Geçersiz yön: " + key);
            if (s.contains("island")) d.island = UUID.fromString(s.getString("island", ""));
            if (s.contains("chest.world")) {
                d.chestWorld = UUID.fromString(s.getString("chest.world", ""));
                d.chestX = s.getInt("chest.x"); d.chestY = s.getInt("chest.y"); d.chestZ = s.getInt("chest.z");
            }
            boolean occupied = result.values().stream().anyMatch(m -> m.world.equals(d.world) && m.x == d.x && m.y == d.y && m.z == d.z);
            if (occupied) throw new IOException("Aynı konumda iki minyon: " + key);
            result.put(d.id, d);
        }
        return result;
    }
    static YamlConfiguration snapshot(Collection<MinionData> records) {
        var yaml = new YamlConfiguration(); yaml.set("schema", 2); yaml.createSection("minions");
        for (var d : records) {
            var s = yaml.createSection("minions." + d.id);
            s.set("owner", d.owner.toString()); s.set("type", d.type.key()); s.set("world", d.world.toString());
            s.set("x", d.x); s.set("y", d.y); s.set("z", d.z); s.set("level", d.level); s.set("health", d.hunger);
            s.set("statistics", d.statistics);
            s.set("name", d.name); s.set("skin", d.skin); s.set("hologram", d.hologram); s.set("facing", d.facing.name());
            if (d.island != null) s.set("island", d.island.toString());
            if (d.chestWorld != null) {
                s.set("chest.world", d.chestWorld.toString()); s.set("chest.x", d.chestX); s.set("chest.y", d.chestY); s.set("chest.z", d.chestZ);
            }
        }
        return yaml;
    }
    public void save(Collection<MinionData> records) throws IOException {
        long generation = reserveSave();
        saveSnapshot(snapshot(records), generation);
    }
    long reserveSave() { return saveGeneration.incrementAndGet(); }
    synchronized void saveSnapshot(YamlConfiguration yaml, long generation) throws IOException {
        // A newer synchronous transfer save must never be overwritten by an old autosave.
        if (generation != saveGeneration.get()) return;
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) Files.copy(file, file.resolveSibling("minions.yml.bak"), StandardCopyOption.REPLACE_EXISTING);
        TransferJournal.write(file, yaml.saveToString());
    }
}
