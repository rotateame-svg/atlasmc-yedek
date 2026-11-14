package net.atlasmc;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * X-ray tespiti: yeraltında kırılan taşa karşı nadir cevher (elmas, antik kalıntı, zümrüt) damar oranını izler.
 * Aynı damar bir kez sayılır. Şüphe olunca yetkililere uyarı gider.
 */
public class AntiXray implements Listener {

    static class Data {
        int total, ores;
        Location lastOre;
        long lastOreTime;
        final Deque<Long> veins = new ArrayDeque<>();
    }

    private final AtlasMC plugin;
    private final AntiCheat ac;
    private final Map<UUID, Data> map = new HashMap<>();

    public AntiXray(AtlasMC plugin, AntiCheat ac) {
        this.plugin = plugin;
        this.ac = ac;
    }

    private boolean rare(String n) {
        return n.equals("DIAMOND_ORE") || n.equals("DEEPSLATE_DIAMOND_ORE") || n.equals("ANCIENT_DEBRIS")
                || n.equals("EMERALD_ORE") || n.equals("DEEPSLATE_EMERALD_ORE");
    }

    private boolean filler(String n, int y) {
        if (y >= 64) return false;
        return n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("ANDESITE") || n.equals("DIORITE")
                || n.equals("GRANITE") || n.equals("TUFF") || n.equals("NETHERRACK") || n.equals("BASALT")
                || n.equals("BLACKSTONE");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || ac.exempt(p)) return;

        Block b = e.getBlock();
        String n = b.getType().name();
        boolean isRare = rare(n);
        boolean isFiller = filler(n, b.getY());
        if (!isRare && !isFiller) return;

        Data d = map.computeIfAbsent(p.getUniqueId(), k -> new Data());
        long now = System.currentTimeMillis();

        if (isFiller) {
            d.total++;
        } else {
            // Aynı damarın devamıysa tekrar sayma
            if (d.lastOre != null && d.lastOre.getWorld() == b.getWorld() && now - d.lastOreTime < 120000
                    && d.lastOre.distanceSquared(b.getLocation()) <= 36) {
                d.lastOre = b.getLocation();
                d.lastOreTime = now;
                return;
            }
            d.lastOre = b.getLocation();
            d.lastOreTime = now;
            d.ores++;
            d.veins.addLast(now);
            while (!d.veins.isEmpty() && now - d.veins.peekFirst() > 600000) d.veins.pollFirst();
            int maxVeins = plugin.getConfig().getInt("anticheat.xray.veins-per-10min", 8);
            if (d.veins.size() >= maxVeins) {
                ac.flag(p, "XRay", "10 dk'da " + d.veins.size() + " nadir damar", 3, false);
                d.veins.clear();
            }
        }

        if (d.total >= 60 || d.ores >= 3) {
            double ratio = (double) d.ores / Math.max(1, d.total);
            double max = plugin.getConfig().getDouble("anticheat.xray.ratio", 0.06);
            if (d.ores > 0 && ratio > max)
                ac.flag(p, "XRay", String.format("cevher oranı %.3f (%d/%d)", ratio, d.ores, d.total), 3, false);
            d.total = 0;
            d.ores = 0;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        map.remove(e.getPlayer().getUniqueId());
    }
}
