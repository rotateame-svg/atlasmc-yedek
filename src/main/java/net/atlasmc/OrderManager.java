package net.atlasmc;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.atlasmc.AtlasMC.color;

/**
 * /order : DonutSMP tarzı alım siparişleri.
 *  - Ana menü: açık siparişler. Birine tıklayınca teslim menüsü açılır; eşyaları koyup onaylarsın, para anında gelir.
 *  - Yeni Sipariş: eşya seç -> adet ve birim fiyatı butonlarla ayarla -> onayla (para peşin düşer).
 *  - Siparişlerim: sol tık teslim edilenleri al, sağ tık iptal et (kalan para iade).
 */
public class OrderManager implements CommandExecutor, Listener {

    static class Order {
        long id;
        UUID owner;
        String ownerName;
        Material mat;
        int amount, filled, collected;
        double unit;
    }

    private static class MainHolder implements InventoryHolder {
        Inventory inv;
        boolean mine;
        int page, pages = 1, sort;
        final List<Long> ids = new ArrayList<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private static class FillHolder implements InventoryHolder {
        Inventory inv;
        long id;
        boolean done;
        @Override public Inventory getInventory() { return inv; }
    }

    private static class PickHolder implements InventoryHolder {
        Inventory inv;
        int page, pages = 1;
        final List<Material> mats = new ArrayList<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private static class SetupHolder implements InventoryHolder {
        Inventory inv;
        Material mat;
        int amount = 64;
        double price = 1;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final String[] SORTS = {"En yüksek fiyat", "En yeni", "En çok kalan"};

    private final AtlasMC plugin;
    private final Map<Long, Order> orders = new LinkedHashMap<>();
    private final List<Material> candidates = new ArrayList<>();
    private long nextId = 1;
    private final File file;

    public OrderManager(AtlasMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
        candidates.addAll(Prices.orderable());
        candidates.sort(Comparator.comparing(Material::name));
        load();
    }

    // ---------- Komut ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        if (a.length == 0) {
            openMain(p, false, 0, 0);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "olustur", "oluştur", "create" -> {
                if (a.length < 3) {
                    plugin.msg(p, "&cKullanım: &e/order olustur <adet> <birim fiyat> [materyal]");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(a[1]);
                    double unit = Double.parseDouble(a[2].replace(",", "."));
                    Material mat = a.length >= 4 ? Material.matchMaterial(a[3]) : p.getInventory().getItemInMainHand().getType();
                    createOrder(p, mat, amount, unit);
                } catch (NumberFormatException ex) {
                    plugin.msg(p, "&cAdet ve fiyat sayı olmalı.");
                }
            }
            case "siparislerim", "siparişlerim" -> openMain(p, true, 0, 1);
            default -> {
                plugin.msg(p, "&e/order &7- Siparişleri gör ve eşya teslim et");
                plugin.msg(p, "&e/order olustur <adet> <birim fiyat> [materyal] &7- Listede olmayan eşya için sipariş ver");
                plugin.msg(p, "&e/order siparislerim &7- Siparişlerini yönet");
            }
        }
        return true;
    }

