package net.atlasmc;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.atlasmc.AtlasMC.color;

/**
 * Günlük ödül: 30 günlük takvim. Her gün bir kez alınır (gece yarısı sıfırlanır, Türkiye saati).
 *  - Giriş yapılan gün sayısı kadar ilerler (kaçırılan gün seriyi bozmaz)
 *  - 7, 14, 21, 28. günler: 1 Kasa Anahtarı
 *  - 30. gün: 3 Kasa Anahtarı + büyük ödül; sonra döngü 1. günden başlar
 */
public class DailyManager implements CommandExecutor, Listener {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final int CYCLE = 30;

    private static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static class Reward {
        double money;
        int crystals, keys;
        final List<ItemStack> items = new ArrayList<>();
    }

    private static final String[][] ITEMS = new String[CYCLE + 1][];
    static {
        ITEMS[1] = new String[]{"COOKED_BEEF:16"};
        ITEMS[2] = new String[]{"EXPERIENCE_BOTTLE:8"};
        ITEMS[3] = new String[]{"GOLDEN_APPLE:2"};
        ITEMS[4] = new String[]{"ENDER_PEARL:4"};
        ITEMS[5] = new String[]{"EXPERIENCE_BOTTLE:16"};
        ITEMS[6] = new String[]{"GOLDEN_APPLE:3"};
        ITEMS[7] = new String[]{"COOKED_BEEF:32", "DIAMOND:2"};
        ITEMS[8] = new String[]{"COOKED_BEEF:16"};
        ITEMS[9] = new String[]{"EXPERIENCE_BOTTLE:16"};
        ITEMS[10] = new String[]{"GOLDEN_APPLE:4", "ENDER_PEARL:4"};
        ITEMS[11] = new String[]{"OBSIDIAN:16"};
        ITEMS[12] = new String[]{"EXPERIENCE_BOTTLE:24"};
        ITEMS[13] = new String[]{"GOLDEN_APPLE:4"};
        ITEMS[14] = new String[]{"COOKED_BEEF:48", "DIAMOND:4"};
        ITEMS[15] = new String[]{"ENDER_PEARL:6"};
        ITEMS[16] = new String[]{"EXPERIENCE_BOTTLE:24"};
        ITEMS[17] = new String[]{"GOLDEN_APPLE:5"};
        ITEMS[18] = new String[]{"OBSIDIAN:24"};
        ITEMS[19] = new String[]{"EXPERIENCE_BOTTLE:32"};
        ITEMS[20] = new String[]{"TOTEM_OF_UNDYING:1"};
        ITEMS[21] = new String[]{"GOLDEN_APPLE:8", "DIAMOND:6"};
        ITEMS[22] = new String[]{"ENDER_PEARL:8"};
        ITEMS[23] = new String[]{"EXPERIENCE_BOTTLE:32"};
        ITEMS[24] = new String[]{"GOLDEN_APPLE:6"};
        ITEMS[25] = new String[]{"END_CRYSTAL:4"};
        ITEMS[26] = new String[]{"EXPERIENCE_BOTTLE:40"};
        ITEMS[27] = new String[]{"GOLDEN_APPLE:8"};
        ITEMS[28] = new String[]{"TOTEM_OF_UNDYING:1", "DIAMOND:8"};
        ITEMS[29] = new String[]{"ENDER_PEARL:8", "EXPERIENCE_BOTTLE:40"};
        ITEMS[30] = new String[]{"TOTEM_OF_UNDYING:2", "DIAMOND:16", "GOLDEN_APPLE:16", "EXPERIENCE_BOTTLE:64"};
    }

    private final AtlasMC plugin;
    private final File file;
    private final YamlConfiguration y;

    public DailyManager(AtlasMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dailyrewards.yml");
        this.y = YamlConfiguration.loadConfiguration(file);
    }

    // ---------- Durum ----------
    private long today() {
        return LocalDate.now(ZONE).toEpochDay();
    }

    private int progress(UUID id) {
        return y.getInt(id + ".progress", 0);
    }

    private long last(UUID id) {
        return y.getLong(id + ".last", -1);
    }

    private boolean canClaim(UUID id) {
        return last(id) < today();
    }

