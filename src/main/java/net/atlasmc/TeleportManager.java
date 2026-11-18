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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
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
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Map<UUID, Req>> requests = new HashMap<>();
    private final Map<UUID, Long> tpaCooldown = new HashMap<>();

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
    public void tp(Player p, Location dest, String label) {
        if (dest == null || dest.getWorld() == null) {
            plugin.msg(p, "&cIşınlanılacak konum bulunamadı.");
            return;
        }
        if (combat != null && combat.isTagged(p)) {
            plugin.msg(p, "&cSavaştayken ışınlanamazsın!");
            return;
        }
        if (plugin.afk() != null && plugin.afk().isAfkWorld(dest.getWorld())) {
            plugin.msg(p, "&cAFK odasına bu şekilde ışınlanamazsın. &7(/afk kullan)");
            return;
        }
        if (!busy.add(p.getUniqueId())) {
            plugin.msg(p, "&cZaten ışınlanıyorsun.");
            return;
        }
        int warm = Util.admin(p) ? 0 : plugin.getConfig().getInt("teleport.warmup-seconds", 3);
        if (warm <= 0) {
            busy.remove(p.getUniqueId());
            doTp(p, dest, label);
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
                    doTp(p, dest, label);
                    return;
                }
                Util.bar(p, "&bIşınlanıyorsun... &f" + left);
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Araçtan / bindirilenlerden kurtarıp ışınlar, düşme hasarını sıfırlar. */
    private void doTp(Player p, Location dest, String label) {
        if (p.isInsideVehicle()) p.leaveVehicle();
        if (!p.getPassengers().isEmpty()) p.eject();
        if (!p.teleport(dest)) {
            plugin.msg(p, "&cIşınlanma başarısız oldu, tekrar dene.");
            return;
        }
        p.setFallDistance(0f);
        plugin.msg(p, "&a" + label + " &7konumuna ışınlandın.");
    }



    // ---------- Kaldığın yerden devam / ölünce spawn ----------
    /** AuthMe varsa oyuncunun giriş yapıp yapmadığını sorar (yoksa true). */
    private boolean authed(Player p) {
        Plugin am = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (am == null || !am.isEnabled()) return true;
        try {
            Class<?> api = am.getClass().getClassLoader().loadClass("fr.xephi.authme.api.v3.AuthMeApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return (Boolean) api.getMethod("isAuthenticated", Player.class).invoke(inst, p);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Ölen oyuncu: kayıtlı konumu silinir, yeniden doğunca (yatağı olsa bile) spawn'a gider. */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        y.set("died." + id, true);
        y.set("last." + id, null);
        save();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!y.getBoolean("died." + id, false)) return; // ölüm değil (örn. End çıkışı), dokunma
        e.setRespawnLocation(spawnLoc());
        y.set("died." + id, null);
        save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        requests.remove(id);
        for (Map<UUID, Req> m : requests.values()) m.remove(id);
        tpaCooldown.remove(id);
        busy.remove(id);

        // Çıkış konumunu kaydet (ölüyse, giriş yapmamışsa ya da AFK odasındaysa kaydetme)
        boolean afkWorld = plugin.afk() != null && plugin.afk().isAfkWorld(p.getWorld());
        if (p.isDead() || p.getHealth() <= 0 || afkWorld || !authed(p) || y.getBoolean("died." + id, false)) return;
        Util.putLoc(y, "last." + id, p.getLocation());
        save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        final UUID id = p.getUniqueId();
        final boolean died = y.getBoolean("died." + id, false);
        final Location saved = Util.getLoc(y, "last." + id);
        if (!died && saved == null) return;

        // Giriş (AuthMe) tamamlanınca çalışır; en fazla 90 sn bekler
        new BukkitRunnable() {
            int tries = 0;

            @Override
            public void run() {
                if (!p.isOnline() || ++tries > 90) {
                    cancel();
                    return;
                }
                if (!authed(p)) return;
                cancel();
                Bukkit.getScheduler().runTaskLater(plugin, () -> restore(p, id, died, saved), 20L);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void restore(Player p, UUID id, boolean died, Location saved) {
        if (!p.isOnline()) return;
        if (died) {
            // Ölürken çıkmış: yatağı olsa bile spawn
            y.set("died." + id, null);
            y.set("last." + id, null);
            save();
            p.teleport(spawnLoc());
            plugin.msg(p, "&7Öldüğün için spawn noktasına döndün.");
            return;
        }
        if (saved == null || saved.getWorld() == null) return;
        if (plugin.afk() != null && plugin.afk().isAfkWorld(saved.getWorld())) return;
        Location cur = p.getLocation();
        if (cur.getWorld().equals(saved.getWorld()) && cur.distanceSquared(saved) < 16) return; // zaten orada
        if (p.isInsideVehicle()) p.leaveVehicle();
        if (p.teleport(saved)) plugin.msg(p, "&7Kaldığın yerden devam ediyorsun.");
    }

    /** Bekleyen isteği alır: isim verilirse o oyuncunun, verilmezse en yeni istek. Süresi dolanlar silinir. */
    private Req takeRequest(Player p, String fromName) {
        Map<UUID, Req> m = requests.get(p.getUniqueId());
        if (m == null) return null;
        long now = System.currentTimeMillis();
        m.values().removeIf(r -> r.expire < now);
        Req pick = null;
        for (Req r : m.values()) {
            if (fromName != null) {
                Player f = Bukkit.getPlayer(r.from);
                if (f != null && f.getName().equalsIgnoreCase(fromName)) pick = r;
            } else if (pick == null || r.expire > pick.expire) {
                pick = r;
            }
        }
        if (pick != null) m.remove(pick.from);
        if (m.isEmpty()) requests.remove(p.getUniqueId());
        return pick;
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
                if (plugin.afk() != null && plugin.afk().isAfk(p)) {
                    plugin.msg(p, "&cAFK odasında ev kuramazsın.");
                    break;
                }
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
                if (plugin.afk() != null && plugin.afk().isAfk(t)) {
                    plugin.msg(p, "&cBu oyuncu AFK odasında, ona ışınlanamazsın.");
                    break;
                }
                long now = System.currentTimeMillis();
                Long cd = tpaCooldown.get(p.getUniqueId());
                if (cd != null && cd > now) {
                    plugin.msg(p, "&cÇok hızlı istek gönderiyorsun, &e" + ((cd - now) / 1000 + 1) + " sn &cbekle.");
                    break;
                }
                tpaCooldown.put(p.getUniqueId(), now + 5000L);
                Req r = new Req();
                r.from = p.getUniqueId();
                r.here = c.getName().equalsIgnoreCase("tpahere");
                r.expire = now + 60000L;
                requests.computeIfAbsent(t.getUniqueId(), k -> new HashMap<>()).put(p.getUniqueId(), r);
                plugin.msg(p, "&a" + t.getName() + " &7oyuncusuna istek gönderildi. &8(60 sn, iptal: /tpcancel)");
                plugin.msg(t, "&e" + p.getName() + (r.here ? " &7seni kendisine ışınlamak istiyor." : " &7sana ışınlanmak istiyor."));
                plugin.msg(t, "&a/tpaccept " + p.getName() + " &7kabul, &c/tpdeny " + p.getName() + " &7reddet.");
                break;
            }
            case "tpaccept": {
                Req r = takeRequest(p, a.length > 0 ? a[0] : null);
                if (r == null) { plugin.msg(p, "&cBekleyen isteğin yok."); break; }
                Player from = Bukkit.getPlayer(r.from);
                if (from == null) { plugin.msg(p, "&cİstek gönderen oyuncu çevrimdışı."); break; }
                plugin.msg(p, "&aİstek kabul edildi.");
                plugin.msg(from, "&a" + p.getName() + " &7isteğini kabul etti.");
                if (r.here) tp(p, from.getLocation(), from.getName());
                else tp(from, p.getLocation(), p.getName());
                break;
            }
            case "tpdeny": {
                Req r = takeRequest(p, a.length > 0 ? a[0] : null);
                if (r == null) { plugin.msg(p, "&cBekleyen isteğin yok."); break; }
                Player from = Bukkit.getPlayer(r.from);
                plugin.msg(p, "&eİstek reddedildi.");
                if (from != null) plugin.msg(from, "&c" + p.getName() + " &7isteğini reddetti.");
                break;
            }
            case "tpcancel": {
                boolean any = false;
                for (Map<UUID, Req> m : requests.values()) any |= m.remove(p.getUniqueId()) != null;
                plugin.msg(p, any ? "&eGönderdiğin istekler iptal edildi." : "&cİptal edilecek isteğin yok.");
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