    private boolean createOrder(Player p, Material mat, int amount, double unit) {
        if (amount < 1 || amount > 10000) {
            plugin.msg(p, "&cAdet 1 ile 10000 arasında olmalı.");
            return false;
        }
        if (Double.isNaN(unit) || unit <= 0 || unit > 1e9) {
            plugin.msg(p, "&cGeçersiz birim fiyat.");
            return false;
        }
        if (mat == null || mat.isAir() || !mat.isItem()) {
            plugin.msg(p, "&cGeçerli bir eşya belirt (materyal yaz ya da elinde tut).");
            return false;
        }
        int count = 0;
        for (Order o : orders.values()) if (o.owner.equals(p.getUniqueId())) count++;
        int max = plugin.getConfig().getInt("order.max-orders", 5);
        if (count >= max) {
            plugin.msg(p, "&cEn fazla &e" + max + " &csiparişin olabilir.");
            return false;
        }
        Economy eco = plugin.getEconomy();
        if (eco == null) {
            plugin.msg(p, "&cEkonomi sistemi bulunamadı!");
            return false;
        }
        double total = amount * unit;
        if (!eco.has(p, total)) {
            plugin.msg(p, "&cYeterli paran yok! Gerekli: &a$" + AtlasMC.money(total));
            return false;
        }
        EconomyResponse r = eco.withdrawPlayer(p, total);
        if (!r.transactionSuccess()) {
            plugin.msg(p, "&cÖdeme başarısız.");
            return false;
        }
        Order o = new Order();
        o.id = nextId++;
        o.owner = p.getUniqueId();
        o.ownerName = p.getName();
        o.mat = mat;
        o.amount = amount;
        o.unit = unit;
        orders.put(o.id, o);
        save();
        plugin.msg(p, "&aSipariş verildi: &f" + amount + "x " + AtlasMC.pretty(mat.name())
                + " &7(tanesi &a$" + AtlasMC.money(unit) + "&7) &c-$" + AtlasMC.money(total));
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

    private void fill(Inventory inv, int from) {
        ItemStack g = icon(Material.GRAY_STAINED_GLASS_PANE, 1, " ");
        for (int i = from; i < inv.getSize(); i++) inv.setItem(i, g);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    // ---------- Ana menü ----------
    private void openMain(Player p, boolean mine, int page, int sort) {
        List<Order> list = new ArrayList<>();
        for (Order o : orders.values()) {
            if (mine) {
                if (o.owner.equals(p.getUniqueId())) list.add(o);
            } else if (o.amount - o.filled > 0) {
                list.add(o);
            }
        }
        if (mine || sort == 1) list.sort(Comparator.comparingLong((Order o) -> o.id).reversed());
        else if (sort == 2) list.sort(Comparator.comparingInt((Order o) -> o.amount - o.filled).reversed());
        else list.sort(Comparator.comparingDouble((Order o) -> o.unit).reversed());

        MainHolder h = new MainHolder();
        h.mine = mine;
        h.sort = sort;
        h.pages = Math.max(1, (list.size() + 44) / 45);
        h.page = Math.max(0, Math.min(page, h.pages - 1));
        h.inv = Bukkit.createInventory(h, 54, color(mine ? "&8Siparişlerim" : "&8Atlas Siparişler"));

        for (int i = 0; i < 45; i++) {
            int idx = h.page * 45 + i;
            if (idx >= list.size()) break;
            Order o = list.get(idx);
            h.ids.add(o.id);
            int remaining = o.amount - o.filled;
            List<String> lore = new ArrayList<>();
            lore.add("&7Sipariş sahibi: &f" + o.ownerName);
            lore.add("&7İstenen: &e" + remaining + "&7/&e" + o.amount);
            lore.add("&7Birim fiyat: &a$" + AtlasMC.money(o.unit));
            lore.add("&7Toplam ödenecek: &a$" + AtlasMC.money(remaining * o.unit));
            lore.add("");
            if (mine) {
                lore.add("&7Teslim edilip alınmayı bekleyen: &b" + o.collected);
                lore.add("&a▸ Sol tık: &fteslim edilenleri al");
                lore.add("&c▸ Sağ tık: &fiptal et (kalan para iade)");
            } else if (o.owner.equals(p.getUniqueId())) {
                lore.add("&8Bu senin siparişin.");
            } else {
                lore.add("&a▸ Teslim etmek için tıkla!");
            }
            h.inv.setItem(i, icon(o.mat, 1, "&f" + AtlasMC.pretty(o.mat.name()), lore.toArray(new String[0])));
        }

        fill(h.inv, 45);
        if (h.page > 0) h.inv.setItem(45, icon(Material.ARROW, 1, "&eÖnceki Sayfa"));
        if (h.page < h.pages - 1) h.inv.setItem(53, icon(Material.ARROW, 1, "&eSonraki Sayfa"));
        if (!mine) {
            List<String> sl = new ArrayList<>();
            sl.add("");
            for (int i = 0; i < SORTS.length; i++) sl.add((i == sort ? "&b ▪ &b" : "&8 ▪ &7") + SORTS[i]);
            sl.add("");
            sl.add("&b▸ &7Değiştirmek için tıkla!");
            h.inv.setItem(47, icon(Material.HOPPER, 1, "&fSırala", sl.toArray(new String[0])));
            h.inv.setItem(49, icon(Material.WRITABLE_BOOK, 1, "&a&lYeni Sipariş",
                    "&7Almak istediğin eşya için sipariş ver.", "", "&a▸ Tıkla!"));
            h.inv.setItem(50, icon(Material.CHEST, 1, "&6Siparişlerim", "&7Kendi siparişlerini yönet."));
        } else {
            h.inv.setItem(49, icon(Material.BOOK, 1, "&aTüm Siparişler", "&7Açık siparişlere dön."));
            h.inv.setItem(50, icon(Material.WRITABLE_BOOK, 1, "&a&lYeni Sipariş", "&7Yeni sipariş ver."));
        }
        p.openInventory(h.inv);
    }

    private void clickMain(Player p, MainHolder h, int s, boolean right) {
        if (s >= 0 && s < 45) {
            if (s >= h.ids.size()) return;
            Order o = orders.get(h.ids.get(s));
            if (o == null) { later(() -> openMain(p, h.mine, h.page, h.sort)); return; }
            if (h.mine) {
                if (right) cancel(p, o);
                else claim(p, o);
                later(() -> openMain(p, true, h.page, h.sort));
            } else if (o.owner.equals(p.getUniqueId())) {
                plugin.msg(p, "&cKendi siparişine teslimat yapamazsın.");
            } else {
                final long id = o.id;
                later(() -> openFill(p, id));
            }
            return;
        }
        if (s == 45 && h.page > 0) later(() -> openMain(p, h.mine, h.page - 1, h.sort));
        else if (s == 53 && h.page < h.pages - 1) later(() -> openMain(p, h.mine, h.page + 1, h.sort));
        else if (!h.mine && s == 47) later(() -> openMain(p, false, 0, (h.sort + 1) % SORTS.length));
        else if (!h.mine && s == 49) later(() -> openPick(p, 0));
        else if (!h.mine && s == 50) later(() -> openMain(p, true, 0, 1));
        else if (h.mine && s == 49) later(() -> openMain(p, false, 0, 0));
        else if (h.mine && s == 50) later(() -> openPick(p, 0));
    }

    // ---------- Teslim menüsü ----------
    private void openFill(Player p, long id) {
        Order o = orders.get(id);
        if (o == null) return;
        FillHolder h = new FillHolder();
        h.id = id;
        h.inv = Bukkit.createInventory(h, 54, color("&8Teslim: " + AtlasMC.pretty(o.mat.name())));
        fill(h.inv, 45);
        renderFill(h);
        p.openInventory(h.inv);
        plugin.msg(p, "&7İstenen eşyaları menüye koy, &aTeslim Et &7butonuna bas.");
    }

    private int acceptable(Inventory inv, Order o) {
        int remaining = o.amount - o.filled, n = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() == o.mat && !it.hasItemMeta()) n += it.getAmount();
        }
        return Math.min(n, remaining);
    }