    /** Menüde gösterilecek tamamlanmış gün sayısı (30 gün bittiyse ve yeni gün geldiyse 0). */
    private int shown(UUID id) {
        int pr = progress(id);
        return (pr >= CYCLE && canClaim(id)) ? 0 : pr;
    }

    private String untilMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime mid = now.toLocalDate().plusDays(1).atStartOfDay();
        long m = Duration.between(now, mid).toMinutes();
        return (m / 60) + "sa " + (m % 60) + "dk";
    }

    private void save() {
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("dailyrewards.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    // ---------- Ödül tablosu ----------
    private Reward reward(int day) {
        Reward r = new Reward();
        double baseM = plugin.getConfig().getDouble("daily.money", 500);
        double stepM = plugin.getConfig().getDouble("daily.money-step", 75);
        int baseC = plugin.getConfig().getInt("daily.crystals", 10);
        int stepC = plugin.getConfig().getInt("daily.crystals-step", 1);
        double mult = day == CYCLE ? 3 : (day % 7 == 0 ? 2 : 1);
        r.money = (baseM + stepM * (day - 1)) * mult;
        r.crystals = (int) Math.round((baseC + stepC * (day - 1)) * mult);
        r.keys = day == CYCLE ? 3 : (day % 7 == 0 ? 1 : 0);
        if (ITEMS[day] != null) {
            for (String spec : ITEMS[day]) {
                String[] p = spec.split(":");
                Material m = Material.matchMaterial(p[0]);
                if (m != null) r.items.add(new ItemStack(m, Integer.parseInt(p[1])));
            }
        }
        return r;
    }

    private List<String> describe(Reward r) {
        List<String> l = new ArrayList<>();
        l.add("&7Para: &a$" + AtlasMC.money(r.money));
        l.add("&7Kristal: &d+" + r.crystals);
        for (ItemStack it : r.items)
            l.add("&7▪ &f" + it.getAmount() + "x " + AtlasMC.pretty(it.getType().name()));
        if (r.keys > 0) l.add("&d&l✦ &d" + r.keys + "x Kasa Anahtarı");
        return l;
    }

    // ---------- Komut ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        Player p = (Player) s;
        if (a.length >= 2 && a[0].equalsIgnoreCase("sifirla") && Util.admin(p)) {
            OfflinePlayer t = Util.findPlayer(a[1]);
            if (t == null) { plugin.msg(p, "&cOyuncu bulunamadı."); return true; }
            y.set(t.getUniqueId().toString(), null);
            save();
            plugin.msg(p, "&e" + t.getName() + " &7günlük ödül ilerlemesi sıfırlandı.");
            return true;
        }
        open(p);
        return true;
    }

    // ---------- Menü ----------
    private ItemStack icon(Material m, int amount, boolean glow, String name, List<String> lore) {
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(color(s));
        meta.setLore(l);
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, color("&8Günlük Ödül"));
        render(h, p);
        p.openInventory(h.inv);
    }

    private void render(Holder h, Player p) {
        Inventory inv = h.inv;
        UUID id = p.getUniqueId();
        ItemStack black = icon(Material.BLACK_STAINED_GLASS_PANE, 1, false, " ", new ArrayList<>());
        for (int i = 0; i < 54; i++) inv.setItem(i, black);

        int done = shown(id);
        boolean can = canClaim(id);
        int nextDay = Math.min(CYCLE, done + 1);

        for (int day = 1; day <= CYCLE; day++) {
            Reward r = reward(day);
            List<String> lore = new ArrayList<>(describe(r));
            lore.add(0, "");
            boolean special = r.keys > 0;
            ItemStack it;
            if (day <= done) {
                lore.add("");
                lore.add("&a✔ Alındı");
                it = icon(Material.LIME_STAINED_GLASS_PANE, day, false, "&a&lGün " + day, lore);
            } else if (day == nextDay && can) {
                lore.add("");
                lore.add("&e▸ Almak için tıkla!");
                Material m = day == CYCLE ? Material.NETHER_STAR : (special ? Material.TRIPWIRE_HOOK : Material.EMERALD);
                it = icon(m, day, true, "&e&lGün " + day + " &a- HAZIR!", lore);
            } else {
                lore.add("");
                lore.add("&8Henüz açılmadı");
                Material m = day == CYCLE ? Material.NETHER_STAR : (special ? Material.TRIPWIRE_HOOK : Material.GRAY_STAINED_GLASS_PANE);
                String title = day == CYCLE ? "&6&lGün " + day + " &d(BÜYÜK ÖDÜL)"
                        : special ? "&d&lGün " + day + " &d(Kasa Anahtarı)" : "&7Gün " + day;
                it = icon(m, day, false, title, lore);
            }
            inv.setItem(day - 1, it);
        }

        inv.setItem(45, icon(Material.BOOK, 1, false, "&b&lNasıl Çalışır?",
                List.of("&7Her gün sunucuya gir ve ödülünü al.",
                        "&7Ödül gece yarısı (00:00) yenilenir.",
                        "",
                        "&d✦ &77, 14, 21 ve 28. günlerde &d1 Kasa Anahtarı",
                        "&d✦ &730. günde &d3 Kasa Anahtarı &7+ büyük ödül",
                        "&730 günden sonra takvim &e1. günden &7tekrar başlar.",
                        "",
                        "&7Toplam giriş yaptığın gün: &e" + y.getInt(id + ".total", 0))));

        if (can) {
            inv.setItem(49, icon(Material.LIME_CONCRETE, 1, true, "&a&lBugünkü Ödülü Al",
                    List.of("&7Alacağın: &eGün " + nextDay, "", "&e▸ Tıkla!")));
        } else {
            inv.setItem(49, icon(Material.RED_CONCRETE, 1, false, "&cBugünkü ödülü aldın",
                    List.of("&7Yeni ödül için: &e" + untilMidnight())));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        Player p = (Player) e.getWhoClicked();
        Holder h = (Holder) e.getInventory().getHolder();
        UUID id = p.getUniqueId();
        int slot = e.getSlot();
        int nextDay = Math.min(CYCLE, shown(id) + 1);
        if (slot == 49 || (slot == nextDay - 1 && canClaim(id))) {
            claim(p);
            render(h, p);
        }
    }

    // ---------- Ödül verme ----------
    private void claim(Player p) {
        UUID id = p.getUniqueId();
        if (!canClaim(id)) {
            plugin.msg(p, "&cBugünkü ödülünü zaten aldın. Yeni ödül: &e" + untilMidnight());
            return;
        }
        int day = shown(id) + 1;
        if (day > CYCLE) day = 1;
        Reward r = reward(day);

        Economy eco = plugin.getEconomy();
        if (eco != null && r.money > 0) eco.depositPlayer(p, r.money);
        plugin.addCrystals(id, r.crystals);
        for (ItemStack it : r.items) Items.giveOrDrop(p, it.clone());

        String note = "";
        if (r.keys > 0) {
            if (Bukkit.getPluginManager().getPlugin("AtlasKasa") != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kasaanahtarver " + r.keys + " " + p.getName());
            } else {
                int comp = 50 * r.keys;
                plugin.addCrystals(id, comp);
                note = " &8(Kasa plugini yok, karşılığında +" + comp + " kristal verildi)";
                plugin.getLogger().warning("AtlasKasa yüklü değil; günlük ödül anahtarı yerine kristal verildi.");
            }
        }

        y.set(id + ".progress", day);
        y.set(id + ".last", today());
        y.set(id + ".total", y.getInt(id + ".total", 0) + 1);
        save();

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.sendTitle(color("&a&lGünlük Ödül"), color("&7Gün &e" + day + "&7/&e" + CYCLE), 5, 40, 10);
        plugin.msg(p, "&aGün " + day + " ödülünü aldın! &a+$" + AtlasMC.money(r.money) + " &8| &d+" + r.crystals
                + " Kristal" + (r.keys > 0 ? " &8| &d" + r.keys + "x Kasa Anahtarı" : "") + note);
        if (day == CYCLE) {
            Bukkit.broadcastMessage(color(AtlasMC.PREFIX + "&e" + p.getName() + " &730 günlük ödül takvimini tamamladı! &6&l✦"));
            plugin.msg(p, "&7Takvim sıfırlandı, bir sonraki ödülün &e1. gün&7'den başlar.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && canClaim(p.getUniqueId()))
                plugin.msg(p, "&a&lGünlük ödülün hazır! &7Almak için &e/gunluk &7yaz.");
        }, 100L);
    }
}
