package tr.pyro.minions;

/** Pure game rules, also used by the standalone regression tests. */
public final class Rules {
    private Rules() {}
    public static long addStatistics(long current, long amount) {
        if (current < 0 || amount < 0) throw new IllegalArgumentException("Statistics cannot be negative");
        return amount > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + amount;
    }
    public static int[] offsets(int width) {
        if (width < 1 || width > 9 || width % 2 == 0) throw new IllegalArgumentException("Width must be odd (1..9)");
        int[] result = new int[width];
        for (int i = 0; i < width; i++) result[i] = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -i / 2);
        return result;
    }
    public static double feedingPrice(int missing, double perPoint) {
        if (missing < 0 || !Double.isFinite(perPoint) || perPoint < 0) throw new IllegalArgumentException("Invalid feeding price");
        return Math.ceil(missing * perPoint * 100.0) / 100.0;
    }
    public static int transfer(int available, int missing, int limit) {
        return Math.max(0, Math.min(Math.min(available, missing), limit));
    }
    public static boolean doubleDrop(double roll, double chancePercent) {
        return roll >= 0 && roll < Math.max(0, Math.min(100, chancePercent));
    }
}
