package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Atlas AntiCheat: Speed, Fly, GroundSpoof, Reach, KillAura, MultiAura, HitRate, Nuker, BlockReach, WallBreak.
 * Varsayılan: sadece yetkililere uyarı + hareket hilelerinde geri çekme. Ceza (kick) config'den açılır.
 */
public class AntiCheat implements Listener {

    static class Data {
        final Map<String, Double> vl = new HashMap<>();
        final Map<String, Long> lastAlert = new HashMap<>();
        Location lastSafe;
        long lastTeleport, lastVelocity, lastDamage, lastMove;
        double winDist;
        long winStart;
        int airTicks, hover, spoof;
        double airRise;
        final Deque<Long> hits = new ArrayDeque<>();
        final Deque<Long> breaks = new ArrayDeque<>();
        final Map<UUID, Long> targets = new HashMap<>();
    }

    private final AtlasMC plugin;
    private final Map<UUID, Data> data = new HashMap<>();

    public AntiCheat(AtlasMC plugin) {
        this.plugin = plugin;
        // VL her 10 saniyede 1 azalır
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Data d : data.values()) d.vl.replaceAll((k, v) -> Math.max(0.0, v - 1.0));
        }, 200L, 200L);
    }

    private Data data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new Data());
    }

    private double cfg(String k, double def) {
        return plugin.getConfig().getDouble("anticheat." + k, def);
    }

    public boolean exempt(Player p) {
        if (!plugin.getConfig().getBoolean("anticheat.enabled", true)) return true;
        if (p.hasPermission("atlasmc.anticheat.bypass")) return true;
        if (p.isOp() && plugin.getConfig().getBoolean("anticheat.exempt-ops", true)) return true;
        GameMode g = p.getGameMode();
        return g == GameMode.CREATIVE || g == GameMode.SPECTATOR;
    }

    private boolean lagging() {
        try {
            Object r = Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            return ((double[]) r)[0] < 17.0;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------- Uyarı / ceza ----------
    public void flag(Player p, String check, String info, double add, boolean setback) {
        Data d = data(p);
        double v = d.vl.merge(check, add, Double::sum);
        if (v >= cfg("alert-vl", 3)) alert(p, d, check, info, v);

        if (setback && v >= cfg("setback-vl", 5) && d.lastSafe != null) {
            Location l = d.lastSafe.clone();
            l.setYaw(p.getLocation().getYaw());
            l.setPitch(p.getLocation().getPitch());
            d.lastTeleport = System.currentTimeMillis();
            p.teleport(l);
        }
        if (plugin.getConfig().getBoolean("anticheat.punish.enabled", false) && v >= cfg("punish.vl", 25)) {
            d.vl.put(check, 0.0);
            String cmd = plugin.getConfig().getString("anticheat.punish.command",
                    "kick {player} Hile şüphesi (AtlasMC AntiCheat)");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("{player}", p.getName()).replace("{check}", check));
        }
    }

    private void alert(Player p, Data d, String check, String info, double v) {
        long now = System.currentTimeMillis();
        Long last = d.lastAlert.get(check);
        if (last != null && now - last < 1500) return;
        d.lastAlert.put(check, now);
        String text = "&c&lAC &8» &f" + p.getName() + " &7→ &c" + check + " &7(" + info + ") &8VL: &e"
                + String.format("%.1f", v) + " &8| &7ping &f" + p.getPing();
        String msg = AtlasMC.color(text);
        for (Player s : Bukkit.getOnlinePlayers())
            if (s.hasPermission("atlasmc.anticheat.alerts")) s.sendMessage(msg);
        plugin.getLogger().info("[AC] " + p.getName() + " " + check + " (" + info + ") VL " + String.format("%.1f", v));
    }

    // ---------- Hareket ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (exempt(p)) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        if (dx == 0 && dy == 0 && dz == 0) return;

        Data d = data(p);
        long now = System.currentTimeMillis();
        boolean grace = now - d.lastTeleport < 2000 || now - d.lastVelocity < 3000 || now - d.lastDamage < 1500
                || p.isGliding() || p.isRiptiding() || p.isInsideVehicle()
                || p.getAllowFlight() || p.isFlying() || lagging();
        if (grace) {
            d.winStart = 0;
            d.winDist = 0;
            d.airTicks = 0;
            d.airRise = 0;
            d.hover = 0;
            d.spoof = 0;
            d.lastMove = now;
            return;
        }

        boolean ground = blockGround(to);
        if (ground) d.lastSafe = to.clone();

        // ---- Speed (1 saniyelik pencere) ----
        if (now - d.lastMove > 500) {
            d.winStart = 0;
            d.winDist = 0;
        }
        d.lastMove = now;
        if (d.winStart == 0) d.winStart = now;
        d.winDist += Math.hypot(dx, dz);
        long el = now - d.winStart;
        if (el >= 1000) {
            double bps = d.winDist / (el / 1000.0);
            double limit = 8.6 * (p.getWalkSpeed() / 0.2);
            PotionEffect sp = p.getPotionEffect(PotionEffectType.SPEED);
            if (sp != null) limit *= 1 + 0.2 * (sp.getAmplifier() + 1);
            if (p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) limit *= 2;
            if (slippery(to)) limit *= 1.8;
            if (bps > limit * 1.25)
                flag(p, "Speed", String.format("%.1f b/s, maks %.1f", bps, limit), 1.5, true);
            d.winStart = now;
            d.winDist = 0;
        }

        // ---- Fly / GroundSpoof ----
        boolean special = p.hasPotionEffect(PotionEffectType.LEVITATION)
                || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || climbOrLiquid(to);
        if (ground) {
            d.airTicks = 0;
            d.airRise = 0;
            d.hover = 0;
        } else {
            d.airTicks++;
            if (dy > 0) d.airRise += dy;
            if (!special) {
                if (d.airTicks > 12 && Math.abs(dy) < 0.02) {
                    if (++d.hover >= 6) {
                        flag(p, "Fly", "havada asılı kalma", 2, true);
                        d.hover = 0;
                    }
                } else if (dy < -0.08) {
                    d.hover = 0;
                }
                PotionEffect jb = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
                double maxRise = 1.4 + (jb == null ? 0 : 0.6 * (jb.getAmplifier() + 1));
                if (d.airRise > maxRise && !bounceBelow(to)) {
                    flag(p, "Fly", String.format("yükselme %.2f blok", d.airRise), 2, true);
                    d.airRise = 0;
                }
            }
        }
        if (p.isOnGround() && !ground && !special && !entitySupport(p)) {
            if (++d.spoof > 12) {
                flag(p, "GroundSpoof", "havada yerde görünüyor", 1.5, true);
                d.spoof = 0;
            }
        } else {
            d.spoof = 0;
        }
    }

    private boolean blockGround(Location l) {
        for (double ox = -0.3; ox <= 0.3; ox += 0.3)
            for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
                Block b = l.clone().add(ox, -0.1, oz).getBlock();
                if (!b.isPassable()) return true;
            }
        return false;
    }

    private boolean slippery(Location l) {
        String n = l.clone().subtract(0, 0.5, 0).getBlock().getType().name();
        return n.contains("ICE") || n.contains("SLIME") || n.contains("SOUL_SAND") || n.contains("SOUL_SOIL");
    }

    private boolean climbOrLiquid(Location l) {
        Block[] bs = {l.getBlock(), l.clone().add(0, 1, 0).getBlock(), l.clone().add(0, -0.5, 0).getBlock()};
        for (Block b : bs) {
            if (b.isLiquid()) return true;
            String n = b.getType().name();
            if (n.contains("LADDER") || n.contains("VINE") || n.contains("SCAFFOLDING") || n.contains("COBWEB")
                    || n.contains("SWEET_BERRY") || n.contains("HONEY") || n.contains("POWDER_SNOW")
                    || n.contains("BUBBLE") || n.contains("TWISTING") || n.contains("WEEPING"))
                return true;
        }
        return false;
    }

    private boolean bounceBelow(Location l) {
        for (int i = 1; i <= 3; i++) {
            String n = l.clone().add(0, -i, 0).getBlock().getType().name();
            if (n.contains("SLIME") || n.endsWith("_BED") || n.contains("HONEY")) return true;
        }
        return false;
    }

    private boolean entitySupport(Player p) {
        for (Entity en : p.getNearbyEntities(0.8, 1.5, 0.8)) {
            String n = en.getType().name();
            if (n.contains("BOAT") || n.contains("MINECART") || n.contains("SHULKER") || n.contains("RAFT"))
                return true;
        }
        return false;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Data d = data(e.getPlayer());
        d.lastTeleport = System.currentTimeMillis();
        if (e.getTo() != null) d.lastSafe = e.getTo().clone();
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent e) {
        data(e.getPlayer()).lastVelocity = System.currentTimeMillis();
    }

    @EventHandler
    public void onDamaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) data(p).lastDamage = System.currentTimeMillis();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        data.remove(e.getPlayer().getUniqueId());
    }

    // ---------- Savaş ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (exempt(p)) return;

        Entity victim = e.getEntity();
        Data d = data(p);
        long now = System.currentTimeMillis();

        // Vuruş hızı
        d.hits.addLast(now);
        while (!d.hits.isEmpty() && now - d.hits.peekFirst() > 1000) d.hits.pollFirst();
        if (d.hits.size() >= 16) flag(p, "HitRate", d.hits.size() + " vuruş/sn", 2, false);

        // Menzil
        Location eye = p.getEyeLocation();
        BoundingBox bb = victim.getBoundingBox();
        double cx = Math.max(Math.max(bb.getMinX() - eye.getX(), 0), eye.getX() - bb.getMaxX());
        double cy = Math.max(Math.max(bb.getMinY() - eye.getY(), 0), eye.getY() - bb.getMaxY());
        double cz = Math.max(Math.max(bb.getMinZ() - eye.getZ(), 0), eye.getZ() - bb.getMaxZ());
        double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double limit = 3.2 + Math.min(1.0, p.getPing() / 150.0 * 0.5);
        if (dist > limit)
            flag(p, "Reach", String.format("%.2f blok, maks %.2f", dist, limit), dist > limit + 0.8 ? 3 : 1.5, false);

        // Bakış açısı (KillAura)
        Vector toVictim = bb.getCenter().subtract(eye.toVector());
        if (toVictim.lengthSquared() > 2.25) {
            double ang = Math.toDegrees(eye.getDirection().angle(toVictim.normalize()));
            if (ang > 70) flag(p, "KillAura", String.format("açı %.0f°", ang), 2, false);
        }

        // Aynı anda birden fazla hedef
        d.targets.put(victim.getUniqueId(), now);
        d.targets.values().removeIf(t -> now - t > 500);
        if (d.targets.size() >= 3) flag(p, "MultiAura", d.targets.size() + " hedef/0.5sn", 3, false);
    }

    // ---------- Blok kırma ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (exempt(p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()
                && hand.getItemMeta().getDisplayName().contains("Kristal Kazma")) return;

        Data d = data(p);
        long now = System.currentTimeMillis();
        d.breaks.addLast(now);
        while (!d.breaks.isEmpty() && now - d.breaks.peekFirst() > 1000) d.breaks.pollFirst();
        if (d.breaks.size() > 22) flag(p, "Nuker", d.breaks.size() + " blok/sn", 2, false);

        Block b = e.getBlock();
        Location eye = p.getEyeLocation();
        double dist = eye.distance(b.getLocation().add(0.5, 0.5, 0.5));
        if (dist > 6.6) flag(p, "BlockReach", String.format("%.1f blok", dist), 2, false);

        RayTraceResult r = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), 7.0, FluidCollisionMode.NEVER, true);
        if (r != null && r.getHitBlock() != null && !r.getHitBlock().equals(b)) {
            double hd = eye.distance(r.getHitBlock().getLocation().add(0.5, 0.5, 0.5));
            if (dist - hd > 1.8) flag(p, "WallBreak", "duvarın arkasından kırma", 2, false);
        }
    }
}
