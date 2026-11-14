package net.atlasmc;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Satış fiyatları. Oyundaki HER eşyanın bir fiyatı vardır:
 *  1) config "sell.MATERYAL" varsa o
 *  2) aşağıdaki tablo
 *  3) isme göre otomatik tahmin (alet/zırh/blok/boya/ahşap/maden bloğu ...)
 * Büyülü eşyalar: temel fiyat + büyü değeri. Hasarlı eşyalar: dayanıklılığa göre indirimli.
 * Shulker kutusu ve bundle içindeki eşyalar da fiyata eklenir.
 */
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
        p("IRON_NUGGET", 2); p("GOLD_NUGGET", 3);
        // Bloklar
        p("COBBLESTONE", 1); p("STONE", 1); p("DEEPSLATE", 1); p("COBBLED_DEEPSLATE", 1); p("DIRT", 0.5);
        p("GRASS_BLOCK", 1); p("SAND", 1); p("GRAVEL", 1); p("NETHERRACK", 0.5); p("END_STONE", 2);
        p("OBSIDIAN", 8); p("CRYING_OBSIDIAN", 20); p("GLASS", 2); p("GLOWSTONE", 4); p("GLOWSTONE_DUST", 3);
        p("CLAY_BALL", 1); p("CLAY", 4); p("STICK", 0.5); p("SPONGE", 40); p("WET_SPONGE", 40);
        // Mob düşenleri
        p("ROTTEN_FLESH", 2); p("BONE", 3); p("BONE_MEAL", 1); p("ARROW", 2); p("STRING", 3); p("SPIDER_EYE", 4);
        p("GUNPOWDER", 6); p("BLAZE_ROD", 15); p("BLAZE_POWDER", 8); p("ENDER_PEARL", 25); p("LEATHER", 4);
        p("FEATHER", 2); p("SLIME_BALL", 6); p("MAGMA_CREAM", 8); p("GHAST_TEAR", 80); p("PHANTOM_MEMBRANE", 15);
        p("SHULKER_SHELL", 100); p("NAUTILUS_SHELL", 40); p("PRISMARINE_SHARD", 3); p("PRISMARINE_CRYSTALS", 4);
        p("INK_SAC", 2); p("GLOW_INK_SAC", 4); p("HEART_OF_THE_SEA", 400); p("EGG", 1);
        p("WITHER_SKELETON_SKULL", 600); p("NETHER_STAR", 3000); p("SCUTE", 20); p("BREEZE_ROD", 20);
        p("DRAGON_BREATH", 100); p("RABBIT_FOOT", 30); p("RABBIT_HIDE", 3); p("FERMENTED_SPIDER_EYE", 8);
        // Çiftlik / yiyecek
        p("WHEAT", 3); p("CARROT", 3); p("POTATO", 3); p("BEETROOT", 3); p("MELON_SLICE", 1);
        p("PUMPKIN", 3); p("SUGAR_CANE", 2); p("SUGAR", 2); p("BEEF", 5); p("PORKCHOP", 5);
        p("CHICKEN", 4); p("MUTTON", 4); p("RABBIT", 4); p("COD", 3); p("SALMON", 4);
        p("COOKED_BEEF", 9); p("COOKED_PORKCHOP", 9); p("COOKED_CHICKEN", 7); p("COOKED_MUTTON", 8);
        p("COOKED_SALMON", 7); p("COOKED_COD", 6); p("BREAD", 6); p("BAKED_POTATO", 5); p("APPLE", 4);
        p("GOLDEN_CARROT", 15); p("GOLDEN_APPLE", 120); p("ENCHANTED_GOLDEN_APPLE", 2500);
        p("HONEY_BOTTLE", 8); p("KELP", 1); p("BAMBOO", 1); p("CACTUS", 1); p("NETHER_WART", 5);
        p("SWEET_BERRIES", 2); p("GLOW_BERRIES", 2); p("COCOA_BEANS", 2); p("POPPY", 1); p("PUMPKIN_PIE", 10);
        p("CAKE", 25); p("COOKIE", 2); p("MUSHROOM_STEW", 8); p("SUSPICIOUS_STEW", 12);
        // PvP / özel / değerli
        p("TOTEM_OF_UNDYING", 300); p("EXPERIENCE_BOTTLE", 40); p("END_CRYSTAL", 40); p("TNT", 20);
        p("ENDER_EYE", 40); p("SPECTRAL_ARROW", 5); p("RESPAWN_ANCHOR", 60); p("SHIELD", 20); p("SHULKER_BOX", 200);
        p("ELYTRA", 3000); p("DRAGON_EGG", 8000); p("BEACON", 6000); p("TRIDENT", 600); p("MACE", 2000);
        p("CONDUIT", 3000); p("HEAVY_CORE", 2500); p("TRIAL_KEY", 200); p("OMINOUS_TRIAL_KEY", 400);
        p("OMINOUS_BOTTLE", 150); p("SADDLE", 100); p("NAME_TAG", 50); p("RECOVERY_COMPASS", 600);
        p("TURTLE_HELMET", 150); p("SPAWNER", 500); p("WIND_CHARGE", 6);
        // Aletler / eşyalar
        p("BOW", 15); p("CROSSBOW", 30); p("FISHING_ROD", 10); p("FLINT_AND_STEEL", 25); p("SHEARS", 30);
        p("BUCKET", 60); p("WATER_BUCKET", 62); p("LAVA_BUCKET", 65); p("MILK_BUCKET", 62);
        p("SPYGLASS", 30); p("COMPASS", 25); p("CLOCK", 35); p("LEAD", 8); p("BOOK", 10); p("PAPER", 1);
        p("ENCHANTED_BOOK", 10); p("WRITTEN_BOOK", 5); p("WRITABLE_BOOK", 12); p("FILLED_MAP", 5); p("MAP", 15);
        p("GLASS_BOTTLE", 2); p("POTION", 25); p("SPLASH_POTION", 25); p("LINGERING_POTION", 30); p("TIPPED_ARROW", 10);
        p("FIREWORK_ROCKET", 6); p("FIREWORK_STAR", 4); p("POPPY", 1);
        // Blok / makine
        p("ANVIL", 150); p("ENCHANTING_TABLE", 250); p("ENDER_CHEST", 80); p("BREWING_STAND", 40);
        p("HOPPER", 80); p("PISTON", 40); p("STICKY_PISTON", 60); p("OBSERVER", 30); p("DISPENSER", 40);
        p("DROPPER", 30); p("REPEATER", 25); p("COMPARATOR", 50); p("DAYLIGHT_DETECTOR", 50); p("JUKEBOX", 160);
        p("BELL", 100); p("CHEST", 6); p("BARREL", 6); p("TRAPPED_CHEST", 20); p("FURNACE", 3);
        p("BLAST_FURNACE", 60); p("SMOKER", 25); p("CRAFTING_TABLE", 3); p("BOOKSHELF", 15); p("LECTERN", 12);
        p("CAULDRON", 25); p("LIGHTNING_ROD", 18); p("TARGET", 15); p("SLIME_BLOCK", 54); p("HONEY_BLOCK", 32);
    }

    /** Tek bir materyalin temel (büyüsüz, hasarsız) fiyatı. */
    public static double get(AtlasMC plugin, Material m) {
        double c = plugin.getConfig().getDouble("sell." + m.name(), -1);
        if (c >= 0) return c;
        Double v = D.get(m);
        if (v != null) return v;
        return Math.max(1, guess(m.name()));
    }

    public static Set<Material> orderable() {
        return D.keySet();
    }

    // ---------- Eşya (büyü, hasar, içerik dahil) ----------
    public static double value(AtlasMC plugin, ItemStack it) {
        return value(plugin, it, 0);
    }

    private static double value(AtlasMC plugin, ItemStack it, int depth) {
        if (it == null || it.getType().isAir()) return 0;
        double unit = get(plugin, it.getType());
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (meta instanceof Damageable) {
                int max = it.getType().getMaxDurability();
                if (max > 0) {
                    double f = 1.0 - ((Damageable) meta).getDamage() / (double) max;
                    unit *= Math.max(0.15, Math.min(1.0, f));
                }
            }
            for (Map.Entry<Enchantment, Integer> en : meta.getEnchants().entrySet())
                unit += enchantValue(en.getKey().getKey().getKey(), en.getValue());
            if (meta instanceof EnchantmentStorageMeta) {
                for (Map.Entry<Enchantment, Integer> en : ((EnchantmentStorageMeta) meta).getStoredEnchants().entrySet())
                    unit += enchantValue(en.getKey().getKey().getKey(), en.getValue());
            }
            if (depth < 2) {
                if (meta instanceof BlockStateMeta) {
                    BlockState st = ((BlockStateMeta) meta).getBlockState();
                    if (st instanceof ShulkerBox) {
                        for (ItemStack c : ((ShulkerBox) st).getInventory().getContents())
                            unit += value(plugin, c, depth + 1);
                    }
                }
                if (meta instanceof BundleMeta) {
                    for (ItemStack c : ((BundleMeta) meta).getItems()) unit += value(plugin, c, depth + 1);
                }
            }
        }
        return unit * it.getAmount();
    }

    private static double enchantValue(String key, int lvl) {
        switch (key) {
            case "mending": return 1500;
            case "silk_touch": return 500;
            case "infinity": return 700;
            case "multishot": return 400;
            case "channeling": return 300;
            case "fortune": return 250.0 * lvl;
            case "looting": return 200.0 * lvl;
            case "swift_sneak": return 400.0 * lvl;
            case "wind_burst": return 400.0 * lvl;
            case "density": case "breach": return 300.0 * lvl;
            case "riptide": return 250.0 * lvl;
            case "soul_speed": case "depth_strider": case "frost_walker": case "loyalty": case "quick_charge":
                return 150.0 * lvl;
            case "unbreaking": case "knockback": case "fire_aspect": return 120.0 * lvl;
            case "thorns": case "sweeping_edge": case "impaling": case "piercing": case "luck_of_the_sea": case "lure":
                return 100.0 * lvl;
            case "efficiency": case "sharpness": case "power": case "smite": case "bane_of_arthropods": return 90.0 * lvl;
            case "protection": case "fire_protection": case "blast_protection": case "projectile_protection":
                return 80.0 * lvl;
            case "binding_curse": case "vanishing_curse": return 5;
            default: return 80.0 * lvl;
        }
    }

    // ---------- İsme göre otomatik fiyat ----------
    private static double tierUnit(String t) {
        switch (t) {
            case "NETHERITE": case "DIAMOND": return 150;
            case "GOLDEN": return 30;
            case "IRON": return 20;
            case "CHAINMAIL": return 12;
            case "STONE": return 1.5;
            case "WOODEN": return 2;
            case "LEATHER": return 6;
            case "COPPER": return 6;
            default: return 0;
        }
    }

    private static double kindUnits(String kind) {
        switch (kind) {
            case "SWORD": return 2.5;
            case "PICKAXE": return 3.5;
            case "AXE": return 3.5;
            case "SHOVEL": return 1.5;
            case "HOE": return 2.5;
            case "HELMET": return 5;
            case "CHESTPLATE": return 8;
            case "LEGGINGS": return 7;
            case "BOOTS": return 4;
            default: return 0;
        }
    }

    private static double guess(String n) {
        // Alet / silah / zırh
        String[] tiers = {"NETHERITE", "DIAMOND", "GOLDEN", "IRON", "CHAINMAIL", "STONE", "WOODEN", "LEATHER", "COPPER"};
        for (String t : tiers) {
            if (!n.startsWith(t + "_")) continue;
            String kind = n.substring(t.length() + 1);
            if (kind.equals("HORSE_ARMOR")) {
                switch (t) {
                    case "IRON": return 100;
                    case "GOLDEN": return 150;
                    case "DIAMOND": return 400;
                    case "LEATHER": return 40;
                    default: break;
                }
            }
            double u = kindUnits(kind);
            if (u > 0) return tierUnit(t) * u + (t.equals("NETHERITE") ? 1800 : 0);
        }

        if (n.endsWith("_SPAWN_EGG")) return 150;
        if (n.startsWith("MUSIC_DISC")) return 80;
        if (n.equals("DISC_FRAGMENT_5")) return 20;
        if (n.endsWith("_POTTERY_SHERD")) return 40;
        if (n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) return 300;
        if (n.equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE")) return 600;
        if (n.endsWith("BANNER_PATTERN")) return 20;
        if (n.endsWith("_SHULKER_BOX")) return 200;

        // Maden blokları / cevherler
        String[] mins = {"DIAMOND", "EMERALD", "GOLD", "IRON", "COAL", "LAPIS", "REDSTONE", "COPPER", "QUARTZ", "AMETHYST"};
        double[] val = {150, 120, 30, 20, 8, 6, 5, 6, 8, 5};
        if (n.equals("NETHERITE_BLOCK")) return 16200;
        if (n.endsWith("_BLOCK")) {
            for (int i = 0; i < mins.length; i++) {
                if (n.equals(mins[i] + "_BLOCK") || n.equals("RAW_" + mins[i] + "_BLOCK"))
                    return (mins[i].equals("QUARTZ") ? 4 : 9) * val[i];
            }
        }
        if (n.endsWith("_ORE")) {
            for (int i = 0; i < mins.length; i++) {
                if (n.contains(mins[i])) return val[i] * (mins[i].equals("LAPIS") ? 5 : mins[i].equals("REDSTONE") ? 4 : 1);
            }
        }
        if (n.contains("COPPER")) return 54;

        // Boyalı / renkli bloklar
        if (n.endsWith("_DYE")) return 3;
        if (n.endsWith("_BANNER")) return 15;
        if (n.endsWith("_BED")) return 20;
        if (n.endsWith("_WOOL")) return 4;
        if (n.endsWith("_CARPET")) return 2;
        if (n.endsWith("_CONCRETE_POWDER") || n.endsWith("_CONCRETE")) return 2;
        if (n.endsWith("GLAZED_TERRACOTTA")) return 3;
        if (n.endsWith("_TERRACOTTA") || n.equals("TERRACOTTA")) return 2;
        if (n.contains("STAINED_GLASS_PANE") || n.equals("GLASS_PANE")) return 1;
        if (n.contains("STAINED_GLASS")) return 2;
        if (n.endsWith("_CANDLE") || n.equals("CANDLE")) return 4;
        if (n.endsWith("_HARNESS")) return 60;

        // Ahşap
        if (n.endsWith("_CHEST_BOAT") || n.endsWith("_CHEST_RAFT")) return 12;
        if (n.endsWith("_BOAT") || n.endsWith("_RAFT")) return 5;
        if (n.endsWith("_HANGING_SIGN")) return 4;
        if (n.endsWith("_SIGN")) return 3;
        if (n.endsWith("_PLANKS")) return 1;
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) return 3;
        if (n.equals("BAMBOO_BLOCK") || n.equals("STRIPPED_BAMBOO_BLOCK")) return 3;
        if (n.endsWith("_SAPLING") || n.endsWith("_PROPAGULE")) return 2;
        if (n.endsWith("_LEAVES")) return 1;
        if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE")
                || n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE")) return 3;

        // Kafalar
        if (n.endsWith("_HEAD") || n.endsWith("_SKULL")) return 50;

        // Taş ailesi, basamak, duvar
        if (n.endsWith("_SLAB") || n.endsWith("_STAIRS") || n.endsWith("_WALL")) return 2;
        if (n.contains("DEEPSLATE") || n.contains("STONE") || n.contains("BRICK") || n.contains("COBBLE")
                || n.contains("TUFF") || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE")
                || n.contains("BLACKSTONE") || n.contains("BASALT") || n.contains("SANDSTONE")
                || n.contains("PRISMARINE") || n.contains("PURPUR") || n.contains("MUD")) return 2;

        return 2;
    }
}
