package net.atlasmc;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.atlasmc.AtlasMC.color;

/** /shop : Kategori menüsü -> ürün listesi -> miktar seçme -> satın al (DonutSMP tarzı, sadece alış). */
public class ShopManager implements CommandExecutor, Listener {

    private record Item(Material mat, double price) {}

    private static class Cat {
        String name;
        Material icon;
        final List<Item> items = new ArrayList<>();
    }

    private static class MainHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static class CatHolder implements InventoryHolder {
        Inventory inv;
        int cat, page, pages = 1;
        @Override public Inventory getInventory() { return inv; }
    }

    private static class QtyHolder implements InventoryHolder {
        Inventory inv;
        Item item;
        int cat, page, amount = 1;
        @Override public Inventory getInventory() { return inv; }
    }

    private final AtlasMC plugin;
    private final List<Cat> cats = new ArrayList<>();

    public ShopManager(AtlasMC plugin) {
        this.plugin = plugin;
        loadConfig();
        if (cats.isEmpty()) loadDefaults();
    }

    private void loadConfig() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("shop.categories");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(k);
            if (c == null) continue;
            Cat cat = new Cat();
            cat.name = c.getString("name", k);
            Material ic = Material.matchMaterial(c.getString("icon", "CHEST"));
            cat.icon = ic == null ? Material.CHEST : ic;
            for (Map<?, ?> m : c.getMapList("items")) {
                Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
                Object pr = m.get("price") != null ? m.get("price") : m.get("buy");
                if (mat == null || pr == null) continue;
                double price;
                try {
                    price = Double.parseDouble(String.valueOf(pr));
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (price > 0) cat.items.add(new Item(mat, price));
            }
            if (!cat.items.isEmpty()) cats.add(cat);
        }
    }

    private void add(String name, String icon, String... items) {
        Cat c = new Cat();
        c.name = name;
        Material ic = Material.matchMaterial(icon);
        c.icon = ic == null ? Material.CHEST : ic;
        for (String s : items) {
            String[] p = s.split(":");
            Material m = Material.matchMaterial(p[0]);
            if (m != null) c.items.add(new Item(m, Double.parseDouble(p[1])));
        }
        cats.add(c);
    }

    private void loadDefaults() {
        add("&c&lPvP", "END_CRYSTAL", "END_CRYSTAL:150", "OBSIDIAN:20", "RESPAWN_ANCHOR:400", "GLOWSTONE:25",
                "TNT:100", "ENDER_PEARL:150", "TOTEM_OF_UNDYING:1000", "GOLDEN_APPLE:300");
        add("&b&lYardımcı Eşyalar", "EXPERIENCE_BOTTLE", "SHIELD:150", "EXPERIENCE_BOTTLE:100", "ARROW:5",
                "SPECTRAL_ARROW:15", "SHULKER_SHELL:800", "SHULKER_BOX:2000");
        add("&6&lYiyecek", "COOKED_BEEF", "COOKED_BEEF:25", "COOKED_PORKCHOP:25", "COOKED_CHICKEN:20",
                "COOKED_SALMON:20", "BREAD:15", "BAKED_POTATO:12", "GOLDEN_CARROT:40", "PUMPKIN_PIE:30",
                "HONEY_BOTTLE:30");
        add("&a&lBloklar", "GRASS_BLOCK", "COBBLESTONE:5", "STONE:8", "DEEPSLATE:8", "OAK_LOG:15", "SAND:8",
                "GLASS:12", "DIRT:3", "NETHERRACK:5", "END_STONE:15");
    }

    // ---------- Komut ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;
        openMain(p);
        return true;
    }

    // ---------- Yardımcılar ----------
    private ItemStack icon(Material m, int amount, String name, String... lore) {
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(color(s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack g = icon(Material.GRAY_STAINED_GLASS_PANE, 1, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, g);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private int capacity(Player p, Material m) {
        int max = m.getMaxStackSize(), cap = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) cap += max;
            else if (it.getType() == m && !it.hasItemMeta()) cap += Math.max(0, max - it.getAmount());
        }
        return cap;
    }

    // ---------- Ana menü ----------
    private void openMain(Player p) {
        MainHolder h = new MainHolder();
        h.inv = Bukkit.createInventory(h, 27, color(plugin.getConfig().getString("shop.title", "&8Atlas Shop")));
        fill(h.inv);
        int n = Math.min(cats.size(), 9);
        int step = n <= 5 ? 2 : 1;
        int width = n <= 5 ? 2 * n - 1 : n;
        int start = 9 + (9 - width) / 2;
        for (int i = 0; i < n; i++) {
            Cat c = cats.get(i);
            h.inv.setItem(start + i * step, icon(c.icon, 1, c.name,
                    "&7" + c.items.size() + " ürün", "", "&a▸ Kategoriyi açmak için tıkla!"));
        }
        p.openInventory(h.inv);
    }

    // ---------- Kategori ----------
    private void openCat(Player p, int ci, int page) {
        Cat c = cats.get(ci);
        CatHolder h = new CatHolder();
        h.cat = ci;
        h.pages = Math.max(1, (c.items.size() + 44) / 45);
        h.page = Math.max(0, Math.min(page, h.pages - 1));
        h.inv = Bukkit.createInventory(h, 54, color("&8Shop » " + c.name));
        for (int i = 0; i < 45; i++) {
            int idx = h.page * 45 + i;
            if (idx >= c.items.size()) break;
            Item it = c.items.get(idx);
            h.inv.setItem(i, icon(it.mat(), 1, "&f" + AtlasMC.pretty(it.mat().name()),
                    "&7Fiyat: &a$" + AtlasMC.money(it.price()) + " &7(tanesi)",
                    "", "&a▸ Miktar seçmek için tıkla!"));
        }
        ItemStack g = icon(Material.GRAY_STAINED_GLASS_PANE, 1, " ");
        for (int i = 45; i < 54; i++) h.inv.setItem(i, g);
        if (h.page > 0) h.inv.setItem(45, icon(Material.ARROW, 1, "&eÖnceki Sayfa"));
        h.inv.setItem(49, icon(Material.BARRIER, 1, "&cGeri Dön"));
        if (h.page < h.pages - 1) h.inv.setItem(53, icon(Material.ARROW, 1, "&eSonraki Sayfa"));
        p.openInventory(h.inv);
    }

    // ---------- Miktar seçme ----------
    private void openQty(Player p, int ci, int page, Item item) {
        QtyHolder h = new QtyHolder();
        h.cat = ci;
        h.page = page;
        h.item = item;
        h.inv = Bukkit.createInventory(h, 27, color("&8" + AtlasMC.pretty(item.mat().name())));
        renderQty(h, p);
        p.openInventory(h.inv);
    }

    private static final int[] QTY_SLOTS = {9, 10, 11, 12, 14, 15, 16, 17};
    private static final int[] QTY_DELTA = {-64, -16, -8, -1, 1, 8, 16, 64};

    private void renderQty(QtyHolder h, Player p) {
        Inventory inv = h.inv;
        fill(inv);
        int cap = Math.min(2304, capacity(p, h.item.mat()));
        if (h.amount > cap) h.amount = Math.max(1, cap);
        if (h.amount < 1) h.amount = 1;

        for (int i = 0; i < QTY_SLOTS.length; i++) {
            int d = QTY_DELTA[i];
            boolean neg = d < 0;
            inv.setItem(QTY_SLOTS[i], icon(neg ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    Math.abs(d), (neg ? "&c" : "&a") + (neg ? "-" : "+") + Math.abs(d)));
        }
        double total = h.item.price() * h.amount;
        inv.setItem(13, icon(h.item.mat(), h.amount, "&f" + AtlasMC.pretty(h.item.mat().name()) + " &7x&e" + h.amount,
                "&7Birim fiyat: &a$" + AtlasMC.money(h.item.price()),
                "&7Toplam: &a$" + AtlasMC.money(total)));
        inv.setItem(18, icon(Material.ARROW, 1, "&eGeri"));
        inv.setItem(20, icon(Material.PAPER, 1, "&fMiktarı Sıfırla", "&7Miktar: &e1"));
        inv.setItem(22, icon(Material.LIME_CONCRETE, 1, "&a&lSatın Al",
                "", "&7Miktar: &e" + h.amount,
                "&7Toplam: &a$" + AtlasMC.money(total),
                "&7Bakiyen: &a$" + AtlasMC.money(plugin.getMoney(p)),
                "", "&a▸ Satın almak için tıkla!"));
        inv.setItem(24, icon(Material.CHEST, 1, "&fMaksimum", "&7Envanterine sığan en fazla: &e" + cap));
    }

    private void buy(Player p, QtyHolder h) {
        Economy eco = plugin.getEconomy();
        if (eco == null) {
            plugin.msg(p, "&cEkonomi sistemi bulunamadı!");
            return;
        }
        Material m = h.item.mat();
        int amt = h.amount;
        if (capacity(p, m) < amt) {
            plugin.msg(p, "&cEnvanterinde yeterli yer yok!");
            return;
        }
        double total = h.item.price() * amt;
        if (!eco.has(p, total)) {
            plugin.msg(p, "&cYeterli paran yok! Gerekli: &a$" + AtlasMC.money(total));
            return;
        }
        EconomyResponse r = eco.withdrawPlayer(p, total);
        if (!r.transactionSuccess()) {
            plugin.msg(p, "&cÖdeme başarısız.");
            return;
        }
        Items.give(p, m, amt);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        plugin.msg(p, "&a" + amt + "x " + AtlasMC.pretty(m.name()) + " &7satın aldın. &c-$" + AtlasMC.money(total));
    }

    // ---------- Tıklamalar ----------
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof MainHolder || h instanceof CatHolder || h instanceof QtyHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof MainHolder || holder instanceof CatHolder || holder instanceof QtyHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        Player p = (Player) e.getWhoClicked();
        int s = e.getSlot();

        if (holder instanceof MainHolder) {
            int n = Math.min(cats.size(), 9);
            int step = n <= 5 ? 2 : 1;
            int width = n <= 5 ? 2 * n - 1 : n;
            int start = 9 + (9 - width) / 2;
            for (int i = 0; i < n; i++) {
                if (s == start + i * step) {
                    final int ci = i;
                    later(() -> openCat(p, ci, 0));
                    return;
                }
            }
        } else if (holder instanceof CatHolder) {
            CatHolder h = (CatHolder) holder;
            Cat c = cats.get(h.cat);
            if (s >= 0 && s < 45) {
                int idx = h.page * 45 + s;
                if (idx < c.items.size()) {
                    Item item = c.items.get(idx);
                    later(() -> openQty(p, h.cat, h.page, item));
                }
            } else if (s == 45 && h.page > 0) later(() -> openCat(p, h.cat, h.page - 1));
            else if (s == 53 && h.page < h.pages - 1) later(() -> openCat(p, h.cat, h.page + 1));
            else if (s == 49) later(() -> openMain(p));
        } else {
            QtyHolder h = (QtyHolder) holder;
            for (int i = 0; i < QTY_SLOTS.length; i++) {
                if (s == QTY_SLOTS[i]) {
                    int cap = Math.max(1, Math.min(2304, capacity(p, h.item.mat())));
                    h.amount = Math.max(1, Math.min(cap, h.amount + QTY_DELTA[i]));
                    renderQty(h, p);
                    return;
                }
            }
            if (s == 18) later(() -> openCat(p, h.cat, h.page));
            else if (s == 20) { h.amount = 1; renderQty(h, p); }
            else if (s == 22) { buy(p, h); renderQty(h, p); }
            else if (s == 24) { h.amount = Math.max(1, Math.min(2304, capacity(p, h.item.mat()))); renderQty(h, p); }
        }
    }
}
