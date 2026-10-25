package tr.pyro.minions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Loads an immutable candidate bundle first; reload swaps it only after validation. */
final class PluginFiles {
    final YamlConfiguration settings, lang;
    final Map<String, YamlConfiguration> menus;
    private PluginFiles(YamlConfiguration settings, YamlConfiguration lang, Map<String, YamlConfiguration> menus) {
        this.settings = settings; this.lang = lang; this.menus = Map.copyOf(menus);
    }
    static PluginFiles load(PyroMinions plugin) throws Exception {
        Path root = plugin.getDataFolder().toPath();
        var raw = read(root.resolve("config.yml"));
        String selectedLanguage = raw.getString("language", "tr").strip().toLowerCase(Locale.ROOT);
        if (!Set.of("tr", "en").contains(selectedLanguage)) throw new IllegalArgumentException("language must be tr or en.");
        boolean upgradeLanguage = !raw.contains("language");
        raw.set("language", selectedLanguage);
        boolean upgradeStatistics = raw.getInt("config-version", 1) < 4;
        if (raw.getInt("config-version", 1) < 3 || raw.contains("minions") || raw.contains("skins")) {
            Path backup = root.resolve("config-before-split.yml.bak");
            if (!Files.exists(backup)) Files.copy(root.resolve("config.yml"), backup);
            for (var type : MinionType.values()) {
                Path file = root.resolve("minions/" + type.key() + ".yml");
                var legacy = raw.getConfigurationSection("minions." + type.key());
                if (!Files.exists(file) && legacy != null) {
                    var extracted = new YamlConfiguration(); extracted.set("name", type.title);
                    for (String key : legacy.getKeys(false)) extracted.set(key, legacy.get(key));
                    Files.createDirectories(file.getParent()); extracted.save(file.toFile());
                }
            }
            // Preserve classic/spider overrides when migrating the skin catalog.
            if (!Files.exists(root.resolve("skins.yml"))) {
                var catalog = resource(plugin, "skins.yml");
                for (String key : List.of("classic", "spiderman")) {
                    var legacy = raw.getConfigurationSection("skins." + key);
                    if (legacy != null) {
                        for (String field : List.of("head-texture", "type-heads", "chest-color", "legs-color", "boots-color"))
                            if (legacy.contains(field)) catalog.set("skins." + key + "." + field, legacy.get(field));
                    }
                }
                catalog.save(root.resolve("skins.yml").toFile());
            }
            raw.set("minions", null); raw.set("skins", null); raw.set("config-version", 3);
            if (!raw.contains("hand-feed-health")) raw.set("hand-feed-health", 50);
            if (!raw.contains("hologram-format")) raw.set("hologram-format", "&e%name% &b%level%. Seviye");
            raw.save(root.resolve("config.yml").toFile());
        }
        if (upgradeStatistics) {
            backup(root.resolve("config.yml"), "config-before-shop.yml.bak");
            if (!raw.contains("shop-enabled")) raw.set("shop-enabled", true);
            raw.set("config-version", 4);
        }
        boolean upgradeLimits = !raw.contains("max-per-island");
        if (upgradeLimits) {
            backup(root.resolve("config.yml"), "config-before-limits.yml.bak");
            raw.set("max-per-island", 20);
        }
        String generalSettings = raw.saveToString();
        raw.setDefaults(resource(plugin, "config.yml"));
        for (var type : MinionType.values()) {
            String path = "minions/" + type.key() + ".yml"; ensure(plugin, path);
            var minion = read(root.resolve(path)); var defaults = resource(plugin, path); boolean changed = false;
            if (type == MinionType.MINER) {
                var tiers = minion.getConfigurationSection("tiers");
                if (tiers != null) for (String level : tiers.getKeys(false)) {
                    if (!tiers.contains(level + ".reach")) {
                        backup(root.resolve(path), "miner-before-forward-line.yml.bak");
                        tiers.set(level + ".reach", Integer.parseInt(level));
                        tiers.set(level + ".width", null); changed = true;
                    }
                }
            }
            for (String key : List.of("shop.enabled", "shop.price", "shop.level")) {
                if (!minion.contains(key)) { minion.set(key, defaults.get(key)); changed = true; }
            }
            if (changed) { backup(root.resolve(path), type.key() + "-before-shop.yml.bak"); minion.save(root.resolve(path).toFile()); }
            raw.set("minions." + type.key(), minion);
        }
        ensure(plugin, "skins.yml"); raw.set("skins", Objects.requireNonNull(read(root.resolve("skins.yml")).getConfigurationSection("skins"), "skins.yml: skins bölümü eksik"));
        ensure(plugin, "lang.yml"); var lang = read(root.resolve("lang.yml")); var defaultLang = resource(plugin, "lang.yml"); lang.setDefaults(defaultLang);
        if (upgradeStatistics) {
            backup(root.resolve("lang.yml"), "lang-before-stats.yml.bak");
            var lore = new ArrayList<>(lang.getStringList("item-lore"));
            if (lore.stream().noneMatch(s -> s.contains("%statistics%"))) {
                lore.add(Math.min(1, lore.size()), defaultLang.getStringList("item-lore").stream().filter(s -> s.contains("%statistics%")).findFirst().orElseThrow());
                lang.set("item-lore", lore);
            }
            var help = new ArrayList<>(lang.getStringList("help.player"));
            if (help.stream().noneMatch(s -> s.contains("shop"))) { help.add("/pminions shop — Minyon marketi"); lang.set("help.player", help); }
            lang.options().copyDefaults(true); lang.save(root.resolve("lang.yml").toFile());
        }
        ensure(plugin, "lang_en.yml");
        if (selectedLanguage.equals("en")) {
            lang = read(root.resolve("lang_en.yml"));
            lang.setDefaults(resource(plugin, "lang_en.yml"));
            lang.options().copyDefaults(true);
        }
        Map<String, YamlConfiguration> menus = new LinkedHashMap<>();
        for (String name : List.of("main", "settings", "skins", "minions", "shop")) {
            String file = "menus/" + name + ".yml"; ensure(plugin, file);
            var menu = read(root.resolve(file));
            if (upgradeStatistics && name.equals("main") && menu.isConfigurationSection("items.properties")) {
                var lore = new ArrayList<>(menu.getStringList("items.properties.lore"));
                if (lore.stream().noneMatch(s -> s.contains("%statistics%"))) {
                    backup(root.resolve(file), "main-before-stats.yml.bak"); boolean replaced = false;
                    for (int i = 0; i < lore.size(); i++) if (lore.get(i).contains("İstatistikler:")) {
                        lore.set(i, lore.get(i).replace("İstatistikler:", "İstatistikler: &f%statistics% %statistics_unit%")); replaced = true; break;
                    }
                    if (!replaced) lore.add(Math.min(1, lore.size()), "&6İstatistikler: &f%statistics% %statistics_unit%");
                    menu.set("items.properties.lore", lore); menu.save(root.resolve(file).toFile());
                }
            }
            applyMenuLanguage(menu, lang, name);
            validateMenu(name, menu); menus.put(name, menu);
        }
        if (raw.getInt("hand-feed-health") < 1) throw new IllegalArgumentException("hand-feed-health en az 1 olmalı");
        if (!raw.getString("hologram-format", "").contains("%level%")) throw new IllegalArgumentException("hologram-format içinde %level% zorunludur");
        if (menus.get("skins").getIntegerList("entries.slots").size() < Objects.requireNonNull(raw.getConfigurationSection("skins")).getKeys(false).size()) throw new IllegalArgumentException("Skin menüsünde tüm skinler için yeterli slot yok");
        if (upgradeStatistics || upgradeLimits || upgradeLanguage) Files.writeString(root.resolve("config.yml"), generalSettings, StandardCharsets.UTF_8);
        return new PluginFiles(raw, lang, menus);
    }
    private static void backup(Path file, String name) throws IOException {
        Path destination = file.resolveSibling(name); if (!Files.exists(destination)) Files.copy(file, destination);
    }
    static void applyMenuLanguage(YamlConfiguration menu, YamlConfiguration language, String name) {
        var section = language.getConfigurationSection("menus." + name);
        if (section == null) return;
        for (String key : section.getKeys(true)) {
            // Locales change text only; layout, materials and actions remain in menus/*.yml.
            if (section.isConfigurationSection(key)) continue;
            if (key.equals("title")) menu.set(key, section.get(key));
            else if ((key.endsWith(".name") || key.endsWith(".lore"))
                && menu.isConfigurationSection(key.substring(0, key.lastIndexOf('.')))) menu.set(key, section.get(key));
        }
    }
    private static void ensure(PyroMinions plugin, String path) { if (!Files.exists(plugin.getDataFolder().toPath().resolve(path))) plugin.saveResource(path, false); }
    static YamlConfiguration read(Path path) throws Exception { var yaml = new YamlConfiguration(); yaml.load(path.toFile()); return yaml; }
    private static YamlConfiguration resource(PyroMinions plugin, String file) throws IOException {
        try (var stream = Objects.requireNonNull(plugin.getResource(file), file); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { return YamlConfiguration.loadConfiguration(reader); }
    }
    private static void validateMenu(String name, YamlConfiguration menu) {
        int size = menu.getInt("size");
        if (size < 9 || size > 54 || size % 9 != 0) throw new IllegalArgumentException(name + ": menü boyutu 9..54 aralığında 9'un katı olmalı");
        material(menu.getString("filler", "BLACK_STAINED_GLASS_PANE"));
        Set<Integer> slots = new HashSet<>();
        var items = menu.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            var item = Objects.requireNonNull(items.getConfigurationSection(key));
            if (!item.getBoolean("enabled", true)) continue;
            String action = item.getString("action", key); if (action.startsWith("buy:")) MinionType.parse(action.substring(4));
            checkSlot(name, item.getInt("slot", -1), size, slots); material(item.getString("material", "STONE"));
        }
        for (int slot : menu.getIntegerList("entries.slots")) checkSlot(name, slot, size, slots);
        if (menu.contains("entries")) material(menu.getString("entries.material", "MINION_HEAD"));
    }
    private static void checkSlot(String name, int slot, int size, Set<Integer> used) {
        if (slot < 0 || slot >= size || !used.add(slot)) throw new IllegalArgumentException(name + ": geçersiz veya yinelenen slot " + slot);
    }
    static Material material(String text) {
        if (text.equals("MINION_HEAD")) return Material.PLAYER_HEAD;
        var material = Material.matchMaterial(text);
        if (material == null || !material.isItem() || material.isAir()) throw new IllegalArgumentException("Geçersiz menü malzemesi: " + text);
        return material;
    }
    String language(String key, String fallback) {
        String value = lang.getString(key);
        return value == null ? fallback : value;
    }
    static String format(String text, Map<String, String> values) {
        // Substitute tokens once so a player's name cannot inject another placeholder or a color code.
        var pattern = java.util.regex.Pattern.compile("%([a-z_]+)%"); var matcher = pattern.matcher(text); StringBuilder out = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), matcher.group())));
        matcher.appendTail(out); return out.toString();
    }
}
