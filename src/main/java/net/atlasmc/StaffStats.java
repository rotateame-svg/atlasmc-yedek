package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.atlasmc.AtlasMC.color;

/**
 * Yetkili aktifliği: yetkililerin (op, atlasmc.admin veya atlasmc.achat) aktif oyun süresi.
 * Süre sadece hareket/sohbet/komut/blok kırma ile aktif olduğunda ve AFK odasında değilken sayılır.
 *  /yetkiliaktiflik [sayfa]         : liste (son 7 güne göre)
 *  /yetkiliaktiflik <oyuncu>             : ayrıntı
 *  /yetkiliaktiflik sil <oyuncu>         : kaydı siler
 */
public class StaffStats implements CommandExecutor, Listener {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final long WINDOW = 5 * 60 * 1000L;
    private static StaffStats instance;

    public static void addAchat(UUID id) {
        if (instance != null) instance.inc(id, "achat");
    }

    public static void addMute(UUID id) {
        if (instance != null) instance.inc(id, "mutes");
    }

    private final AtlasMC plugin;
    private final File file;
    private final YamlConfiguration y;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private int saveTick = 0;

    public StaffStats(AtlasMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "staffstats.yml");
        this.y = YamlConfiguration.loadConfiguration(file);
        instance = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::minuteTick, 1200L, 1200L);
    }

    private boolean isStaff(Player p) {
        return Util.admin(p) || p.hasPermission("atlasmc.achat");
    }

    private void inc(UUID id, String key) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String path = "staff." + id + "." + key;
            y.set(path, y.getLong(path) + 1);
        });
    }

    private void save() {
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("staffstats.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    public void saveAll() {
        save();
    }

    private long today() {
        return LocalDate.now(ZONE).toEpochDay();
    }

    // ---------- Süre sayımı ----------
    private void minuteTick() {
        long now = System.currentTimeMillis();
        long today = today();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!isStaff(p)) {
                online.remove(id);
                continue;
            }
            online.add(id);
            String base = "staff." + id;
            y.set(base + ".name", p.getName());
            y.set(base + ".rank", plugin.getRank(p));
            y.set(base + ".last", now);
            Long la = lastActive.get(id);
            boolean afk = plugin.afk() != null && plugin.afk().isAfk(p);
            if (la != null && now - la < WINDOW && !afk) {
                y.set(base + ".total", y.getLong(base + ".total") + 1);
                String dk = base + ".days." + today;
                y.set(dk, y.getLong(dk) + 1);
            }
        }
        if (++saveTick >= 5) {
            saveTick = 0;
            prune(today);
            save();
        }
    }

    private void prune(long today) {
        ConfigurationSection staff = y.getConfigurationSection("staff");
        if (staff == null) return;
        for (String id : staff.getKeys(false)) {
            ConfigurationSection days = staff.getConfigurationSection(id + ".days");
            if (days == null) continue;
            for (String d : days.getKeys(false)) {
                try {
                    if (Long.parseLong(d) < today - 40) days.set(d, null);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !isStaff(p)) return;
            UUID id = p.getUniqueId();
            online.add(id);
            lastActive.put(id, System.currentTimeMillis());
            String base = "staff." + id;
            y.set(base + ".name", p.getName());
            y.set(base + ".rank", plugin.getRank(p));
            y.set(base + ".joins", y.getLong(base + ".joins") + 1);
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (online.remove(id)) y.set("staff." + id + ".last", System.currentTimeMillis());
        lastActive.remove(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!online.contains(id)) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        if (f.getBlockX() != t.getBlockX() || f.getBlockY() != t.getBlockY() || f.getBlockZ() != t.getBlockZ())
            lastActive.put(id, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (online.contains(id)) lastActive.put(id, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommandUse(PlayerCommandPreprocessEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (online.contains(id)) lastActive.put(id, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (online.contains(id)) lastActive.put(id, System.currentTimeMillis());
    }

    // ---------- Komut ----------
    private static String fmt(long m) {
        return m >= 60 ? (m / 60) + "sa " + (m % 60) + "dk" : m + "dk";
    }

    private long sumDays(String base, long today, int n) {
        long t = 0;
        for (int i = 0; i < n; i++) t += y.getLong(base + ".days." + (today - i));
        return t;
    }

    private String lastSeen(UUID id, String base) {
        if (online.contains(id) && Bukkit.getPlayer(id) != null) return "&aÇevrimiçi";
        long last = y.getLong(base + ".last", 0);
        if (last <= 0) return "&8bilinmiyor";
        long m = (System.currentTimeMillis() - last) / 60000;
        return "&f" + (m >= 1440 ? (m / 1440) + " gün" : m >= 60 ? (m / 60) + " saat" : m + " dk") + " önce";
    }

    private static class Row {
        UUID id;
        String base, name, rank;
        long week, today, total;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!Util.admin(s)) {
            plugin.msg(s, "&cBu komutu kullanamazsın.");
            return true;
        }
        long today = today();

        if (a.length >= 2 && a[0].equalsIgnoreCase("sil")) {
            OfflinePlayer t = Util.findPlayer(a[1]);
            if (t == null || !y.contains("staff." + t.getUniqueId())) {
                plugin.msg(s, "&cBu oyuncunun yetkili kaydı yok.");
                return true;
            }
            y.set("staff." + t.getUniqueId(), null);
            save();
            plugin.msg(s, "&e" + t.getName() + " &7yetkili kaydı silindi.");
            return true;
        }

        if (a.length >= 1 && !a[0].matches("\\d+")) {
            OfflinePlayer t = Util.findPlayer(a[0]);
            if (t == null || !y.contains("staff." + t.getUniqueId())) {
                plugin.msg(s, "&cBu oyuncunun yetkili kaydı yok.");
                return true;
            }
            String base = "staff." + t.getUniqueId();
            s.sendMessage(color("&b&lYetkili: &f" + y.getString(base + ".name", t.getName())
                    + " &8[" + y.getString(base + ".rank", "&7?") + "&8]"));
            s.sendMessage(color("&7Bugün: &b" + fmt(sumDays(base, today, 1)) + " &8| &7Son 7 gün: &a" + fmt(sumDays(base, today, 7))
                    + " &8| &7Son 30 gün: &a" + fmt(sumDays(base, today, 30))));
            s.sendMessage(color("&7Toplam: &f" + fmt(y.getLong(base + ".total")) + " &8| &7Giriş: &f" + y.getLong(base + ".joins")));
            s.sendMessage(color("&7Yetkili sohbeti mesajı: &f" + y.getLong(base + ".achat")
                    + " &8| &7Susturma: &f" + y.getLong(base + ".mutes")));
            s.sendMessage(color("&7Son görülme: " + lastSeen(t.getUniqueId(), base)));
            return true;
        }

        ConfigurationSection staff = y.getConfigurationSection("staff");
        if (staff == null || staff.getKeys(false).isEmpty()) {
            plugin.msg(s, "&7Henüz kayıtlı yetkili yok. Yetkililer girdikçe kaydedilir.");
            return true;
        }
        List<Row> rows = new ArrayList<>();
        for (String k : staff.getKeys(false)) {
            try {
                Row r = new Row();
                r.id = UUID.fromString(k);
                r.base = "staff." + k;
                r.name = y.getString(r.base + ".name", "?");
                r.rank = y.getString(r.base + ".rank", "&7?");
                r.week = sumDays(r.base, today, 7);
                r.today = sumDays(r.base, today, 1);
                r.total = y.getLong(r.base + ".total");
                rows.add(r);
            } catch (IllegalArgumentException ignored) {
            }
        }
        rows.sort(Comparator.comparingLong((Row r) -> r.week).reversed().thenComparingLong(r -> -r.total));

        int per = 10;
        int pages = Math.max(1, (rows.size() + per - 1) / per);
        int page = 1;
        if (a.length >= 1) {
            try {
                page = Math.max(1, Math.min(pages, Integer.parseInt(a[0])));
            } catch (NumberFormatException ignored) {
            }
        }
        long limit = plugin.getConfig().getLong("staff.inactive-minutes-week", 120);
        int inactive = 0;
        for (Row r : rows) if (r.week < limit) inactive++;

        s.sendMessage(color("&b&lYetkili Aktifliği &8(sayfa " + page + "/" + pages + ")"));
        s.sendMessage(color("&7Toplam &f" + rows.size() + " &7yetkili, &c" + inactive + " &7tanesi son 7 günde &c"
                + fmt(limit) + "&7'den az aktif."));
        for (int i = (page - 1) * per; i < Math.min(rows.size(), page * per); i++) {
            Row r = rows.get(i);
            String warn = r.week < limit ? "&c⚠ " : "";
            s.sendMessage(color("&e" + (i + 1) + ". " + warn + "&f" + r.name + " &8[" + r.rank + "&8] &7| 7 gün: &a" + fmt(r.week)
                    + " &7| Bugün: &b" + fmt(r.today) + " &7| " + lastSeen(r.id, r.base)));
        }
        s.sendMessage(color("&8Ayrıntı: /yetkiliaktiflik <oyuncu> | Sayfa: /yetkiliaktiflik <sayfa>"));
        return true;
    }
}
