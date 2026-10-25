package tr.pyro.minions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

final class MinionListener implements Listener {
    private final PyroMinions plugin;
    record Pending(UUID minion, long expires) {}
    final Map<UUID, Pending> links = new HashMap<>();
    final Map<UUID, Pending> renames = new ConcurrentHashMap<>();
    record Interaction(UUID minion, long tick) {}
    final Map<UUID, Interaction> interactions = new HashMap<>();
    MinionListener(PyroMinions plugin) { this.plugin = plugin; plugin.menus.listener = this; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND && event.getItem() != null && event.getItem().hasItemMeta()
            && event.getItem().getItemMeta().getPersistentDataContainer().has(plugin.itemKey, PersistentDataType.STRING)) {
            event.setCancelled(true); return;
        }
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var player = event.getPlayer(); var block = event.getClickedBlock(); if (block == null) return;
        if (plugin.transfers.pending(player.getUniqueId())) { event.setCancelled(true); return; }
        Pending pending = links.remove(player.getUniqueId());
        if (pending != null) {
            event.setCancelled(true);
            var d = plugin.minions.get(pending.minion());
            if (pending.expires() < System.currentTimeMillis() || !plugin.canManage(player, d)) { plugin.message(player, "link-expired"); return; }
            if (!(block.getState() instanceof Chest) || plugin.resolveChest(d, block.getLocation()) == null) { plugin.message(player, "invalid-chest"); return; }
            double range = plugin.data().getDouble("chest-link-distance", 8);
            if (block.getLocation().distanceSquared(d.location()) > range * range) { plugin.message(player, "chest-too-far", "range", range); return; }
            d.chestWorld = block.getWorld().getUID(); d.chestX = block.getX(); d.chestY = block.getY(); d.chestZ = block.getZ();
            plugin.saveNow(); plugin.message(player, "linked"); return;
        }
        var item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        var tags = item.getItemMeta().getPersistentDataContainer(); String type = tags.get(plugin.itemKey, PersistentDataType.STRING);
        if (type == null) return;
        event.setCancelled(true);
        if (!player.hasPermission("pyrominions.use")) { plugin.message(player, "placement-permission"); return; }
        if (event.getBlockFace() != BlockFace.UP) { plugin.message(player, "place-on-top"); return; }
        var target = block.getRelative(BlockFace.UP); var location = target.getLocation();
        if (!target.isEmpty() || !block.getType().isSolid() || !plugin.canBuild(player.getUniqueId(), location)) { plugin.message(player, "invalid-location"); return; }
        if (plugin.minions.values().stream().anyMatch(d -> d.world.equals(location.getWorld().getUID()) && d.x == target.getX() && d.y == target.getY() && d.z == target.getZ())) {
            plugin.message(player, "occupied"); return;
        }
        UUID island = plugin.islands == null ? null : plugin.islands.islandAt(location);
        if (limitReached(player, island)) return;
        // Give existing build-protection listeners a placement check before spawning an entity.
        var check = new BlockPlaceEvent(target, target.getState(), block, item, player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(check);
        if (check.isCancelled() || !check.canBuild()) { plugin.message(player, "protected"); return; }
        // Other event listeners can place minions synchronously; check the current totals again.
        if (limitReached(player, island)) return;
        try {
            var minionType = MinionType.parse(type); int level = tags.getOrDefault(plugin.levelKey, PersistentDataType.INTEGER, 1);
            if (!plugin.hasTier(minionType, level)) throw new IllegalArgumentException("invalid-minion-level");
            var d = new MinionData(UUID.randomUUID(), player.getUniqueId(), minionType, location);
            d.level = level; d.hunger = plugin.maxHealth(d);
            d.statistics = tags.getOrDefault(plugin.statisticsKey, PersistentDataType.LONG, 0L);
            if (d.statistics < 0) throw new IllegalArgumentException("invalid-statistics");
            d.skin = tags.getOrDefault(plugin.skinKey, PersistentDataType.STRING, "classic");
            d.name = tags.getOrDefault(plugin.nameKey, PersistentDataType.STRING, plugin.typeName(d.type));
            d.skin = plugin.skins.key(d.skin);
            d.hologram = tags.getOrDefault(plugin.hologramKey, PersistentDataType.BYTE, (byte)1) != 0;
            d.facing = cardinal(player.getFacing().getOppositeFace());
            if (plugin.islands != null) d.island = plugin.islands.islandAt(location);
            plugin.minions.put(d.id, d);
            try {
                plugin.spawn(d);
                if (d.stand == null || !d.stand.isValid()) throw new IllegalArgumentException("spawn-failed");
            } catch (RuntimeException failure) { plugin.remove(d); throw failure; }
            // Consume in creative too: one token always represents one minion.
            if (!plugin.transfers.place(player, d, item)) { plugin.remove(d); return; }
            plugin.message(player, "placed", "type", plugin.typeName(d.type));
        } catch (IllegalArgumentException e) { plugin.message(player, "placement-failed", "reason", plugin.lang("messages." + e.getMessage(), e.getMessage())); }
    }
    boolean isMinion(Entity entity) { return plugin.tagged(entity); }
    private boolean limitReached(Player player, UUID island) {
        int personal = PlacementLimits.playerLimit(player, plugin.data().getInt("max-per-player", 20));
        if (PlacementLimits.playerCount(plugin.minions.values(), player.getUniqueId()) >= personal) {
            plugin.message(player, "player-limit", "limit", personal); return true;
        }
        int shared = plugin.data().getInt("max-per-island", 20);
        if (island != null && PlacementLimits.islandCount(plugin.minions.values(), island) >= shared) {
            plugin.message(player, "island-limit", "limit", shared); return true;
        }
        return false;
    }
    private BlockFace cardinal(BlockFace facing) { return switch (facing) { case NORTH, SOUTH, EAST, WEST -> facing; default -> BlockFace.NORTH; }; }
    @EventHandler(ignoreCancelled = true)
    public void dispense(BlockDispenseEvent event) {
        if (event.getItem().hasItemMeta() && event.getItem().getItemMeta().getPersistentDataContainer().has(plugin.itemKey, PersistentDataType.STRING)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void open(PlayerInteractEntityEvent event) {
        if (!plugin.tagged(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        var d = plugin.fromEntity(event.getRightClicked());
        if (!plugin.canManage(event.getPlayer(), d)) { plugin.message(event.getPlayer(), "not-owner"); return; }
        var player = event.getPlayer(); long tick = plugin.interactionTick;
        var last = interactions.put(player.getUniqueId(), new Interaction(d.id, tick));
        if (last != null && last.minion().equals(d.id) && last.tick() == tick) return;
        if (player.getInventory().getItemInMainHand().getType() == Material.COOKED_BEEF) { plugin.handFeed(player, d); return; }
        plugin.menus.main(event.getPlayer(), d);
    }
    // Armor stands dispatch INTERACT_AT through a separate HandlerList.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void openAt(PlayerInteractAtEntityEvent event) { open(event); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void manipulate(PlayerArmorStandManipulateEvent event) {
        if (plugin.tagged(event.getRightClicked())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (!plugin.tagged(event.getEntity())) return;
        event.setCancelled(true);
    }
    void pickup(Player player, Entity attacked) {
        var d = plugin.fromEntity(attacked);
        if (d == null || !d.owner.equals(player.getUniqueId()) || !plugin.canManage(player, d)) { plugin.message(player, "owner-pickup-only"); return; }
        if (d.hunger != plugin.maxHealth(d)) { plugin.message(player, "pickup-not-full"); return; }
        var item = plugin.token(d.type, d.level, d.skin, d.name, d.statistics, d.hologram);
        if (player.getInventory().firstEmpty() < 0) { plugin.message(player, "inventory-full"); return; }
        if (plugin.transfers.pickup(player, d, item)) plugin.message(player, "picked-up");
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakSupport(BlockBreakEvent event) {
        var b = event.getBlock();
        if (plugin.minions.values().stream().anyMatch(d -> d.world.equals(b.getWorld().getUID()) && d.x == b.getX() && d.z == b.getZ() && (d.y == b.getY() + 1 || d.y == b.getY()))) {
            event.setCancelled(true); plugin.message(event.getPlayer(), "remove-first");
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(this::supportsMinion)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void pistonRetract(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(this::supportsMinion)) event.setCancelled(true); }
    private boolean supportsMinion(Block b) { return plugin.minions.values().stream().anyMatch(d -> d.world.equals(b.getWorld().getUID()) && d.x == b.getX() && d.z == b.getZ() && d.y == b.getY() + 1); }
    @EventHandler(ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) { event.blockList().removeIf(this::supportsMinion); }
    @EventHandler(ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent event) { event.blockList().removeIf(this::supportsMinion); }
    @EventHandler
    public void chunk(ChunkLoadEvent event) { plugin.reconcile(event.getChunk()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void rename(AsyncPlayerChatEvent event) {
        if (plugin.paperEvents) return;
        if (!renames.containsKey(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        rename(event.getPlayer(), event.getMessage());
    }
    void rename(Player sender, String input) {
        Pending pending = renames.remove(sender.getUniqueId()); if (pending == null) return;
        String text = input.strip();
        Bukkit.getScheduler().runTask(plugin, () -> {
            var player = sender; var d = plugin.minions.get(pending.minion());
            if (pending.expires() < System.currentTimeMillis() || !plugin.canManage(player, d)) { plugin.message(player, "rename-expired"); return; }
            if (text.equalsIgnoreCase(plugin.lang("labels.cancel-word", "iptal"))) { plugin.message(player, "rename-cancelled"); return; }
            if (text.isBlank() || text.codePointCount(0, text.length()) > 32 || text.chars().anyMatch(c -> Character.isISOControl(c) || c == '§' || c == '&')) {
                plugin.message(player, "rename-invalid"); return;
            }
            d.name = text; plugin.update(d); plugin.saveNow(); plugin.message(player, "renamed");
        });
    }
    // Invulnerable armor stands need the swing event on Spigot, where no pre-attack event exists.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent event) {
        if (plugin.paperEvents || event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        var player = event.getPlayer();
        double reach = player.getGameMode() == GameMode.CREATIVE ? 5 : 3;
        var eye = player.getEyeLocation(); var direction = eye.getDirection();
        var hit = player.getWorld().rayTraceEntities(eye, direction, reach, entity -> entity instanceof org.bukkit.entity.LivingEntity && entity != player);
        if (hit == null || hit.getHitEntity() == null || !plugin.tagged(hit.getHitEntity())) return;
        var wall = player.getWorld().rayTraceBlocks(eye, direction, reach, FluidCollisionMode.NEVER, true);
        if (wall != null && wall.getHitPosition().distanceSquared(eye.toVector()) < hit.getHitPosition().distanceSquared(eye.toVector())) return;
        pickup(player, hit.getHitEntity());
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void join(PlayerJoinEvent event) { plugin.transfers.recoverPlayer(event.getPlayer()); }
    @EventHandler
    public void quit(PlayerQuitEvent event) { links.remove(event.getPlayer().getUniqueId()); renames.remove(event.getPlayer().getUniqueId()); interactions.remove(event.getPlayer().getUniqueId()); }
}
