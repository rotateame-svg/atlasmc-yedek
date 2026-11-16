package net.atlasmc;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

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

/**
 * Chunk (16x16) bazlı arazi koruması + spawn dünyası koruması.
 * /claim /unclaim /claims /trust /untrust /trustlist
 */
public class ClaimManager implements CommandExecutor, Listener {

    private static class Claim {
        UUID owner;
        String ownerName;
        String world;
        int cx, cz;
    }

    private final AtlasMC plugin;
    private final File file;
    private final Map<String, Claim> claims = new HashMap<>();
    private final Map<UUID, Set<UUID>> trust = new HashMap<>();

    public ClaimManager(AtlasMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        load();
    }

    // ---------- Anahtarlar / kontroller ----------
    private static String key(String world, int cx, int cz) {
        return world + ";" + cx + ";" + cz;
    }

    private static String key(Block b) {
        return key(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4);
    }

    private static String key(Location l) {
        return key(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    private boolean claimWorld(String w) {
        List<String> worlds = plugin.getConfig().getStringList("claim.worlds");
        if (worlds.isEmpty()) worlds = List.of("smp", "smp_nether", "smp_the_end");
        return worlds.contains(w);
    }

    private String spawnWorld() {
        return plugin.getConfig().getString("spawn.protect-world", "spawn");
    }

    private boolean bypass(Player p) {
        return Util.admin(p) || p.hasPermission("atlasmc.claim.bypass");
    }

    /** Oyuncu bu bloğa dokunabilir mi? */
    private boolean allowed(Player p, Block b) {
        if (bypass(p)) return true;
        if (b.getWorld().getName().equals(spawnWorld())) return false;
        Claim c = claims.get(key(b));
        if (c == null) return true;
        if (c.owner.equals(p.getUniqueId())) return true;
        Set<UUID> t = trust.get(c.owner);
        return t != null && t.contains(p.getUniqueId());
    }

    private void deny(Player p, Block b) {
        if (b.getWorld().getName().equals(spawnWorld())) {
            Util.bar(p, "&cSpawn bölgesinde bunu yapamazsın!");
            return;
        }
        Claim c = claims.get(key(b));
        Util.bar(p, "&cBu arazi &f" + (c == null ? "?" : c.ownerName) + " &cadlı oyuncuya ait!");
    }

    private boolean protectedBlock(Block b) {
        return b.getWorld().getName().equals(spawnWorld()) || claims.containsKey(key(b));
    }

    // ---------- Komutlar ----------
    private int limit(Player p) {
        if (p.isOp()) return 9999;
        return Util.permNumber(p, "atlasmc.claims.", plugin.getConfig().getInt("claim.default-limit", 10));
    }

    private int count(UUID id) {
        int n = 0;
        for (Claim c : claims.values()) if (c.owner.equals(id)) n++;
        return n;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        Player p = (Player) s;
        switch (cmd.getName().toLowerCase()) {
            case "claim": {
                if (a.length > 0 && (a[0].equalsIgnoreCase("bilgi") || a[0].equalsIgnoreCase("info"))) {
                    Claim c = claims.get(key(p.getLocation()));
                    plugin.msg(p, c == null ? "&7Bu bölge sahipsiz." : "&7Bu arazi: &f" + c.ownerName + "&7'a ait.");
                    break;
                }
                String w = p.getWorld().getName();
                if (!claimWorld(w)) { plugin.msg(p, "&cBu dünyada arazi alamazsın."); break; }
                String k = key(p.getLocation());
                if (claims.containsKey(k)) {
                    Claim ex = claims.get(k);
                    plugin.msg(p, "&cBu bölge zaten &f" + ex.ownerName + " &cadlı oyuncuya ait.");
                    break;
                }
                if (count(p.getUniqueId()) >= limit(p)) {
                    plugin.msg(p, "&cEn fazla &e" + limit(p) + " &carazi alabilirsin.");
                    break;
                }
                double cost = plugin.getConfig().getDouble("claim.cost", 0);
                if (cost > 0) {
                    Economy eco = plugin.getEconomy();
                    if (eco == null || !eco.has(p, cost)) {
                        plugin.msg(p, "&cYeterli paran yok! Gerekli: &a$" + AtlasMC.money(cost));
                        break;
                    }
                    eco.withdrawPlayer(p, cost);
                }
                Claim c = new Claim();
                c.owner = p.getUniqueId();
                c.ownerName = p.getName();
                c.world = w;
                c.cx = p.getLocation().getBlockX() >> 4;
                c.cz = p.getLocation().getBlockZ() >> 4;
                claims.put(k, c);
                save();
                plugin.msg(p, "&aBu bölge (16x16) artık senin! &8(" + count(p.getUniqueId()) + "/" + limit(p) + ")");
                break;
            }
            case "unclaim": {
                String k = key(p.getLocation());
                Claim c = claims.get(k);
                if (c == null) { plugin.msg(p, "&cBu bölge sahipsiz."); break; }
                if (!c.owner.equals(p.getUniqueId()) && !Util.admin(p)) { plugin.msg(p, "&cBu arazi sana ait değil."); break; }
                claims.remove(k);
                save();
                plugin.msg(p, "&eArazi bırakıldı.");
                break;
            }
            case "claims": {
                List<String> list = new ArrayList<>();
                for (Claim c : claims.values())
                    if (c.owner.equals(p.getUniqueId()) && list.size() < 15)
                        list.add("&7" + c.world + " &8(" + (c.cx * 16) + ", " + (c.cz * 16) + ")");
                plugin.msg(p, "&7Araziler: &e" + count(p.getUniqueId()) + "&7/&e" + limit(p));
                for (String l : list) p.sendMessage(color(" " + l));
                break;
            }
            case "trust": {
                if (a.length < 1) { plugin.msg(p, "&cKullanım: &e/trust <oyuncu>"); break; }
                OfflinePlayer t = Util.findPlayer(a[0]);
                if (t == null || t.getUniqueId().equals(p.getUniqueId())) { plugin.msg(p, "&cOyuncu bulunamadı."); break; }
                trust.computeIfAbsent(p.getUniqueId(), x -> new HashSet<>()).add(t.getUniqueId());
                save();
                plugin.msg(p, "&a" + t.getName() + " &7artık araziler üzerinde inşa edebilir.");
                break;
            }
            case "untrust": {
                if (a.length < 1) { plugin.msg(p, "&cKullanım: &e/untrust <oyuncu>"); break; }
                OfflinePlayer t = Util.findPlayer(a[0]);
                Set<UUID> set = trust.get(p.getUniqueId());
                if (t == null || set == null || !set.remove(t.getUniqueId())) { plugin.msg(p, "&cBu oyuncuya zaten yetki vermemişsin."); break; }
                save();
                plugin.msg(p, "&e" + t.getName() + " &7yetkisi alındı.");
                break;
            }
            case "trustlist": {
                Set<UUID> set = trust.get(p.getUniqueId());
                if (set == null || set.isEmpty()) { plugin.msg(p, "&7Kimseye yetki vermemişsin."); break; }
                List<String> names = new ArrayList<>();
                for (UUID u : set) {
                    String n = Bukkit.getOfflinePlayer(u).getName();
                    names.add(n == null ? u.toString().substring(0, 8) : n);
                }
                plugin.msg(p, "&7Yetkili oyuncular: &e" + String.join("&7, &e", names));
                break;
            }
            default:
                break;
        }
        return true;
    }

    // ---------- Koruma olayları ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!allowed(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer(), e.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!allowed(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer(), e.getBlock());
        }
    }

