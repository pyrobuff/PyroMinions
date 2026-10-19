package tr.pyro.minions;

import java.util.Locale;
import org.bukkit.Material;

public enum MinionType {
    FEEDER("Besleyici Minyon", Material.GOLDEN_APPLE),
    MINER("Madenci Minyon", Material.IRON_PICKAXE),
    FISHER("Balıkçı Minyon", Material.FISHING_ROD);
    public final String title;
    public final Material tool;
    MinionType(String title, Material tool) { this.title = title; this.tool = tool; }
    public static MinionType parse(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "feeder", "besleyici" -> FEEDER;
            case "miner", "madenci" -> MINER;
            case "fisher", "balikci", "balıkçı" -> FISHER;
            default -> throw new IllegalArgumentException("Tür: feeder, miner veya fisher olmalı.");
        };
    }
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
