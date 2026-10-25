package tr.pyro.minions;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;
import tr.pyro.minions.api.events.FisherMinionFishEvent;

public final class PyroMinions extends JavaPlugin {
    final Map<UUID, MinionData> minions = new LinkedHashMap<>();
    NamespacedKey itemKey, levelKey, entityKey, skinKey, nameKey, hologramKey, statisticsKey;
    Economy economy;
    IslandHook islands;
    Menus menus;
    SkinService skins;
    ShopService shop;
    PluginFiles files;
    private MinionStore store;
    TransferJournal transfers;
    private boolean loaded;
    private long tick;
    long interactionTick;
    boolean paperEvents;
    private int appearanceRevision;
    private volatile boolean autosavePending;
    private static final EulerAngle IDLE_ARM = new EulerAngle(Math.toRadians(-15), 0, 0);
    private Set<Material> mineable = Set.of();

    @Override public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "minion-type"); levelKey = new NamespacedKey(this, "minion-level");
        entityKey = new NamespacedKey(this, "minion-id"); skinKey = new NamespacedKey(this, "skin"); nameKey = new NamespacedKey(this, "custom-name");
        hologramKey = new NamespacedKey(this, "hologram");
        statisticsKey = new NamespacedKey(this, "work-statistics");
        try {
            files = PluginFiles.load(this);
            validateConfig();
            skins = new SkinService(this);
            shop = new ShopService(this);
            var registration = getServer().getServicesManager().getRegistration(Economy.class);
            if (registration == null) throw new IllegalStateException("Vault ekonomi sağlayıcısı bulunamadı (ör. EssentialsX).");
            economy = registration.getProvider();
            if (getServer().getPluginManager().isPluginEnabled("SuperiorSkyblock2")) {
                islands = new IslandHook(this); getServer().getPluginManager().registerEvents(islands, this);
            }
            if (data().getBoolean("require-island") && islands == null) throw new IllegalStateException("require-island=true için SuperiorSkyblock2 gerekli.");
            store = new MinionStore(this); transfers = new TransferJournal(this); transfers.recoverWorld(); minions.putAll(store.load()); loaded = true;
            for (var d : minions.values()) d.skin = skins.key(d.skin);
            menus = new Menus(this);
            getServer().getPluginManager().registerEvents(menus, this);
            var listener = new MinionListener(this);
            getServer().getPluginManager().registerEvents(listener, this);
            try {
                Class.forName("io.papermc.paper.event.player.PrePlayerAttackEntityEvent");
                getServer().getPluginManager().registerEvents(new PaperEvents(listener), this);
                paperEvents = true;
            } catch (ClassNotFoundException ignored) { /* Spigot uses Bukkit events. */ }
            getServer().getScheduler().runTaskTimer(this, () -> interactionTick++, 1, 1);
            var commands = new Commands(this);
            Objects.requireNonNull(getCommand("pyrominions")).setExecutor(commands);
            Objects.requireNonNull(getCommand("pyrominions")).setTabCompleter(commands);
            Objects.requireNonNull(getCommand("pyrominionsadmin")).setExecutor(commands);
            Objects.requireNonNull(getCommand("pyrominionsadmin")).setTabCompleter(commands);
            for (var world : Bukkit.getWorlds()) for (var chunk : world.getLoadedChunks()) reconcile(chunk);
            getServer().getScheduler().runTaskTimer(this, this::pulse, 1, 2);
            getServer().getScheduler().runTaskTimer(this, this::saveAutomatic, 600, Math.max(100, data().getLong("save-interval-seconds", 30) * 20));
            getLogger().info("Yapımcı: pyroBuff | PyroMinions " + getDescription().getVersion());
            getLogger().info("PyroMinions hazır. " + minions.size() + " minyon yüklendi.");
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "PyroMinions başlatılamadı; kayıtlar korunuyor.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        if (!loaded) return;
        Bukkit.getOnlinePlayers().forEach(p -> { if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Holder) p.closeInventory(); });
        saveNow();
        for (var d : minions.values()) if (d.stand != null && d.stand.isValid()) d.stand.remove();
    }
    void validateConfig() {
        for (String key : List.of("max-per-player", "max-per-island")) {
            if (!data().isInt(key) || data().getInt(key) < 0)
                throw new IllegalArgumentException(key + " must be a non-negative integer.");
        }
        double price = data().getDouble("feed-price-per-point", .5);
        if (!Double.isFinite(price) || price < 0) throw new IllegalArgumentException("Besleme fiyatı negatif veya sonsuz olamaz.");
        Set<Material> blocks = EnumSet.noneOf(Material.class);
        for (String s : data().getStringList("minions.miner.blocks")) {
            Material material = Material.matchMaterial(s);
            if (material == null || !material.isBlock() || material.isAir()) throw new IllegalArgumentException("Geçersiz madenci bloğu: " + s);
            blocks.add(material);
        }
        mineable = Set.copyOf(blocks);
        for (var type : MinionType.values()) {
            double shopPrice = data().getDouble("minions." + type.key() + ".shop.price");
            int shopLevel = data().getInt("minions." + type.key() + ".shop.level", 1);
            if (!Double.isFinite(shopPrice) || shopPrice < 0 || !hasTier(type, shopLevel)) throw new IllegalArgumentException("Geçersiz market fiyatı/seviyesi: " + type);
            var tiers = data().getConfigurationSection("minions." + type.key() + ".tiers");
            if (tiers == null || tiers.getKeys(false).isEmpty()) throw new IllegalArgumentException("Seviyeler eksik: " + type);
            for (int level = 1; level <= tiers.getKeys(false).size(); level++) {
                var tier = tiers.getConfigurationSection("" + level);
                if (tier == null || tier.getInt("interval-ticks") < 2 || tier.getInt("max-health") < 1) throw new IllegalArgumentException("Geçersiz seviye: " + type + "/" + level);
                double chance = tier.getDouble("double-drop-chance"), upgrade = tier.getDouble("upgrade-price");
                if (!Double.isFinite(chance) || chance < 0 || chance > 100 || !Double.isFinite(upgrade) || upgrade < 0) throw new IllegalArgumentException("Geçersiz şans/fiyat");
                if (type == MinionType.MINER && (tier.getInt("reach") < 1 || tier.getInt("reach") > 9)) throw new IllegalArgumentException("Madenci reach 1..9 olmalı");
                if (type == MinionType.FEEDER && (tier.getInt("range") < 1 || tier.getInt("range") > 32 || tier.getInt("feed-amount") < 1)) throw new IllegalArgumentException("Geçersiz besleme alanı/miktarı");
            }
        }
        var loot = data().getConfigurationSection("minions.fisher.loot");
        if (loot == null || loot.getKeys(false).isEmpty()) throw new IllegalArgumentException("Balıkçı ganimeti boş olamaz.");
        long total = 0;
        for (String key : loot.getKeys(false)) {
            Material m = Material.matchMaterial(key); int weight = loot.getInt(key);
            if (m == null || !m.isItem() || m.isAir() || weight < 1) throw new IllegalArgumentException("Geçersiz balıkçı ganimeti: " + key);
            total += weight;
        }
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("Ganimet ağırlığı fazla.");
        var skins = data().getConfigurationSection("skins");
        if (skins == null || !skins.contains("classic") || skins.getKeys(false).size() > 27) throw new IllegalArgumentException("1..27 skin ve classic skini gerekli.");
        for (String key : skins.getKeys(false)) {
            if (!key.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Geçersiz skin anahtarı");
            SkinService.texture(skins.getString(key + ".head-texture", ""));
            var types = skins.getConfigurationSection(key + ".type-heads");
            if (types != null) for (String type : types.getKeys(false)) SkinService.texture(types.getString(type));
            for (String part : List.of("chest", "legs", "boots")) {
                int color = Integer.parseInt(skins.getString(key + "." + part + "-color", skins.getString(key + ".color", "808080")), 16);
                if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Skin rengi 000000–FFFFFF aralığında olmalı");
            }
        }
        if (this.skins != null) this.skins.clear(); appearanceRevision++;
    }
    boolean hasTier(MinionType type, int level) { return level > 0 && data().isConfigurationSection("minions." + type.key() + ".tiers." + level); }
    ConfigurationSection tier(MinionData d) { return Objects.requireNonNull(data().getConfigurationSection("minions." + d.type.key() + ".tiers." + d.level)); }
    int maxHealth(MinionData d) { return tier(d).getInt("max-health"); }
    int interval(MinionData d) { return tier(d).getInt("interval-ticks"); }
    org.bukkit.configuration.file.YamlConfiguration data() { return files.settings; }
    String lang(String key, String fallback) { return files.language(key, fallback); }
    // The YAML template owns presentation; Vault supplies only the currency text.
    static String moneyText(String formatted) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', formatted));
    }
    String money(double amount) { return moneyText(economy.format(amount)); }
    String typeName(MinionType type) { return lang("labels.minion-types." + type.key(), data().getString("minions." + type.key() + ".name", type.title)); }
    String localizedName(MinionType type, String name) {
        String english = switch (type) { case MINER -> "Miner Minion"; case FISHER -> "Fisher Minion"; case FEEDER -> "Feeder Minion"; };
        if (name == null || name.equals(type.title) || name.equals(english)
            || name.equals(data().getString("minions." + type.key() + ".name"))) return typeName(type);
        return name;
    }
    String skinName(String skin) { return lang("labels.skin-names." + skin, data().getString("skins." + skin + ".name", skin)); }
    private String hologramFormat() {
        String configured = data().getString("hologram-format", "&e%name% &b%level%. Seviye");
        return configured.equals("&e%name% &b%level%. Seviye") || configured.equals("&e%name% &bLevel %level%")
            ? lang("labels.hologram-format", configured) : configured;
    }
    String statisticsUnit(MinionType type) { return lang("labels.statistics-units." + type.key(), switch (type) { case MINER -> "Blok"; case FISHER -> "Balık"; case FEEDER -> "Besleme"; }); }
    static String text(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    void message(org.bukkit.command.CommandSender player, String key, Object... replacements) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < replacements.length; i += 2) values.put(replacements[i].toString(), String.valueOf(replacements[i + 1]));
        player.sendMessage(text(lang("prefix", "&6PyroMinions &8» &f") + PluginFiles.format(lang("messages." + key, key), values)));
    }
    boolean canBuild(UUID owner, Location location) {
        return islands == null ? !data().getBoolean("require-island") : islands.canBuild(owner, location);
    }
    boolean sameIsland(MinionData d, Location location) {
        if (!d.world.equals(location.getWorld().getUID())) return false;
        return islands == null || (d.island != null && d.island.equals(islands.islandAt(location)) && islands.canBuild(d.owner, location));
    }
    boolean canManage(Player player, MinionData d) {
        return d != null && !transfers.pending(player.getUniqueId()) && d.owner.equals(player.getUniqueId())
            && d.world.equals(player.getWorld().getUID()) && d.location().distanceSquared(player.getLocation()) <= 64;
    }
    ItemStack token(MinionType type, int level, String skin, String customName) {
        return token(type, level, skin, customName, 0, true);
    }
    ItemStack token(MinionType type, int level, String skin, String customName, long statistics, boolean hologram) {
        if (statistics < 0) throw new IllegalArgumentException("Negatif minyon istatistiği");
        var item = skins.head(skin, type);
        var meta = item.getItemMeta(); meta.setDisplayName(text("&e" + formatName(localizedName(type, customName), level)));
        meta.setLore(files.lang.getStringList("item-lore").stream().map(s -> text(PluginFiles.format(s, Map.of("level", "" + level, "statistics", "" + statistics, "statistics_unit", statisticsUnit(type))))).toList());
        var pdc = meta.getPersistentDataContainer();
        pdc.set(itemKey, PersistentDataType.STRING, type.key()); pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pdc.set(skinKey, PersistentDataType.STRING, skin);
        pdc.set(statisticsKey, PersistentDataType.LONG, statistics);
        pdc.set(hologramKey, PersistentDataType.BYTE, (byte)(hologram ? 1 : 0));
        if (customName != null) pdc.set(nameKey, PersistentDataType.STRING, customName);
        item.setItemMeta(meta); return item;
    }
    MinionData fromEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (id == null) return null;
        try { return minions.get(UUID.fromString(id)); } catch (IllegalArgumentException e) { return null; }
    }
    boolean tagged(Entity entity) { return entity.getPersistentDataContainer().has(entityKey, PersistentDataType.STRING); }
    void reconcile(Chunk chunk) {
        Map<UUID, ArmorStand> found = new HashMap<>();
        for (Entity entity : chunk.getEntities()) {
            if (!tagged(entity)) continue;
            var d = fromEntity(entity);
            if (!(entity instanceof ArmorStand stand) || d == null || !d.world.equals(chunk.getWorld().getUID())
                || d.x >> 4 != chunk.getX() || d.z >> 4 != chunk.getZ() || found.containsKey(d.id)) { entity.remove(); continue; }
            boolean reacquired = d.stand == null || !d.stand.isValid() || !d.stand.getUniqueId().equals(stand.getUniqueId());
            found.put(d.id, stand); d.stand = stand;
            if (reacquired || d.nextAction < 0) startWorkCycle(d);
            update(d);
        }
        for (var d : minions.values()) if (d.world.equals(chunk.getWorld().getUID()) && d.x >> 4 == chunk.getX() && d.z >> 4 == chunk.getZ() && !found.containsKey(d.id)) spawn(d);
    }
    void spawn(MinionData d) {
        if (!d.chunkLoaded() || d.stand != null && d.stand.isValid()) return;
        startWorkCycle(d);
        d.stand = d.location().getWorld().spawn(d.location(), ArmorStand.class, stand -> {
            stand.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, d.id.toString());
            stand.setGravity(false); stand.setInvulnerable(true); stand.setSilent(true); stand.setPersistent(true);
            stand.setCanPickupItems(false); stand.setSmall(true); stand.setArms(true); stand.setBasePlate(false);
            stand.setCollidable(false);
            for (EquipmentSlot slot : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                for (ArmorStand.LockType lock : ArmorStand.LockType.values()) stand.addEquipmentLock(slot, lock);
            }
        });
        update(d);
    }
    void update(MinionData d) {
        if (d.stand == null || !d.stand.isValid()) return;
        d.stand.setRotation(d.yaw(), 0);
        d.stand.setCustomName(text(PluginFiles.format(hologramFormat(), Map.of("name", localizedName(d.type, d.name), "level", "" + d.level))));
        d.stand.setCustomNameVisible(d.hologram);
        UUID entity = d.stand.getUniqueId();
        if (entity.equals(d.appearanceEntity) && Objects.equals(d.skin, d.appearanceSkin)
            && d.appearanceRevision == appearanceRevision) return;
        var equipment = d.stand.getEquipment();
        equipment.setHelmet(skins.head(d.skin, d.type));
        equipment.setChestplate(leather(Material.LEATHER_CHESTPLATE, skins.color(d.skin, "chest")));
        equipment.setLeggings(leather(Material.LEATHER_LEGGINGS, skins.color(d.skin, "legs")));
        equipment.setBoots(leather(Material.LEATHER_BOOTS, skins.color(d.skin, "boots")));
        equipment.setItemInMainHand(new ItemStack(d.type.tool));
        d.appearanceEntity = entity; d.appearanceSkin = d.skin; d.appearanceRevision = appearanceRevision;
        d.stand.setRightArmPose(IDLE_ARM); d.armMoving = false;
    }
    private ItemStack leather(Material type, int rgb) {
        var item = new ItemStack(type); var meta = (LeatherArmorMeta) item.getItemMeta(); meta.setColor(Color.fromRGB(rgb)); item.setItemMeta(meta); return item;
    }
    private String formatName(String name, int level) { return PluginFiles.format(lang("labels.level-format", "%name% %level%. Seviye"), Map.of("name", name, "level", "" + level)); }
    String displayName(MinionData d) { return formatName(localizedName(d.type, d.name), d.level); }
    void remove(MinionData d) { minions.remove(d.id); if (d.stand != null && d.stand.isValid()) d.stand.remove(); }
    boolean saveNow() {
        if (!loaded) return false;
        try { store.save(minions.values()); return true; }
        catch (IOException e) { getLogger().log(java.util.logging.Level.SEVERE, "Minyonlar kaydedilemedi!", e); return false; }
    }
    private void saveAutomatic() {
        if (!loaded || autosavePending) return;
        // Copy Bukkit-owned mutable records on the server thread; serialize and write the detached snapshot off-thread.
        var snapshot = MinionStore.snapshot(minions.values());
        long generation = store.reserveSave();
        autosavePending = true;
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try { store.saveSnapshot(snapshot, generation); }
            catch (IOException e) { getLogger().log(java.util.logging.Level.SEVERE, "Automatic minion save failed!", e); }
            finally { autosavePending = false; }
        });
    }
    private void idleArm(MinionData d) {
        if (!d.armMoving) return;
        if (d.stand != null && d.stand.isValid()) d.stand.setRightArmPose(IDLE_ARM);
        d.armMoving = false;
    }
    private void pulse() {
        tick += 2;
        for (var d : List.copyOf(minions.values())) {
            if (!d.chunkLoaded()) continue;
            if (d.stand == null || !d.stand.isValid()) { reconcile(d.location().getChunk()); continue; }
            if (d.swingUntil > tick) {
                d.stand.setRightArmPose(new EulerAngle(Math.toRadians(-30 - 65 * Math.sin((d.swingUntil - tick) * Math.PI / 12)), 0, 0));
                d.armMoving = true;
            } else idleArm(d);
            if (!workDue(d)) continue;
            if (d.hunger <= 0) continue;
            var owner = Bukkit.getPlayer(d.owner);
            // An online owner allows protection plugins to evaluate mining with the real player.
            if (owner == null || !canBuild(d.owner, d.location()) || !sameIsland(d, d.location())) continue;
            performWork(d, owner);
        }
    }
    private boolean performWork(MinionData d, Player owner) {
        boolean worked = switch (d.type) { case MINER -> mine(d, owner); case FISHER -> fish(d); case FEEDER -> feedOthers(d); };
        if (worked) {
            d.hunger = Math.max(0, d.hunger - 1); d.swingUntil = tick + 12;
        } else if (d.type == MinionType.MINER) {
            d.swingUntil = 0;
            idleArm(d);
        }
        return worked;
    }
    private void startWorkCycle(MinionData d) {
        // Add a stable phase only to the initial wait. Never shorten a full work interval.
        int slots = Math.max(1, interval(d) / 2);
        d.nextAction = tick + interval(d) + 2L * Math.floorMod(d.id.hashCode(), slots);
    }
    private void resetWorkCycle(MinionData d) { d.nextAction = tick + interval(d); }
    private boolean workDue(MinionData d) {
        if (d.nextAction < 0) { resetWorkCycle(d); return false; }
        if (tick < d.nextAction) return false;
        resetWorkCycle(d); return true;
    }
    private boolean mine(MinionData d, Player owner) {
        int reach = tier(d).getInt("reach", d.level);
        boolean worked = false;
        var world = d.location().getWorld();
        int dx = d.facing.getModX(), dz = d.facing.getModZ();
        for (int distance = 1; distance <= reach; distance++) {
            int x = d.x + dx * distance, z = d.z + dz * distance;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            var block = world.getBlockAt(x, d.y, z);
            if (!mineable.contains(block.getType()) || block.getState() instanceof InventoryHolder || !sameIsland(d, block.getLocation())) continue;
            var event = new BlockBreakEvent(block, owner); Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || !mineable.contains(block.getType()) || block.getState() instanceof InventoryHolder) continue;
            List<ItemStack> drops = event.isDropItems() ? new ArrayList<>(block.getDrops(new ItemStack(Material.NETHERITE_PICKAXE))) : List.of();
            block.setType(Material.AIR, true);
            d.statistics = Rules.addStatistics(d.statistics, 1);
            output(d, drops); // No exp orbs are spawned, including ore XP.
            worked = true;
        }
        return worked;
    }
    private boolean fish(MinionData d) {
        var world = d.location().getWorld(); int x = d.x + d.facing.getModX(), z = d.z + d.facing.getModZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
        var water = world.getBlockAt(x, d.y, z);
        if (water.getType() != Material.WATER) water = world.getBlockAt(x, d.y - 1, z);
        if (water.getType() != Material.WATER || !sameIsland(d, water.getLocation())) return false;
        var loot = Objects.requireNonNull(data().getConfigurationSection("minions.fisher.loot"));
        int total = loot.getKeys(false).stream().mapToInt(loot::getInt).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (String key : loot.getKeys(false)) {
            roll -= loot.getInt(key);
            if (roll < 0) {
                var event = new FisherMinionFishEvent(d.id, d.owner, d.location(), List.of(new ItemStack(Objects.requireNonNull(Material.matchMaterial(key)))));
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return false;
                var drops = event.getDrops().stream().filter(Objects::nonNull).filter(i -> !i.getType().isAir() && i.getAmount() > 0).toList();
                d.statistics = Rules.addStatistics(d.statistics, output(d, drops, true));
                break;
            }
        }
        world.spawnParticle(Particle.SPLASH, water.getLocation().add(.5, 1, .5), 5, .15, .1, .15, 0);
        return true;
    }
    private boolean feedOthers(MinionData d) {
        int range = tier(d).getInt("range"), amount = tier(d).getInt("feed-amount"); boolean fed = false;
        for (var target : minions.values()) {
            // Feeders cannot recharge one another; energy is transferred, never created.
            if (target == d || target.type == MinionType.FEEDER || !target.owner.equals(d.owner) || !target.world.equals(d.world) || !target.chunkLoaded()) continue;
            if (target.location().distanceSquared(d.location()) > range * range || !sameIsland(d, target.location())) continue;
            int transfer = Rules.transfer(d.hunger - 1, maxHealth(target) - target.hunger, amount);
            if (transfer == 0) continue;
            target.hunger += transfer; d.hunger -= transfer; update(target); fed = true;
            d.statistics = Rules.addStatistics(d.statistics, 1);
        }
        return fed;
    }
    private long output(MinionData d, Collection<ItemStack> original) {
        return output(d, original, false);
    }
    private long output(MinionData d, Collection<ItemStack> original, boolean countFishOnly) {
        long produced = 0;
        boolean twice = Rules.doubleDrop(ThreadLocalRandom.current().nextDouble(100), tier(d).getDouble("double-drop-chance"));
        var chest = d.chestLocation(); Inventory inventory = null;
        if (chest != null) inventory = resolveChest(d, chest);
        for (var item : original) for (int copy = 0; copy < (twice ? 2 : 1); copy++) {
            var stack = item.clone();
            if (!countFishOnly || Set.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH).contains(stack.getType()))
                produced = Rules.addStatistics(produced, stack.getAmount());
            Collection<ItemStack> remaining = inventory == null ? List.of(stack) : inventory.addItem(stack).values();
            for (var drop : remaining) d.location().getWorld().dropItemNaturally(d.location().add(0, .4, 0), drop);
        }
        return produced;
    }
    Inventory resolveChest(MinionData d, Location location) {
        if (!safeChestHalf(d, location) || !(location.getBlock().getState() instanceof Chest chest)) return null;
        var shape = (org.bukkit.block.data.type.Chest)chest.getBlockData();
        if (shape.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            var facing = shape.getFacing(); int sign = shape.getType() == org.bukkit.block.data.type.Chest.Type.LEFT ? 1 : -1;
            var other = location.clone().add(-facing.getModZ() * sign, 0, facing.getModX() * sign);
            if (!safeChestHalf(d, other) || !(other.getBlock().getState() instanceof Chest)) return null;
            var inventory = chest.getInventory();
            if (!(inventory.getHolder() instanceof DoubleChest doubleChest) || inventory.getSize() != 54) return null;
            if (!(doubleChest.getLeftSide() instanceof Chest left) || !(doubleChest.getRightSide() instanceof Chest right)
                || !safeChestHalf(d, left.getLocation()) || !safeChestHalf(d, right.getLocation())) return null;
            return inventory;
        }
        return chest.getInventory();
    }
    private boolean safeChestHalf(MinionData d, Location location) {
        return location.getWorld() != null && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
            && sameIsland(d, location) && location.distanceSquared(d.location()) <= Math.pow(data().getDouble("chest-link-distance", 8), 2);
    }
    void handFeed(Player player, MinionData d) {
        if (!canManage(player, d)) { message(player, "not-owner"); return; }
        var item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.COOKED_BEEF) return;
        if (d.hunger >= maxHealth(d)) { message(player, "already-full"); return; }
        int amount = Math.min(data().getInt("hand-feed-health", 50), maxHealth(d) - d.hunger);
        var remainder = item.clone(); remainder.setAmount(remainder.getAmount() - 1); player.getInventory().setItemInMainHand(remainder);
        d.hunger += amount; update(d); saveNow(); message(player, "hand-fed", "amount", amount, "health", d.hunger, "max_health", maxHealth(d));
    }
    boolean charge(Player player, double cost) {
        if (!Double.isFinite(cost) || cost < 0) return false;
        if (cost == 0) return true;
        if (!economy.has(player, cost)) { message(player, "insufficient-money", "price", money(cost)); return false; }
        var result = economy.withdrawPlayer(player, cost);
        if (!result.transactionSuccess()) { message(player, "payment-failed", "reason", result.errorMessage); return false; }
        return true;
    }
    void feed(Player player, MinionData d) {
        int missing = maxHealth(d) - d.hunger;
        if (missing <= 0) { message(player, "already-full"); return; }
        if (charge(player, Rules.feedingPrice(missing, data().getDouble("feed-price-per-point", .5)))) {
            d.hunger = maxHealth(d); update(d); saveNow(); message(player, "fed");
        }
    }
    void upgrade(Player player, MinionData d) {
        if (!hasTier(d.type, d.level + 1)) { message(player, "max-level"); return; }
        if (charge(player, tier(d).getDouble("upgrade-price"))) {
            d.level++; d.cursor = 0; resetWorkCycle(d); update(d); saveNow();
            message(player, "upgraded", "level", d.level);
        }
    }
}