    private boolean interactive(Block b) {
        String n = b.getType().name();
        if (n.contains("DOOR") || n.contains("GATE") || n.contains("BUTTON") || n.equals("LEVER")
                || n.contains("ANVIL") || n.contains("BED") || n.equals("BEACON") || n.contains("CAULDRON")
                || n.equals("COMPOSTER") || n.equals("LECTERN") || n.equals("JUKEBOX") || n.equals("NOTE_BLOCK")
                || n.equals("REPEATER") || n.equals("COMPARATOR") || n.equals("DAYLIGHT_DETECTOR")
                || n.contains("SIGN") || n.equals("FLOWER_POT") || n.equals("CAKE") || n.equals("RESPAWN_ANCHOR")
                || n.equals("ENCHANTING_TABLE") || n.equals("GRINDSTONE") || n.equals("LOOM")
                || n.equals("STONECUTTER") || n.equals("CARTOGRAPHY_TABLE") || n.equals("SMITHING_TABLE"))
            return true;
        return b.getState() instanceof Container;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();
        if (e.getAction() == Action.PHYSICAL) {
            String n = b.getType().name();
            if ((n.equals("FARMLAND") || n.equals("TURTLE_EGG")) && !allowed(p, b)) e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!protectedBlock(b) || allowed(p, b)) return;
        if (b.getWorld().getName().equals(spawnWorld()) && !(b.getState() instanceof Container)) return;
        if (interactive(b)) {
            e.setCancelled(true);
            deny(p, b);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!allowed(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer(), e.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!allowed(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer(), e.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && !allowed(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = shooter(e.getRemover());
        if (p != null && !allowed(p, e.getEntity().getLocation().getBlock())) e.setCancelled(true);
    }

    private Player shooter(Entity d) {
        if (d instanceof Player) return (Player) d;
        if (d instanceof Projectile && ((Projectile) d).getShooter() instanceof Player)
            return (Player) ((Projectile) d).getShooter();
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player p = shooter(e.getDamager());
        if (p == null) return;
        Entity v = e.getEntity();
        Block b = v.getLocation().getBlock();

        // Spawn dünyasında oyuncu-oyuncu savaşı kapalı (config)
        if (v instanceof Player && b.getWorld().getName().equals(spawnWorld())
                && plugin.getConfig().getBoolean("spawn.no-pvp", true) && !bypass(p)) {
            e.setCancelled(true);
            return;
        }
        if (v instanceof Hanging || v instanceof ArmorStand || v instanceof Animals || v instanceof AbstractVillager) {
            if (!allowed(p, b)) {
                e.setCancelled(true);
                deny(p, b);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Entity v = e.getRightClicked();
        if (v instanceof Hanging || v instanceof ArmorStand) {
            Block b = v.getLocation().getBlock();
            if (!allowed(e.getPlayer(), b)) {
                e.setCancelled(true);
                deny(e.getPlayer(), b);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (protectedBlock(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!protectedBlock(e.getBlock())) return;
        Player p = e.getPlayer();
        if (p == null || !allowed(p, e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobGrief(EntityChangeBlockEvent e) {
        Entity en = e.getEntity();
        if ((en instanceof Enderman || en instanceof Wither || en instanceof Ravager) && protectedBlock(e.getBlock()))
            e.setCancelled(true);
    }

    // ---------- Girdi / çıktı bilgisi ----------
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        if ((f.getBlockX() >> 4) == (t.getBlockX() >> 4) && (f.getBlockZ() >> 4) == (t.getBlockZ() >> 4)
                && f.getWorld().equals(t.getWorld())) return;
        Player p = e.getPlayer();
        Claim c = claims.get(key(t));
        if (c == null) {
            if (claims.containsKey(key(f))) Util.bar(p, "&7Sahipsiz bölge");
        } else if (c.owner.equals(p.getUniqueId())) {
            Util.bar(p, "&a✔ Senin arazin");
        } else {
            Util.bar(p, "&e⚑ " + c.ownerName + " adlı oyuncunun arazisi");
        }
    }

    // ---------- Kayıt ----------
    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Claim c : claims.values()) {
            String p = "claims." + (i++) + ".";
            y.set(p + "owner", c.owner.toString());
            y.set(p + "name", c.ownerName);
            y.set(p + "world", c.world);
            y.set(p + "cx", c.cx);
            y.set(p + "cz", c.cz);
        }
        for (Map.Entry<UUID, Set<UUID>> e : trust.entrySet()) {
            List<String> l = new ArrayList<>();
            for (UUID u : e.getValue()) l.add(u.toString());
            y.set("trust." + e.getKey(), l);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("claims.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cs = y.getConfigurationSection("claims");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(k);
                if (s == null) continue;
                try {
                    Claim c = new Claim();
                    c.owner = UUID.fromString(s.getString("owner"));
                    c.ownerName = s.getString("name", "?");
                    c.world = s.getString("world");
                    c.cx = s.getInt("cx");
                    c.cz = s.getInt("cz");
                    claims.put(key(c.world, c.cx, c.cz), c);
                } catch (Exception ignored) {
                }
            }
        }
        ConfigurationSection ts = y.getConfigurationSection("trust");
        if (ts != null) {
            for (String k : ts.getKeys(false)) {
                try {
                    Set<UUID> set = new HashSet<>();
                    for (String u : ts.getStringList(k)) set.add(UUID.fromString(u));
                    trust.put(UUID.fromString(k), set);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }
}