    private void renderFill(FillHolder h) {
        Order o = orders.get(h.id);
        if (o == null) return;
        int acc = acceptable(h.inv, o);
        h.inv.setItem(45, icon(Material.RED_STAINED_GLASS_PANE, 1, "&c&lİptal", "&7Eşyaları geri al."));
        h.inv.setItem(49, icon(Material.LIME_CONCRETE, 1, "&a&lTeslim Et",
                "", "&7Teslim edilecek: &e" + acc,
                "&7Kazanç: &a$" + AtlasMC.money(acc * o.unit),
                "", "&a▸ Onaylamak için tıkla!"));
        h.inv.setItem(53, icon(o.mat, 1, "&f" + AtlasMC.pretty(o.mat.name()),
                "&7Kalan istek: &e" + (o.amount - o.filled) + "&7/&e" + o.amount,
                "&7Birim fiyat: &a$" + AtlasMC.money(o.unit),
                "&7Sipariş sahibi: &f" + o.ownerName));
    }

    private void confirmFill(Player p, FillHolder h) {
        Order o = orders.get(h.id);
        Economy eco = plugin.getEconomy();
        if (o == null || eco == null) {
            later(p::closeInventory);
            return;
        }
        int remaining = o.amount - o.filled;
        int accepted = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack it = h.inv.getItem(i);
            if (it == null || it.getType() != o.mat || it.hasItemMeta() || accepted >= remaining) continue;
            int take = Math.min(it.getAmount(), remaining - accepted);
            accepted += take;
            if (it.getAmount() - take <= 0) h.inv.setItem(i, null);
            else {
                it.setAmount(it.getAmount() - take);
                h.inv.setItem(i, it);
            }
        }
        if (accepted == 0) {
            plugin.msg(p, "&cMenüde bu siparişin istediği eşya yok! (Büyülü/isimli eşyalar kabul edilmez)");
            return;
        }
        double pay = accepted * o.unit;
        eco.depositPlayer(p, pay);
        o.filled += accepted;
        o.collected += accepted;
        save();
        h.done = true;
        for (int i = 0; i < 45; i++) {
            ItemStack it = h.inv.getItem(i);
            if (it != null && !it.getType().isAir()) {
                Items.giveOrDrop(p, it);
                h.inv.setItem(i, null);
            }
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        plugin.msg(p, "&a" + accepted + "x " + AtlasMC.pretty(o.mat.name()) + " &7teslim ettin. &2+$" + AtlasMC.money(pay));
        Player owner = Bukkit.getPlayer(o.owner);
        if (owner != null)
            plugin.msg(owner, "&a" + p.getName() + " &7siparişine &f" + accepted + "x " + AtlasMC.pretty(o.mat.name())
                    + " &7teslim etti. &e/order siparislerim &7ile al.");
        later(p::closeInventory);
    }

