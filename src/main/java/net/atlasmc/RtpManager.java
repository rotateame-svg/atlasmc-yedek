package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static net.atlasmc.AtlasMC.color;

/** /rtp menüsü, RTP NPC'si ve rastgele ışınlanma. */
public class RtpManager implements CommandExecutor, Listener {

    private static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final AtlasMC plugin;
    private final NamespacedKey npcKey;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();

    public RtpManager(AtlasMC plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "rtp_npc");
        if (plugin.getConfig().getBoolean("rtp.auto-load-worlds", true)) {
            load(overworld(), World.Environment.NORMAL);
            load(nether(), World.Environment.NETHER);
            load(end(), World.Environment.THE_END);
        }
    }

    private String overworld() { return plugin.getConfig().getString("rtp.worlds.overworld", "smp"); }
    private String nether() { return plugin.getConfig().getString("rtp.worlds.nether", "smp_nether"); }
    private String end() { return plugin.getConfig().getString("rtp.worlds.end", "smp_the_end"); }

    private void load(String name, World.Environment env) {
        if (Bukkit.getWorld(name) != null) return;
        try {
            new WorldCreator(name).environment(env).createWorld();
        } catch (Exception ex) {
            plugin.getLogger().warning("Dünya yüklenemedi (" + name + "): " + ex.getMessage());
        }
    }

    // ---------- Komutlar ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("rtpnpcolustur")) return npcCommand(s, a);
        if (!(s instanceof Player p)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        open(p);
        return true;
    }

    private boolean npcCommand(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        if (!p.hasPermission("atlasmc.admin")) {
            plugin.msg(p, "&cBu komutu kullanamazsın.");
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("sil")) {
            int n = 0;
            for (Entity en : p.getNearbyEntities(5, 5, 5)) {
                if (isNpc(en)) {
                    en.remove();
                    n++;
                }
            }
            plugin.msg(p, n > 0 ? "&e" + n + " NPC silindi." : "&cYakında RTP NPC'si yok.");
            return true;
        }
        Location loc = p.getLocation();
        loc.setYaw(loc.getYaw() + 180f);
        loc.setPitch(0f);
        String name = plugin.getConfig().getString("rtp.npc-name", "&b&lIşınlanma Merkezi");
        p.getWorld().spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setCustomName(color(name));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        });
        plugin.msg(p, "&aRTP NPC'si oluşturuldu. Silmek için NPC'nin yanında &e/rtpnpcolustur sil");
        return true;
    }

    private boolean isNpc(Entity e) {
        return e != null && e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    // ---------- NPC olayları ----------
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!isNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        open(e.getPlayer());
    }

    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        if (isNpc(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (isNpc(e.getEntity())) e.setCancelled(true);
    }

    // ---------- Menü ----------
    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(color(s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack worldItem(Material m, String title, String worldName) {
        World w = Bukkit.getWorld(worldName);
        String online = w == null ? "&cKapalı" : "&f" + w.getPlayers().size();
        return named(m, title,
                "&7Rastgele bir konuma ışınlanırsın.",
                "&7Çevrimiçi: " + online,
                "",
                w == null ? "&cŞu an kullanılamıyor" : "&aTıkla ve ışınlan!");
    }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, color("&8Işınlanma Merkezi"));
        h.inv = inv;
        ItemStack glass = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);
        inv.setItem(11, worldItem(Material.GRASS_BLOCK, "&a&lDünya", overworld()));
        inv.setItem(13, worldItem(Material.NETHERRACK, "&c&lNether", nether()));
        inv.setItem(15, worldItem(Material.END_STONE, "&d&lEnd", end()));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        Player p = (Player) e.getWhoClicked();
        String target = switch (e.getSlot()) {
            case 11 -> overworld();
            case 13 -> nether();
            case 15 -> end();
            default -> null;
        };
        if (target == null) return;
        p.closeInventory();
        start(p, target);
    }

    // ---------- Işınlanma ----------
    private void start(Player p, String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            plugin.msg(p, "&cBu dünya şu an kapalı.");
            return;
        }
        UUID id = p.getUniqueId();
        if (busy.contains(id)) {
            plugin.msg(p, "&cZaten ışınlanıyorsun.");
            return;
        }
        long now = System.currentTimeMillis();
        int cd = plugin.getConfig().getInt("rtp.cooldown-seconds", 10);
        if (!p.hasPermission("atlasmc.admin")) {
            Long last = cooldown.get(id);
            if (last != null && now - last < cd * 1000L) {
                long left = (cd * 1000L - (now - last)) / 1000 + 1;
                plugin.msg(p, "&cTekrar ışınlanmak için &e" + left + " sn &cbeklemelisin.");
                return;
            }
        }
        busy.add(id);
        Location from = p.getLocation();
        int countdown = plugin.getConfig().getInt("rtp.countdown", 3);

        new BukkitRunnable() {
            int left = countdown;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    busy.remove(id);
                    cancel();
                    return;
                }
                Location now = p.getLocation();
                if (now.getWorld() != from.getWorld()
                        || now.getBlockX() != from.getBlockX()
                        || now.getBlockY() != from.getBlockY()
                        || now.getBlockZ() != from.getBlockZ()) {
                    busy.remove(id);
                    cancel();
                    plugin.msg(p, "&cHareket ettiğin için ışınlanma iptal edildi.");
                    return;
                }
                if (left <= 0) {
                    cancel();
                    find(p, w);
                    return;
                }
                p.sendTitle(color("&b&l" + left), color("&7Kıpırdama!"), 0, 22, 0);
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void find(Player p, World w) {
        UUID id = p.getUniqueId();
        int maxTries = plugin.getConfig().getInt("rtp.max-tries", 30);
        plugin.msg(p, "&7Uygun bir konum aranıyor...");
        new BukkitRunnable() {
            int tries = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    busy.remove(id);
                    cancel();
                    return;
                }
                tries++;
                Location loc = candidate(w);
                if (loc == null && tries >= maxTries && w.getEnvironment() == World.Environment.THE_END) {
                    int y = w.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    if (y > 5) loc = new Location(w, 0.5, y + 1, 0.5);
                }
                if (loc != null) {
                    cancel();
                    busy.remove(id);
                    cooldown.put(id, System.currentTimeMillis());
                    p.teleport(loc);
                    p.setFallDistance(0f);
                    p.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    plugin.msg(p, "&a" + w.getName() + " &7dünyasına ışınlandın! &8(X: " + loc.getBlockX() + ", Z: " + loc.getBlockZ() + ")");
                    return;
                }
                if (tries >= maxTries) {
                    cancel();
                    busy.remove(id);
                    plugin.msg(p, "&cUygun bir konum bulunamadı, tekrar dene.");
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private Location candidate(World w) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World.Environment env = w.getEnvironment();
        int max = plugin.getConfig().getInt("rtp.radius", 5000);
        int min = plugin.getConfig().getInt("rtp.min-radius", 300);
        if (env == World.Environment.THE_END) {
            max = plugin.getConfig().getInt("rtp.end-radius", 4000);
            min = plugin.getConfig().getInt("rtp.end-min-radius", 1100);
        }
        double half = w.getWorldBorder().getSize() / 2.0 - 32;
        if (half < max) max = (int) Math.max(16, half);
        if (min >= max) min = 0;

        Location c = w.getWorldBorder().getCenter();
        double dist = Math.sqrt(r.nextDouble((double) min * min, (double) max * max + 1));
        double ang = r.nextDouble(Math.PI * 2);
        int x = (int) Math.round(c.getX() + Math.cos(ang) * dist);
        int z = (int) Math.round(c.getZ() + Math.sin(ang) * dist);

        if (env == World.Environment.NETHER) {
            for (int y = 100; y >= 33; y--) {
                Block g = w.getBlockAt(x, y, z);
                if (!g.getType().isSolid() || !safeGround(g)) continue;
                Block a = g.getRelative(0, 1, 0);
                Block b = g.getRelative(0, 2, 0);
                if (a.isPassable() && !a.isLiquid() && b.isPassable() && !b.isLiquid() && !hazardNear(w, x, y, z))
                    return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
            return null;
        }

        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (env == World.Environment.THE_END && y < 5) return null;
        Block g = w.getBlockAt(x, y, z);
        if (!safeGround(g)) return null;
        Block a = g.getRelative(0, 1, 0);
        Block b = g.getRelative(0, 2, 0);
        if (!a.isPassable() || a.isLiquid() || !b.isPassable() || b.isLiquid()) return null;
        if (hazardNear(w, x, y, z)) return null;
        return new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    private boolean safeGround(Block b) {
        Material t = b.getType();
        if (t.isAir() || b.isLiquid()) return false;
        String n = t.name();
        return !(n.endsWith("LEAVES") || n.contains("MAGMA") || n.contains("CACTUS") || n.contains("FIRE")
                || n.contains("LAVA") || n.contains("POWDER_SNOW") || n.contains("CAMPFIRE")
                || n.contains("SWEET_BERRY") || n.contains("WITHER_ROSE") || n.contains("COBWEB")
                || n.contains("POINTED_DRIPSTONE") || n.equals("BEDROCK"));
    }

    private boolean hazardNear(World w, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = -1; dy <= 2; dy++) {
                    Material t = w.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (t == Material.LAVA || t == Material.FIRE || t == Material.SOUL_FIRE
                            || t == Material.MAGMA_BLOCK || t == Material.CACTUS || t == Material.WITHER_ROSE)
                        return true;
                }
        return false;
    }
}
