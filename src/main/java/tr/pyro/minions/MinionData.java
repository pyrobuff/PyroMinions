package tr.pyro.minions;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;

public final class MinionData {
    public final UUID id, owner, world;
    public final MinionType type;
    public final int x, y, z;
    public UUID island;
    public int level = 1, hunger, cursor;
    public long statistics;
    public BlockFace facing = BlockFace.NORTH;
    public String name, skin = "classic";
    public boolean hologram = true;
    public UUID chestWorld;
    public int chestX, chestY, chestZ;
    public transient ArmorStand stand;
    public transient long nextAction = -1;
    public transient long swingUntil;
    transient boolean armMoving;
    transient UUID appearanceEntity;
    transient String appearanceSkin;
    transient int appearanceRevision = -1;
    public MinionData(UUID id, UUID owner, MinionType type, Location loc) {
        this(id, owner, type, loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    public MinionData(UUID id, UUID owner, MinionType type, UUID world, int x, int y, int z) {
        this.id = id; this.owner = owner; this.type = type;
        this.world = world; this.x = x; this.y = y; this.z = z;
        name = type.title;
    }
    public Location location() { return new Location(Bukkit.getWorld(world), x + .5, y, z + .5); }
    public boolean chunkLoaded() {
        var w = Bukkit.getWorld(world); return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }
    public Location chestLocation() {
        var w = chestWorld == null ? null : Bukkit.getWorld(chestWorld);
        return w == null ? null : new Location(w, chestX, chestY, chestZ);
    }
    public float yaw() {
        return switch (facing) { case SOUTH -> 0; case WEST -> 90; case NORTH -> 180; case EAST -> 270; default -> 0; };
    }
}
