package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

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

/** /spawn /setspawn /sethome /home /delhome /homes /tpa /tpahere /tpaccept /tpdeny /tpcancel /back /warp /setwarp /delwarp /warps */
public class TeleportManager implements CommandExecutor, TabCompleter, Listener {

    private static class Req {
        UUID from;
        boolean here;
        long expire;
    }

    private final AtlasMC plugin;
    private final CombatManager combat;
    private final File file;
    private final YamlConfiguration y;
    private final Map<UUID, Location> back = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Req> requests = new HashMap<>();

    public TeleportManager(AtlasMC plugin, CombatManager combat) {
        this.plugin = plugin;
        this.combat = combat;
        this.file = new File(plugin.getDataFolder(), "smp.yml");
        this.y = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("smp.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    private Location spawnLoc() {
        Location l = Util.getLoc(y, "spawn");
        return l != null ? l : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    // ---------- Işınlanma (bekleme + hareket iptali) ----------
    private void tp(Player p, Location dest, String label) {
        if (dest == null || dest.getWorld() == null) {
            plugin.msg(p, "&cIşınlanılacak konum bulunamadı.");
            return;
        }
        if (combat != null && combat.isTagged(p)) {
            plugin.msg(p, "&cSavaştayken ışınlanamazsın!");
            return;
        }
        if (!busy.add(p.getUniqueId())) {
            plugin.msg(p, "&cZaten ışınlanıyorsun.");
            return;
        }
        int warm = Util.admin(p) ? 0 : plugin.getConfig().getInt("teleport.warmup-seconds", 3);
        if (warm <= 0) {
            busy.remove(p.getUniqueId());
            p.teleport(dest);
            plugin.msg(p, "&a" + label + " &7konumuna ışınlandın.");
            return;
        }
        final Location from = p.getLocation();
        plugin.msg(p, "&7" + label + " konumuna ışınlanıyorsun, &e" + warm + " sn &7kıpırdama!");
        new BukkitRunnable() {
            int left = warm;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    busy.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                Location now = p.getLocation();
                if (!now.getWorld().equals(from.getWorld()) || now.distanceSquared(from) > 1.0) {
                    busy.remove(p.getUniqueId());
                    cancel();
                    plugin.msg(p, "&cHareket ettiğin için ışınlanma iptal edildi.");
                    return;
                }
                if (combat != null && combat.isTagged(p)) {
                    busy.remove(p.getUniqueId());
                    cancel();
                    plugin.msg(p, "&cSavaşa girdiğin için ışınlanma iptal edildi.");
                    return;
                }
                if (left <= 0) {
                    busy.remove(p.getUniqueId());
                    cancel();
                    p.teleport(dest);
                    plugin.msg(p, "&a" + label + " &7konumuna ışınlandın.");
                    return;
                }
                Util.bar(p, "&bIşınlanıyorsun... &f" + left);
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.COMMAND || c == PlayerTeleportEvent.TeleportCause.PLUGIN)
            back.put(e.getPlayer().getUniqueId(), e.getFrom());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        back.put(e.getEntity().getUniqueId(), e.getEntity().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        requests.remove(e.getPlayer().getUniqueId());
        busy.remove(e.getPlayer().getUniqueId());
    }

    // ---------- Evler ----------
    private ConfigurationSection homes(Player p, boolean create) {
        String path = "homes." + p.getUniqueId();
        ConfigurationSection s = y.getConfigurationSection(path);
        if (s == null && create) s = y.createSection(path);
        return s;
    }

    private int homeLimit(Player p) {
        if (p.isOp()) return 999;
        return Util.permNumber(p, "atlasmc.homes.", plugin.getConfig().getInt("home.default-limit", 2));
    }

    // ---------- Komutlar ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        Player p = (Player) s;
        switch (c.getName().toLowerCase()) {
            case "spawn":
                tp(p, spawnLoc(), "Spawn");
                break;
            case "setspawn":
                if (!Util.admin(p)) { plugin.msg(p, "&cBu komutu kullanamazsın."); break; }
                Util.putLoc(y, "spawn", p.getLocation());
                save();
                p.getWorld().setSpawnLocation(p.getLocation());
                plugin.msg(p, "&aSpawn noktası ayarlandı.");
                break;
            case "sethome": {
                String name = a.length > 0 ? a[0].toLowerCase() : "home";
                if (!name.matches("[a-z0-9_]{1,16}")) {
                    plugin.msg(p, "&cEv adı en fazla 16 harf/rakam/_ olabilir.");
                    break;
                }
                ConfigurationSection hs = homes(p, true);
                if (!hs.contains(name) && hs.getKeys(false).size() >= homeLimit(p)) {
                    plugin.msg(p, "&cEn fazla &e" + homeLimit(p) + " &cevin olabilir. &7(/delhome ile sil)");
                    break;
                }
                Util.putLoc(hs, name, p.getLocation());
                save();
                plugin.msg(p, "&a" + name + " &7evi kaydedildi.");
                break;
            }
            case "home": {
                ConfigurationSection hs = homes(p, false);
                if (hs == null || hs.getKeys(false).isEmpty()) {
                    plugin.msg(p, "&cHiç evin yok. &e/sethome &7ile ev kur.");
                    break;
                }
                String name;
                if (a.length > 0) name = a[0].toLowerCase();
                else if (hs.contains("home")) name = "home";
                else if (hs.getKeys(false).size() == 1) name = hs.getKeys(false).iterator().next();
                else {
                    plugin.msg(p, "&7Evlerin: &e" + String.join("&7, &e", hs.getKeys(false)));
                    break;
                }
                Location l = Util.getLoc(hs, name);
                if (l == null) { plugin.msg(p, "&cBu isimde bir evin yok."); break; }
                tp(p, l, "Ev (" + name + ")");
                break;
            }
            case "delhome": {
                ConfigurationSection hs = homes(p, false);
                String name = a.length > 0 ? a[0].toLowerCase() : "home";
                if (hs == null || !hs.contains(name)) { plugin.msg(p, "&cBu isimde bir evin yok."); break; }
                hs.set(name, null);
                save();
                plugin.msg(p, "&e" + name + " &7evi silindi.");
                break;
            }
            case "homes": {
                ConfigurationSection hs = homes(p, false);
                if (hs == null || hs.getKeys(false).isEmpty()) { plugin.msg(p, "&7Hiç evin yok."); break; }
                plugin.msg(p, "&7Evlerin &8(" + hs.getKeys(false).size() + "/" + homeLimit(p) + ")&7: &e"
                        + String.join("&7, &e", hs.getKeys(false)));
                break;
            }
            case "tpa":
            case "tpahere": {
                if (a.length < 1) { plugin.msg(p, "&cKullanım: &e/" + c.getName() + " <oyuncu>"); break; }
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null || t.equals(p)) { plugin.msg(p, "&cOyuncu bulunamadı."); break; }
                Req r = new Req();
                r.from = p.getUniqueId();
                r.here = c.getName().equalsIgnoreCase("tpahere");
                r.expire = System.currentTimeMillis() + 60000L;
                requests.put(t.getUniqueId(), r);
                plugin.msg(p, "&a" + t.getName() + " &7oyuncusuna istek gönderildi. &8(60 sn)");
                plugin.msg(t, "&e" + p.getName() + (r.here ? " &7seni kendisine ışınlamak istiyor." : " &7sana ışınlanmak istiyor."));
                plugin.msg(t, "&a/tpaccept &7kabul, &c/tpdeny &7reddet.");
                break;
            }
            case "tpaccept": {
                Req r = requests.remove(p.getUniqueId());
                if (r == null || r.expire < System.currentTimeMillis()) { plugin.msg(p, "&cBekleyen isteğin yok."); break; }
                Player from = Bukkit.getPlayer(r.from);
                if (from == null) { plugin.msg(p, "&cİstek gönderen oyuncu çevrimdışı."); break; }
                plugin.msg(p, "&aİstek kabul edildi.");
                plugin.msg(from, "&a" + p.getName() + " &7isteğini kabul etti.");
                if (r.here) tp(p, from.getLocation(), from.getName());
                else tp(from, p.getLocation(), p.getName());
                break;
            }
            case "tpdeny": {
                Req r = requests.remove(p.getUniqueId());
                if (r == null) { plugin.msg(p, "&cBekleyen isteğin yok."); break; }
                Player from = Bukkit.getPlayer(r.from);
                plugin.msg(p, "&eİstek reddedildi.");
                if (from != null) plugin.msg(from, "&c" + p.getName() + " &7isteğini reddetti.");
                break;
            }
            case "tpcancel": {
                boolean any = requests.values().removeIf(r -> r.from.equals(p.getUniqueId()));
                plugin.msg(p, any ? "&eGönderdiğin istekler iptal edildi." : "&cİptal edilecek isteğin yok.");
                break;
            }
            case "back": {
                Location l = back.get(p.getUniqueId());
                if (l == null) { plugin.msg(p, "&cDönülecek bir konum yok."); break; }
                tp(p, l.clone(), "Son konum");
                break;
            }
            case "warp": {
                ConfigurationSection ws = y.getConfigurationSection("warps");
                if (a.length < 1) {
                    plugin.msg(p, ws == null || ws.getKeys(false).isEmpty() ? "&7Hiç warp yok."
                            : "&7Warplar: &e" + String.join("&7, &e", ws.getKeys(false)));
                    break;
                }
                Location l = Util.getLoc(ws, a[0].toLowerCase());
                if (l == null) { plugin.msg(p, "&cBöyle bir warp yok."); break; }
                tp(p, l, "Warp (" + a[0].toLowerCase() + ")");
                break;
            }
            case "warps": {
                ConfigurationSection ws = y.getConfigurationSection("warps");
                plugin.msg(p, ws == null || ws.getKeys(false).isEmpty() ? "&7Hiç warp yok."
                        : "&7Warplar: &e" + String.join("&7, &e", ws.getKeys(false)));
                break;
            }
            case "setwarp": {
                if (!Util.admin(p)) { plugin.msg(p, "&cBu komutu kullanamazsın."); break; }
                if (a.length < 1 || !a[0].matches("[a-zA-Z0-9_]{1,16}")) { plugin.msg(p, "&cKullanım: &e/setwarp <isim>"); break; }
                Util.putLoc(y, "warps." + a[0].toLowerCase(), p.getLocation());
                save();
                plugin.msg(p, "&a" + a[0].toLowerCase() + " &7warp'ı kaydedildi.");
                break;
            }
            case "delwarp": {
                if (!Util.admin(p)) { plugin.msg(p, "&cBu komutu kullanamazsın."); break; }
                if (a.length < 1 || !y.contains("warps." + a[0].toLowerCase())) { plugin.msg(p, "&cBöyle bir warp yok."); break; }
                y.set("warps." + a[0].toLowerCase(), null);
                save();
                plugin.msg(p, "&e" + a[0].toLowerCase() + " &7warp'ı silindi.");
                break;
            }
            default:
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length != 1 || !(s instanceof Player)) return out;
        String n = c.getName().toLowerCase();
        String pre = a[0].toLowerCase();
        if (n.equals("tpa") || n.equals("tpahere")) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
        } else if (n.equals("home") || n.equals("delhome")) {
            ConfigurationSection hs = homes((Player) s, false);
            if (hs != null) for (String k : hs.getKeys(false)) if (k.startsWith(pre)) out.add(k);
        } else if (n.equals("warp") || n.equals("delwarp")) {
            ConfigurationSection ws = y.getConfigurationSection("warps");
            if (ws != null) for (String k : ws.getKeys(false)) if (k.startsWith(pre)) out.add(k);
        }
        return out;
    }
}
