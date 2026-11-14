package net.atlasmc;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** /sat ve spawner satışı için eşya başına satış fiyatları. Config'te "sell.MATERYAL: fiyat" ile ezilebilir. */
public final class Prices {
    private Prices() {}

    private static final Map<Material, Double> D = new HashMap<>();

    private static void p(String name, double v) {
        Material m = Material.matchMaterial(name);
        if (m != null) D.put(m, v);
    }

    static {
        // Madenler
        p("COAL", 8); p("CHARCOAL", 6); p("IRON_INGOT", 20); p("GOLD_INGOT", 30); p("COPPER_INGOT", 6);
        p("DIAMOND", 150); p("EMERALD", 120); p("LAPIS_LAZULI", 6); p("REDSTONE", 5); p("QUARTZ", 8);
        p("NETHERITE_SCRAP", 400); p("NETHERITE_INGOT", 1800); p("ANCIENT_DEBRIS", 450);
        p("RAW_IRON", 15); p("RAW_GOLD", 22); p("RAW_COPPER", 4); p("AMETHYST_SHARD", 5);
        // Bloklar
        p("COBBLESTONE", 1); p("STONE", 1); p("DEEPSLATE", 1); p("COBBLED_DEEPSLATE", 1); p("DIRT", 0.5);
        p("SAND", 1); p("GRAVEL", 1); p("NETHERRACK", 0.5); p("END_STONE", 2); p("OBSIDIAN", 8);
        p("GLASS", 2); p("GLOWSTONE", 4); p("GLOWSTONE_DUST", 3); p("CLAY_BALL", 1);
        p("OAK_LOG", 3); p("SPRUCE_LOG", 3); p("BIRCH_LOG", 3); p("JUNGLE_LOG", 3); p("ACACIA_LOG", 3);
        p("DARK_OAK_LOG", 3); p("MANGROVE_LOG", 3); p("CHERRY_LOG", 3); p("STICK", 0.5);
        // Mob düşenleri
        p("ROTTEN_FLESH", 2); p("BONE", 3); p("ARROW", 2); p("STRING", 3); p("SPIDER_EYE", 4);
        p("GUNPOWDER", 6); p("BLAZE_ROD", 15); p("ENDER_PEARL", 25); p("LEATHER", 4); p("FEATHER", 2);
        p("SLIME_BALL", 6); p("MAGMA_CREAM", 8); p("GHAST_TEAR", 80); p("PHANTOM_MEMBRANE", 15);
        p("SHULKER_SHELL", 100); p("NAUTILUS_SHELL", 40); p("PRISMARINE_SHARD", 3); p("PRISMARINE_CRYSTALS", 4);
        p("INK_SAC", 2); p("GLOW_INK_SAC", 4); p("HEART_OF_THE_SEA", 400); p("EGG", 1);
        p("WITHER_SKELETON_SKULL", 600); p("NETHER_STAR", 3000); p("SCUTE", 20); p("BREEZE_ROD", 20);
        // Çiftlik / yiyecek
        p("WHEAT", 3); p("CARROT", 3); p("POTATO", 3); p("BEETROOT", 3); p("MELON_SLICE", 1);
        p("PUMPKIN", 3); p("SUGAR_CANE", 2); p("SUGAR", 2); p("BEEF", 5); p("PORKCHOP", 5);
        p("CHICKEN", 4); p("MUTTON", 4); p("RABBIT", 4); p("COD", 3); p("SALMON", 4);
        p("COOKED_BEEF", 9); p("COOKED_PORKCHOP", 9); p("COOKED_CHICKEN", 7); p("COOKED_MUTTON", 8);
        p("COOKED_SALMON", 7); p("COOKED_COD", 6); p("BREAD", 6); p("BAKED_POTATO", 5); p("APPLE", 4);
        p("GOLDEN_CARROT", 15); p("GOLDEN_APPLE", 120); p("HONEY_BOTTLE", 8); p("KELP", 1); p("BAMBOO", 1);
        p("CACTUS", 1); p("NETHER_WART", 5); p("SWEET_BERRIES", 2); p("GLOW_BERRIES", 2); p("COCOA_BEANS", 2);
        p("POPPY", 1); p("PUMPKIN_PIE", 10);
        // PvP / özel
        p("TOTEM_OF_UNDYING", 300); p("EXPERIENCE_BOTTLE", 40); p("END_CRYSTAL", 40); p("TNT", 20);
        p("ENDER_EYE", 40); p("SPECTRAL_ARROW", 5); p("RESPAWN_ANCHOR", 60); p("SHIELD", 20); p("SHULKER_BOX", 200);
    }

    public static double get(AtlasMC plugin, Material m) {
        return plugin.getConfig().getDouble("sell." + m.name(), D.getOrDefault(m, 0.0));
    }

    public static Set<Material> orderable() {
        return D.keySet();
    }
}