    private void handleFill(InventoryClickEvent e, FillHolder h) {
        Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == e.getInventory() && e.getSlot() >= 45) {
            e.setCancelled(true);
            if (e.getSlot() == 49) confirmFill(p, h);
            else if (e.getSlot() == 45) later(p::closeInventory);
            return;
        }
        later(() -> renderFill(h));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof FillHolder)) return;
        FillHolder h = (FillHolder) e.getInventory().getHolder();
        Player p = (Player) e.getPlayer();
        for (int i = 0; i < 45; i++) {
            ItemStack it = e.getInventory().getItem(i);
            if (it != null && !it.getType().isAir()) {
                Items.giveOrDrop(p, it);
                e.getInventory().setItem(i, null);
            }
        }
    }

    // ---------- Eşya seçme ----------
    private void openPick(Player p, int page) {
        PickHolder h = new PickHolder();
        h.mats.addAll(candidates);
        h.pages = Math.max(1, (h.mats.size() + 44) / 45);
        h.page = Math.max(0, Math.min(page, h.pages - 1));
        h.inv = Bukkit.createInventory(h, 54, color("&8Sipariş için eşya seç"));
        for (int i = 0; i < 45; i++) {
            int idx = h.page * 45 + i;
            if (idx >= h.mats.size()) break;
            Material m = h.mats.get(idx);
            h.inv.setItem(i, icon(m, 1, "&f" + AtlasMC.pretty(m.name()), "&a▸ Sipariş vermek için tıkla!"));
        }
        fill(h.inv, 45);
        if (h.page > 0) h.inv.setItem(45, icon(Material.ARROW, 1, "&eÖnceki Sayfa"));
        h.inv.setItem(47, icon(Material.PAPER, 1, "&eListede yok mu?",
                "&7Komutla sipariş ver:", "&f/order olustur <adet> <fiyat> <materyal>"));
        h.inv.setItem(49, icon(Material.BARRIER, 1, "&cGeri Dön"));
        if (h.page < h.pages - 1) h.inv.setItem(53, icon(Material.ARROW, 1, "&eSonraki Sayfa"));
        p.openInventory(h.inv);
    }

    // ---------- Sipariş ayarlama ----------
    private static final int[] A_SLOTS = {9, 10, 11, 12, 14, 15, 16, 17};
    private static final int[] A_DELTA = {-256, -64, -16, -1, 1, 16, 64, 256};
    private static final int[] P_SLOTS = {18, 19, 20, 21, 23, 24, 25, 26};
    private static final int[] P_DELTA = {-1000, -100, -10, -1, 1, 10, 100, 1000};

    private void openSetup(Player p, Material m) {
        SetupHolder h = new SetupHolder();
        h.mat = m;
        double sell = Prices.get(plugin, m);
        h.price = Math.max(1, Math.round(sell * 1.5));
        h.inv = Bukkit.createInventory(h, 45, color("&8Sipariş: " + AtlasMC.pretty(m.name())));
        renderSetup(h, p);
        p.openInventory(h.inv);
    }

    private void renderSetup(SetupHolder h, Player p) {
        Inventory inv = h.inv;
        fill(inv, 0);
        for (int i = 0; i < A_SLOTS.length; i++) {
            int d = A_DELTA[i];
            inv.setItem(A_SLOTS[i], icon(d < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    Math.abs(d), (d < 0 ? "&c-" : "&a+") + Math.abs(d) + " adet"));
        }
        for (int i = 0; i < P_SLOTS.length; i++) {
            int d = P_DELTA[i];
            inv.setItem(P_SLOTS[i], icon(d < 0 ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                    1, (d < 0 ? "&c-$" : "&a+$") + Math.abs(d)));
        }
        double total = h.amount * h.price;
        inv.setItem(13, icon(h.mat, h.amount, "&fAdet: &e" + h.amount, "&7İstenen eşya: &f" + AtlasMC.pretty(h.mat.name())));
        inv.setItem(22, icon(Material.GOLD_INGOT, 1, "&fBirim Fiyat: &a$" + AtlasMC.money(h.price),
                "&7Tanesi için ödeyeceğin fiyat."));
        inv.setItem(27, icon(Material.BARRIER, 1, "&cGeri Dön"));
        inv.setItem(31, icon(Material.EMERALD, 1, "&a&lSiparişi Oluştur",
                "", "&7Eşya: &f" + AtlasMC.pretty(h.mat.name()),
                "&7Adet: &e" + h.amount,
                "&7Birim fiyat: &a$" + AtlasMC.money(h.price),
                "&7Toplam: &a$" + AtlasMC.money(total),
                "&7Bakiyen: &a$" + AtlasMC.money(plugin.getMoney(p)),
                "", "&a▸ Onaylamak için tıkla!"));
    }

    private void clickSetup(Player p, SetupHolder h, int s) {
        for (int i = 0; i < A_SLOTS.length; i++) {
            if (s == A_SLOTS[i]) {
                h.amount = Math.max(1, Math.min(10000, h.amount + A_DELTA[i]));
                renderSetup(h, p);
                return;
            }
        }
        for (int i = 0; i < P_SLOTS.length; i++) {
            if (s == P_SLOTS[i]) {
                h.price = Math.max(1, Math.min(1e9, h.price + P_DELTA[i]));
                renderSetup(h, p);
                return;
            }
        }
        if (s == 27) later(() -> openPick(p, 0));
        else if (s == 31) {
            if (createOrder(p, h.mat, h.amount, h.price)) later(() -> openMain(p, true, 0, 1));
            else renderSetup(h, p);
        }
    }

    // ---------- Tıklama dağıtımı ----------
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof FillHolder) {
            for (int slot : e.getRawSlots()) if (slot >= 45 && slot < 54) { e.setCancelled(true); return; }
            FillHolder h = (FillHolder) holder;
            later(() -> renderFill(h));
        } else if (holder instanceof MainHolder || holder instanceof PickHolder || holder instanceof SetupHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof FillHolder) {
            handleFill(e, (FillHolder) holder);
            return;
        }
        if (!(holder instanceof MainHolder || holder instanceof PickHolder || holder instanceof SetupHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        Player p = (Player) e.getWhoClicked();
        int s = e.getSlot();

        if (holder instanceof MainHolder) {
            clickMain(p, (MainHolder) holder, s, e.getClick().isRightClick());
        } else if (holder instanceof PickHolder) {
            PickHolder h = (PickHolder) holder;
            if (s >= 0 && s < 45) {
                int idx = h.page * 45 + s;
                if (idx < h.mats.size()) {
                    Material m = h.mats.get(idx);
                    later(() -> openSetup(p, m));
                }
            } else if (s == 45 && h.page > 0) later(() -> openPick(p, h.page - 1));
            else if (s == 53 && h.page < h.pages - 1) later(() -> openPick(p, h.page + 1));
            else if (s == 49) later(() -> openMain(p, false, 0, 0));
        } else {
            clickSetup(p, (SetupHolder) holder, s);
        }
    }

    // ---------- Siparişlerim eylemleri ----------
    private void claim(Player p, Order o) {
        if (o.collected <= 0) {
            plugin.msg(p, "&7Henüz teslim edilen ürün yok.");
            return;
        }
        int given = Items.give(p, o.mat, o.collected);
        o.collected -= given;
        if (given == 0) plugin.msg(p, "&cEnvanterin dolu!");
        else plugin.msg(p, "&a" + given + "x " + AtlasMC.pretty(o.mat.name()) + " &7teslim aldın.");
        if (o.filled >= o.amount && o.collected <= 0) {
            orders.remove(o.id);
            plugin.msg(p, "&7Sipariş tamamlandı ve kaldırıldı.");
        }
        save();
    }

    private void cancel(Player p, Order o) {
        Economy eco = plugin.getEconomy();
        int remaining = o.amount - o.filled;
        if (remaining > 0 && eco != null) {
            double refund = remaining * o.unit;
            eco.depositPlayer(p, refund);
            plugin.msg(p, "&eSipariş iptal edildi. &a+$" + AtlasMC.money(refund) + " &7iade.");
        } else {
            plugin.msg(p, "&eSipariş kaldırıldı.");
        }
        int left = o.collected;
        while (left > 0) {
            int n = Math.min(left, o.mat.getMaxStackSize());
            Items.giveOrDrop(p, new ItemStack(o.mat, n));
            left -= n;
        }
        orders.remove(o.id);
        save();
    }

    // ---------- Kayıt ----------
    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Order o : orders.values()) {
            String p = "orders." + o.id + ".";
            y.set(p + "owner", o.owner.toString());
            y.set(p + "name", o.ownerName);
            y.set(p + "material", o.mat.name());
            y.set(p + "amount", o.amount);
            y.set(p + "filled", o.filled);
            y.set(p + "collected", o.collected);
            y.set(p + "unit", o.unit);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("orders.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("orders");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null) continue;
            try {
                Order o = new Order();
                o.id = Long.parseLong(k);
                o.owner = UUID.fromString(s.getString("owner"));
                o.ownerName = s.getString("name", "?");
                o.mat = Material.matchMaterial(s.getString("material", ""));
                if (o.mat == null) continue;
                o.amount = s.getInt("amount");
                o.filled = s.getInt("filled");
                o.collected = s.getInt("collected");
                o.unit = s.getDouble("unit");
                orders.put(o.id, o);
                nextId = Math.max(nextId, o.id + 1);
            } catch (Exception ignored) {
            }
        }
    }
}
