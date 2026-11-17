package net.atlasmc;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static net.atlasmc.AtlasMC.color;

/** Savaş kilidi: oyuncu vuruşunda 15 sn boyunca hiçbir komut kullanılamaz, çıkarsa ölür. */
public class CombatManager implements Listener {

    private static CombatManager instance;

    /** Başka sınıflardan (RTP NPC vb.) savaş kontrolü. */
    public static boolean isCombat(Player p) {
        return instance != null && instance.isTagged(p);
    }

    private final AtlasMC plugin;
    private final Map<UUID, Long> until = new HashMap<>();

    public CombatManager(AtlasMC plugin) {
        this.plugin = plugin;
        instance = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private int seconds() {
        return plugin.getConfig().getInt("combat.seconds", 15);
    }

    public boolean isTagged(Player p) {
        Long t = until.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    private void tag(Player p) {
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            if (p.getAllowFlight() || p.isFlying()) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
        boolean was = isTagged(p);
        until.put(p.getUniqueId(), System.currentTimeMillis() + seconds() * 1000L);
        if (!was) plugin.msg(p, "&c&lSAVAŞTASIN! &7" + seconds() + " saniye boyunca hiçbir komut kullanamazsın.");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            long left = e.getValue() - now;
            if (left <= 0) {
                it.remove();
                plugin.msg(p, "&aArtık savaşta değilsin, komutları kullanabilirsin.");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color("&aSavaş bitti")));
            } else {
                long sn = (left + 999) / 1000;
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&c⚔ Savaştasın! &f" + sn + " sn")));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Entity d = e.getDamager();
        Player attacker = null;
        if (d instanceof Player) attacker = (Player) d;
        else if (d instanceof Projectile && ((Projectile) d).getShooter() instanceof Player)
            attacker = (Player) ((Projectile) d).getShooter();
        if (attacker == null || attacker.equals(victim)) return;
        GameMode g = attacker.getGameMode();
        if (g == GameMode.CREATIVE || g == GameMode.SPECTATOR) return;
        tag(victim);
        tag(attacker);
    }

    /** Savaştayken HİÇBİR komut kullanılamaz. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!isTagged(p)) return;
        e.setCancelled(true);
        long left = (until.get(p.getUniqueId()) - System.currentTimeMillis() + 999) / 1000;
        plugin.msg(p, "&cSavaştayken komut kullanamazsın! &7Kalan: &e" + left + " sn");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        until.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!isTagged(p)) {
            until.remove(p.getUniqueId());
            return;
        }
        until.remove(p.getUniqueId());
        if (plugin.getConfig().getBoolean("combat.kill-on-quit", true)) {
            Bukkit.broadcastMessage(color(AtlasMC.PREFIX + "&e" + p.getName() + " &cSavaştayken çıktığı için öldürüldü!"));
            p.setHealth(0.0);
        }
    }
}
