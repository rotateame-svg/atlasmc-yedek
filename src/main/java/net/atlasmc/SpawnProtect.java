package net.atlasmc;

import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Spawn dünyası koruması: yöneticiler dışında kırma, koyma, sandık, patlama ve (config'e göre) PvP kapalı. */
public class SpawnProtect implements Listener {

    private final AtlasMC plugin;

    public SpawnProtect(AtlasMC plugin) {
        this.plugin = plugin;
    }

    private String world() {
        return plugin.getConfig().getString("spawn.protect-world", "spawn");
    }

    private boolean inSpawn(Block b) {
        return b.getWorld().getName().equals(world());
    }

    private boolean bypass(Player p) {
        return Util.admin(p) || p.hasPermission("atlasmc.spawn.bypass");
    }

    private boolean blocked(Player p, Block b) {
        return inSpawn(b) && !bypass(p);
    }

    private void deny(Player p) {
        Util.bar(p, "&cSpawn bölgesinde bunu yapamazsın!");
    }

    private Player shooter(Entity d) {
        if (d instanceof Player) return (Player) d;
        if (d instanceof Projectile && ((Projectile) d).getShooter() instanceof Player)
            return (Player) ((Projectile) d).getShooter();
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (blocked(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (blocked(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (blocked(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (blocked(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && blocked(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = shooter(e.getRemover());
        if (p != null && blocked(p, e.getEntity().getLocation().getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !blocked(e.getPlayer(), b)) return;
        if (e.getAction() == Action.PHYSICAL) {
            String n = b.getType().name();
            if (n.equals("FARMLAND") || n.equals("TURTLE_EGG")) e.setCancelled(true);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && b.getState() instanceof Container) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Entity v = e.getRightClicked();
        if ((v instanceof Hanging || v instanceof ArmorStand) && blocked(e.getPlayer(), v.getLocation().getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player p = shooter(e.getDamager());
        if (p == null) return;
        Entity v = e.getEntity();
        Block b = v.getLocation().getBlock();
        if (!inSpawn(b) || bypass(p)) return;
        if (v instanceof Player) {
            if (plugin.getConfig().getBoolean("spawn.no-pvp", true)) e.setCancelled(true);
        } else if (v instanceof Hanging || v instanceof ArmorStand || v instanceof Animals || v instanceof AbstractVillager) {
            e.setCancelled(true);
            deny(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::inSpawn);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::inSpawn);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (inSpawn(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!inSpawn(e.getBlock())) return;
        Player p = e.getPlayer();
        if (p == null || !bypass(p)) e.setCancelled(true);
    }
}
