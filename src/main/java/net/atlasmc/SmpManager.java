package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.atlasmc.AtlasMC.color;

/**
 * SMP için eksik kalanlar: başlangıç paketi, ölüm konumu, yer temizliği (ClearLag), mob sınırı,
 * uyku oranı, /vanish, /seen, /kurallar, /discord.
 */
public class SmpManager implements CommandExecutor, Listener {

    private final AtlasMC plugin;
    private final Set<UUID> vanished = new HashSet<>();
    private int clearLeft;

    public SmpManager(AtlasMC plugin) {
        this.plugin = plugin;
        sleepRule(plugin.getConfig().getInt("sleep.percentage", 30));
        startClearLag();
    }

    // ---------- Uyku oranı ----------
    @SuppressWarnings("unchecked")
    private void sleepRule(int pct) {
        if (pct <= 0) return;
        for (World w : Bukkit.getWorlds()) {
            for (String n : new String[]{"playersSleepingPercentage", "players_sleeping_percentage"}) {
                try {
                    GameRule<?> r = GameRule.getByName(n);
                    if (r != null && Integer.class.equals(r.getType())) {
                        w.setGameRule((GameRule<Integer>) r, pct);
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------- ClearLag ----------
    private void startClearLag() {
        if (!plugin.getConfig().getBoolean("clearlag.enabled", true)) return;
        final int interval = Math.max(1, plugin.getConfig().getInt("clearlag.interval-minutes", 10)) * 60;
        clearLeft = interval;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            clearLeft--;
            if (clearLeft == 30 || clearLeft == 10 || clearLeft == 5)
                Bukkit.broadcastMessage(color(AtlasMC.PREFIX + "&eYerdeki eşyalar &c" + clearLeft + " saniye &eiçinde temizlenecek!"));
            if (clearLeft <= 0) {
                clearLeft = interval;
                int n = 0;
                for (World w : Bukkit.getWorlds()) {
                    for (Item it : w.getEntitiesByClass(Item.class)) {
                        if (it.getTicksLived() < 200) continue;
                        it.remove();
                        n++;
                    }
                }
                Bukkit.broadcastMessage(color(AtlasMC.PREFIX + "&a" + n + " &7yerdeki eşya temizlendi."));
            }
        }, 20L, 20L);
    }

    // ---------- Mob sınırı (üretme / yumurta) ----------
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r != CreatureSpawnEvent.SpawnReason.BREEDING && r != CreatureSpawnEvent.SpawnReason.EGG
                && r != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG && r != CreatureSpawnEvent.SpawnReason.DISPENSE_EGG)
            return;
        int limit = plugin.getConfig().getInt("mobs.per-chunk-limit", 60);
        if (limit <= 0) return;
        Chunk ch = e.getLocation().getChunk();
        int n = 0;
        for (Entity en : ch.getEntities())
            if (en instanceof LivingEntity && !(en instanceof Player) && !(en instanceof ArmorStand)) n++;
        if (n >= limit) e.setCancelled(true);
    }

    // ---------- Başlangıç paketi / ölüm konumu / vanish girişleri ----------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        for (UUID v : vanished) {
            Player vp = Bukkit.getPlayer(v);
            if (vp != null) p.hidePlayer(plugin, vp);
        }
        if (!p.hasPlayedBefore() && plugin.getConfig().getBoolean("starter.enabled", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                String[] items = {"STONE_SWORD:1", "STONE_PICKAXE:1", "STONE_AXE:1", "STONE_SHOVEL:1",
                        "COOKED_BEEF:16", "TORCH:16", "OAK_BOAT:1"};
                for (String spec : items) {
                    String[] s = spec.split(":");
                    Material m = Material.matchMaterial(s[0]);
                    if (m != null) Items.giveOrDrop(p, new ItemStack(m, Integer.parseInt(s[1])));
                }
                plugin.msg(p, "&aHoş geldin! Başlangıç paketin envanterine eklendi. &7Komutlar için &e/yardim");
            }, 60L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        vanished.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Location l = p.getLocation();
        plugin.msg(p, "&cÖldüğün konum: &f" + l.getWorld().getName() + " &7X: &f" + l.getBlockX()
                + " &7Y: &f" + l.getBlockY() + " &7Z: &f" + l.getBlockZ());
    }

    // ---------- Komutlar ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase()) {
            case "vanish":
                vanish(s);
                break;
            case "seen":
                seen(s, a);
                break;
            case "kurallar": {
                List<String> rules = plugin.getConfig().getStringList("rules");
                if (rules.isEmpty()) {
                    rules = List.of("Hile, x-ray ve bug kullanmak yasaktır.",
                            "Küfür, hakaret ve reklam yasaktır.",
                            "Başkasının arazisine / eşyasına zarar vermek yasaktır.",
                            "Yetkililere saygılı ol, kararlarına uy.");
                }
                s.sendMessage(color("&b&lSunucu Kuralları"));
                int i = 1;
                for (String r : rules) s.sendMessage(color("&e" + (i++) + ". &7" + r));
                break;
            }
            case "discord": {
                String link = plugin.getConfig().getString("links.discord", "");
                if (link.isEmpty()) plugin.msg(s, "&cDiscord linki ayarlanmamış. &7(config.yml → links.discord)");
                else plugin.msg(s, "&9Discord: &f" + link);
                break;
            }
            default:
                break;
        }
        return true;
    }

    private void vanish(CommandSender s) {
        if (!(s instanceof Player)) { s.sendMessage("Sadece oyuncular kullanabilir."); return; }
        Player p = (Player) s;
        if (!Util.admin(p)) { plugin.msg(p, "&cBu komutu kullanamazsın."); return; }
        if (vanished.remove(p.getUniqueId())) {
            for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, p);
            plugin.msg(p, "&eVanish kapatıldı, artık görünüyorsun.");
        } else {
            vanished.add(p.getUniqueId());
            for (Player o : Bukkit.getOnlinePlayers()) if (!Util.admin(o)) o.hidePlayer(plugin, p);
            plugin.msg(p, "&aVanish açıldı, oyuncular seni göremez.");
        }
    }

    private void seen(CommandSender s, String[] a) {
        if (a.length < 1) { plugin.msg(s, "&cKullanım: &e/seen <oyuncu>"); return; }
        OfflinePlayer t = Util.findPlayer(a[0]);
        if (t == null) { plugin.msg(s, "&cBu oyuncu sunucuya hiç girmemiş."); return; }
        if (t.isOnline() && !vanished.contains(t.getUniqueId())) {
            plugin.msg(s, "&a" + t.getName() + " &7şu an çevrimiçi.");
            return;
        }
        long m = (System.currentTimeMillis() - t.getLastPlayed()) / 60000;
        String ago = m >= 1440 ? (m / 1440) + " gün" : m >= 60 ? (m / 60) + " saat" : m + " dakika";
        plugin.msg(s, "&e" + t.getName() + " &7en son &f" + ago + " &7önce görüldü.");
    }
}
