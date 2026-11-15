package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.atlasmc.AtlasMC.color;

/**
 * Kit sistemi.
 *  /kitler                       : kit menüsü (sol tık al, sağ tık içeriği gör)
 *  /kityetkiver <oyuncu> <kit>   : oyuncuya kit yetkisi verir
 *  /kityetkial  <oyuncu> <kit>   : kit yetkisini geri alır
 * Yetki: LuckPerms izni "atlasmc.kit.<kit>" (örn. atlasmc.kit.vipplus) VEYA /kityetkiver ile verilen yetki.
 */
public class KitManager implements CommandExecutor, TabCompleter, Listener {

    private static class Kit {
        String id, name;
        Material icon;
        final String[] lines;
        final List<ItemStack> items = new ArrayList<>();
        Kit(String id, String name, Material icon, String... lines) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.lines = lines;
        }
    }

    private static class MenuHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static class PreviewHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final int[] SLOTS = {10, 11, 12, 14, 15, 16};

    private final AtlasMC plugin;
    private final List<Kit> kits = new ArrayList<>();
    private final Map<UUID, Set<String>> access = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final File file;

    public KitManager(AtlasMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        define();
        load();
    }

    // ---------- Kit tanımları ----------
    private ItemStack make(String mat, int amount, String... ench) {
        Material m = Material.matchMaterial(mat);
        if (m == null) return null;
        ItemStack it = new ItemStack(m, amount);
        if (ench.length > 0) {
            ItemMeta meta = it.getItemMeta();
            for (String e : ench) {
                String[] p = e.split(":");
                Enchantment en = Enchantment.getByKey(NamespacedKey.minecraft(p[0]));
                if (en != null) meta.addEnchant(en, Integer.parseInt(p[1]), true);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private void put(Kit k, String mat, int amount, String... ench) {
        ItemStack it = make(mat, amount, ench);
        if (it != null) k.items.add(it);
    }

    private void armor(Kit k, String tier, String... ench) {
        put(k, tier + "_HELMET", 1, ench);
        put(k, tier + "_CHESTPLATE", 1, ench);
        put(k, tier + "_LEGGINGS", 1, ench);
        put(k, tier + "_BOOTS", 1, ench);
    }

    private void define() {
        Kit k = new Kit("drehber", "&9&lD.Rehber Kiti", Material.DIAMOND_CHESTPLATE,
                "&7▪ &fElmas Zırh Seti &8(Koruma 3, Kırılmazlık 3)",
                "&7▪ &fElmas Kılıç &8(Keskinlik 3, Kırılmazlık 3)",
                "&7▪ &f8x Altın Elma",
                "&7▪ &f32x Pişmiş Et");
        armor(k, "DIAMOND", "protection:3", "unbreaking:3");
        put(k, "DIAMOND_SWORD", 1, "sharpness:3", "unbreaking:3");
        put(k, "GOLDEN_APPLE", 8);
        put(k, "COOKED_BEEF", 32);
        kits.add(k);

        k = new Kit("rehber", "&3&lRehber Kiti", Material.NETHERITE_CHESTPLATE,
                "&7▪ &fNetherite Zırh Seti &8(Koruma 4, Kırılmazlık 3, Onarım)",
                "&7▪ &fNetherite Kılıç &8(Keskinlik 4, Kırılmazlık 3, Onarım)",
                "&7▪ &fNetherite Balta &8(Keskinlik 4, Verimlilik 3, Kırılmazlık 3, Onarım)",
                "&7▪ &fKalkan &8(Kırılmazlık 3, Onarım)",
                "&7▪ &f16x Altın Elma",
                "&7▪ &f8x Ender İncisi",
                "&7▪ &f64x Pişmiş Et");
        armor(k, "NETHERITE", "protection:4", "unbreaking:3", "mending:1");
        put(k, "NETHERITE_SWORD", 1, "sharpness:4", "unbreaking:3", "mending:1");
        put(k, "NETHERITE_AXE", 1, "sharpness:4", "efficiency:3", "unbreaking:3", "mending:1");
        put(k, "SHIELD", 1, "unbreaking:3", "mending:1");
        put(k, "GOLDEN_APPLE", 16);
        put(k, "ENDER_PEARL", 8);
        put(k, "COOKED_BEEF", 64);
        kits.add(k);

        k = new Kit("vip", "&a&lVIP Kiti", Material.IRON_CHESTPLATE,
                "&7▪ &fDemir Zırh Seti &8(Koruma 2, Kırılmazlık 2)",
                "&7▪ &fDemir Kılıç &8(Keskinlik 2, Kırılmazlık 2)",
                "&7▪ &f3x Altın Elma",
                "&7▪ &f16x Pişmiş Et");
        armor(k, "IRON", "protection:2", "unbreaking:2");
        put(k, "IRON_SWORD", 1, "sharpness:2", "unbreaking:2");
        put(k, "GOLDEN_APPLE", 3);
        put(k, "COOKED_BEEF", 16);
        kits.add(k);

        k = new Kit("vipplus", "&2&lVIP+ Kiti", Material.DIAMOND_HELMET,
                "&7▪ &fElmas Zırh Seti &8(Koruma 2, Kırılmazlık 2)",
                "&7▪ &fElmas Kılıç &8(Keskinlik 2, Kırılmazlık 2)",
                "&7▪ &f5x Altın Elma",
                "&7▪ &f32x Pişmiş Et");
        armor(k, "DIAMOND", "protection:2", "unbreaking:2");
        put(k, "DIAMOND_SWORD", 1, "sharpness:2", "unbreaking:2");
        put(k, "GOLDEN_APPLE", 5);
        put(k, "COOKED_BEEF", 32);
        kits.add(k);

        k = new Kit("mvip", "&6&lMVIP Kiti", Material.DIAMOND_SWORD,
                "&7▪ &fElmas Zırh Seti &8(Koruma 3, Kırılmazlık 2)",
                "&7▪ &fElmas Kılıç &8(Keskinlik 3, Kırılmazlık 2)",
                "&7▪ &fElmas Kazma &8(Verimlilik 3, Kırılmazlık 2)",
                "&7▪ &f8x Altın Elma",
                "&7▪ &f4x Ender İncisi",
                "&7▪ &f32x Pişmiş Et");
        armor(k, "DIAMOND", "protection:3", "unbreaking:2");
        put(k, "DIAMOND_SWORD", 1, "sharpness:3", "unbreaking:2");
        put(k, "DIAMOND_PICKAXE", 1, "efficiency:3", "unbreaking:2");
        put(k, "GOLDEN_APPLE", 8);
        put(k, "ENDER_PEARL", 4);
        put(k, "COOKED_BEEF", 32);
        kits.add(k);

        k = new Kit("mvipplus", "&e&lMVIP+ Kiti", Material.NETHERITE_HELMET,
                "&7▪ &fNetherite Zırh Seti &8(Koruma 3, Kırılmazlık 3)",
                "&7▪ &fNetherite Kılıç &8(Keskinlik 3, Kırılmazlık 3)",
                "&7▪ &fNetherite Kazma &8(Verimlilik 4, Kırılmazlık 3)",
                "&7▪ &f12x Altın Elma",
                "&7▪ &f8x Ender İncisi",
                "&7▪ &f48x Pişmiş Et");
        armor(k, "NETHERITE", "protection:3", "unbreaking:3");
        put(k, "NETHERITE_SWORD", 1, "sharpness:3", "unbreaking:3");
        put(k, "NETHERITE_PICKAXE", 1, "efficiency:4", "unbreaking:3");
        put(k, "GOLDEN_APPLE", 12);
        put(k, "ENDER_PEARL", 8);
        put(k, "COOKED_BEEF", 48);
        kits.add(k);
    }

    /** "VIP+", "d.rehber", "MVIP+" -> "vipplus", "drehber", "mvipplus" */
    private static String norm(String s) {
        return s.toLowerCase().replace("+", "plus").replaceAll("[^a-z0-9]", "");
    }

    private Kit find(String name) {
        String n = norm(name);
        for (Kit k : kits) if (k.id.equals(n)) return k;
        return null;
    }

    // ---------- Yetki / bekleme ----------
    private boolean hasAccess(Player p, Kit k) {
        if (p.isOp() || p.hasPermission("atlasmc.kit.all") || p.hasPermission("atlasmc.kit." + k.id)) return true;
        Set<String> s = access.get(p.getUniqueId());
        return s != null && s.contains(k.id);
    }

    private long cooldownMs() {
        return plugin.getConfig().getLong("kit.cooldown-hours", 24) * 3600000L;
    }

    private long remaining(UUID id, Kit k) {
        Map<String, Long> m = cooldowns.get(id);
        if (m == null) return 0;
        Long next = m.get(k.id);
        return next == null ? 0 : Math.max(0, next - System.currentTimeMillis());
    }

    private static String fmt(long ms) {
        long m = ms / 60000;
        return (m / 60) + "sa " + (m % 60) + "dk";
    }

    // ---------- Komutlar ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String name = c.getName().toLowerCase();
        if (name.equals("kitler")) {
            if (!(s instanceof Player)) {
                s.sendMessage("Sadece oyuncular kullanabilir.");
                return true;
            }
            openMenu((Player) s);
            return true;
        }

        // /kityetkiver, /kityetkial
        if (!s.isOp() && !s.hasPermission("atlasmc.admin")) {
            plugin.msg(s, "&cBu komutu kullanamazsın.");
            return true;
        }
        if (a.length < 2) {
            plugin.msg(s, "&cKullanım: &e/" + name + " <oyuncu> <kit ismi>");
            plugin.msg(s, "&7Kitler: &fd.rehber, rehber, vip, vip+, mvip, mvip+");
            return true;
        }
        Kit kit = find(a[1]);
        if (kit == null) {
            plugin.msg(s, "&cBöyle bir kit yok. &7Kitler: &fd.rehber, rehber, vip, vip+, mvip, mvip+");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(a[0]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(a[0]);
            if (!target.hasPlayedBefore()) {
                plugin.msg(s, "&cBu oyuncu sunucuya hiç girmemiş.");
                return true;
            }
        }
        UUID id = target.getUniqueId();
        String tn = target.getName() == null ? a[0] : target.getName();
        String kn = color(kit.name);

        if (name.equals("kityetkiver")) {
            access.computeIfAbsent(id, k -> new HashSet<>()).add(kit.id);
            save();
            plugin.msg(s, "&a" + tn + " &7oyuncusuna " + kn + " &7yetkisi verildi.");
            if (target.isOnline())
                plugin.msg(target.getPlayer(), kn + " &ayetkisi aldın! &7Almak için &e/kitler");
        } else {
            Set<String> set = access.get(id);
            if (set != null) set.remove(kit.id);
            save();
            plugin.msg(s, "&e" + tn + " &7oyuncusunun " + kn + " &7yetkisi alındı.");
            if (target.isOnline()) plugin.msg(target.getPlayer(), kn + " &cyetkin alındı.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (c.getName().equalsIgnoreCase("kitler")) return out;
        if (a.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(a[0].toLowerCase())) out.add(p.getName());
        } else if (a.length == 2) {
            for (String k : new String[]{"d.rehber", "rehber", "vip", "vip+", "mvip", "mvip+"})
                if (k.startsWith(a[1].toLowerCase())) out.add(k);
        }
        return out;
    }

    // ---------- Menü ----------
    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(color(s));
        meta.setLore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private void openMenu(Player p) {
        MenuHolder h = new MenuHolder();
        h.inv = Bukkit.createInventory(h, 27, color("&8Kitler"));
        ItemStack g = icon(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>());
        for (int i = 0; i < 27; i++) h.inv.setItem(i, g);
        for (int i = 0; i < kits.size() && i < SLOTS.length; i++) {
            Kit k = kits.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&8Kit içeriği:");
            for (String l : k.lines) lore.add(l);
            lore.add("");
            long left = remaining(p.getUniqueId(), k);
            if (!hasAccess(p, k)) lore.add("&c✘ Bu kite yetkin yok.");
            else if (left > 0) lore.add("&e⌛ Bekleme: &f" + fmt(left));
            else lore.add("&a✔ Almak için sol tıkla!");
            lore.add("&7Sağ tık: &fiçeriği gör");
            h.inv.setItem(SLOTS[i], icon(k.icon, k.name, lore));
        }
        p.openInventory(h.inv);
    }

    private void openPreview(Player p, Kit k) {
        PreviewHolder h = new PreviewHolder();
        int size = Math.max(9, ((k.items.size() - 1) / 9 + 1) * 9);
        h.inv = Bukkit.createInventory(h, Math.min(54, size), color("&8" + ChatStrip.strip(k.name) + " içeriği"));
        for (int i = 0; i < k.items.size() && i < 54; i++) h.inv.setItem(i, k.items.get(i).clone());
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof MenuHolder || h instanceof PreviewHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof MenuHolder || holder instanceof PreviewHolder)) return;
        e.setCancelled(true);
        if (!(holder instanceof MenuHolder) || e.getClickedInventory() != e.getInventory()) return;
        Player p = (Player) e.getWhoClicked();
        for (int i = 0; i < kits.size() && i < SLOTS.length; i++) {
            if (e.getSlot() != SLOTS[i]) continue;
            final Kit k = kits.get(i);
            if (e.getClick().isRightClick()) Bukkit.getScheduler().runTask(plugin, () -> openPreview(p, k));
            else {
                claim(p, k);
                Bukkit.getScheduler().runTask(plugin, () -> openMenu(p));
            }
            return;
        }
    }

    // ---------- Kit alma ----------
    private boolean isArmor(ItemStack it, String suffix) {
        return it.getType().name().endsWith(suffix);
    }

    private void claim(Player p, Kit k) {
        if (!hasAccess(p, k)) {
            plugin.msg(p, "&cBu kite yetkin yok!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        long left = remaining(p.getUniqueId(), k);
        if (left > 0) {
            plugin.msg(p, "&cBu kiti tekrar almak için &e" + fmt(left) + " &cbeklemelisin.");
            return;
        }

        PlayerInventory inv = p.getInventory();
        boolean helmet = inv.getHelmet() == null || inv.getHelmet().getType().isAir();
        boolean chest = inv.getChestplate() == null || inv.getChestplate().getType().isAir();
        boolean legs = inv.getLeggings() == null || inv.getLeggings().getType().isAir();
        boolean boots = inv.getBoots() == null || inv.getBoots().getType().isAir();

        List<ItemStack> toInv = new ArrayList<>();
        List<ItemStack> equip = new ArrayList<>();
        for (ItemStack it : k.items) {
            ItemStack c = it.clone();
            if (helmet && isArmor(c, "_HELMET")) { helmet = false; equip.add(c); }
            else if (chest && isArmor(c, "_CHESTPLATE")) { chest = false; equip.add(c); }
            else if (legs && isArmor(c, "_LEGGINGS")) { legs = false; equip.add(c); }
            else if (boots && isArmor(c, "_BOOTS")) { boots = false; equip.add(c); }
            else toInv.add(c);
        }

        int free = 0;
        for (ItemStack s : inv.getStorageContents()) if (s == null || s.getType().isAir()) free++;
        if (free < toInv.size()) {
            plugin.msg(p, "&cEnvanterinde yeterli yer yok! En az &e" + toInv.size() + " &cboş slot gerekli.");
            return;
        }

        for (ItemStack c : equip) {
            if (isArmor(c, "_HELMET")) inv.setHelmet(c);
            else if (isArmor(c, "_CHESTPLATE")) inv.setChestplate(c);
            else if (isArmor(c, "_LEGGINGS")) inv.setLeggings(c);
            else inv.setBoots(c);
        }
        for (ItemStack c : toInv) inv.addItem(c);

        cooldowns.computeIfAbsent(p.getUniqueId(), x -> new HashMap<>())
                .put(k.id, System.currentTimeMillis() + cooldownMs());
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        plugin.msg(p, color(k.name) + " &aalındı! &7Boş zırh slotlarına otomatik giydirildi.");
    }

    // ---------- Kayıt ----------
    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> e : access.entrySet())
            y.set("access." + e.getKey(), new ArrayList<>(e.getValue()));
        for (Map.Entry<UUID, Map<String, Long>> e : cooldowns.entrySet())
            for (Map.Entry<String, Long> c : e.getValue().entrySet())
                y.set("cooldown." + e.getKey() + "." + c.getKey(), c.getValue());
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("kits.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection a = y.getConfigurationSection("access");
        if (a != null) {
            for (String k : a.getKeys(false)) {
                try {
                    access.put(UUID.fromString(k), new HashSet<>(a.getStringList(k)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection c = y.getConfigurationSection("cooldown");
        if (c != null) {
            for (String k : c.getKeys(false)) {
                ConfigurationSection s = c.getConfigurationSection(k);
                if (s == null) continue;
                try {
                    Map<String, Long> m = new HashMap<>();
                    for (String kit : s.getKeys(false)) m.put(kit, s.getLong(kit));
                    cooldowns.put(UUID.fromString(k), m);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }
}
