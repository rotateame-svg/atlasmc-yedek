package net.atlaskasa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static net.atlaskasa.AtlasKasa.color;
import static net.atlaskasa.AtlasKasa.msg;
import static net.atlaskasa.AtlasKasa.strip;

/** Çok tipli kasa sistemi: her tipin kendi kasa bloğu, hologramı ve KENDİ anahtarı vardır. */
public class CrateManager implements Listener {

    static class Type {
        String id, name, block, keyName, keyMaterial, holo;
    }

    static class Crate {
        int id;
        String type = "oyuncu";
        String world;
        int x, y, z;
        UUID holo;
        final List<ItemStack> items = new ArrayList<>();
    }

    private static class PreviewHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static class AnimHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final AtlasKasa plugin;
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final Map<String, Crate> crates = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();
    private final Set<UUID> animating = new HashSet<>();
    private int nextId = 1;
    private final File crateFile;
    private final File pendingFile;
    private final NamespacedKey keyTag;
    private final NamespacedKey holoTag;

    public CrateManager(AtlasKasa plugin) {
        this.plugin = plugin;
        this.keyTag = new NamespacedKey(plugin, "kasa_anahtari");
        this.holoTag = new NamespacedKey(plugin, "kasa_yazisi");
        this.crateFile = new File(plugin.getDataFolder(), "crates.yml");
        this.pendingFile = new File(plugin.getDataFolder(), "pending.yml");
        loadTypes();
        load();
    }

    // ---------- Tipler ----------
    static String norm(String s) {
        return s.toLowerCase().replace('ö', 'o').replace('ü', 'u').replace('ş', 's').replace('ç', 'c')
                .replace('ğ', 'g').replace('ı', 'i').replaceAll("[^a-z0-9]", "");
    }

    private void addType(String id, String name, String block, String keyName, String keyMat, String holo) {
        String p = "types." + id + ".";
        Type t = new Type();
        t.id = id;
        t.name = plugin.getConfig().getString(p + "name", name);
        t.block = plugin.getConfig().getString(p + "block", block);
        t.keyName = plugin.getConfig().getString(p + "key-name", keyName);
        t.keyMaterial = plugin.getConfig().getString(p + "key-material", keyMat);
        t.holo = plugin.getConfig().getString(p + "hologram", holo);
        types.put(id, t);
    }

    private void loadTypes() {
        addType("efsanevi", "&6&lAtlas MC Efsanevi Kasa", "YELLOW_SHULKER_BOX", "&6&lEfsanevi Kasa Anahtarı", "NETHER_STAR",
                "&6&l✦ EFSANEVİ KASA ✦\n&7Sağ tık: &fEfsanevi Anahtarla aç\n&7Sol tık: &fÖdülleri gör");
        addType("ozel", "&d&lAtlas MC Özel Kasa", "PURPLE_SHULKER_BOX", "&d&lÖzel Kasa Anahtarı", "OMINOUS_TRIAL_KEY",
                "&d&l✦ ÖZEL KASA ✦\n&7Sağ tık: &fÖzel Anahtarla aç\n&7Sol tık: &fÖdülleri gör");
        addType("oyuncu", "&a&lAtlas MC Oyuncu Kasası", "LIME_SHULKER_BOX", "&a&lOyuncu Kasası Anahtarı", "TRIAL_KEY",
                "&a&l✦ OYUNCU KASASI ✦\n&7Sağ tık: &fOyuncu Anahtarıyla aç\n&7Sol tık: &fÖdülleri gör");
        // config'e eklenen ek tipler
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("types");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                String id = norm(k);
                if (id.isEmpty() || types.containsKey(id)) continue;
                addType(id, "&f&lAtlas MC " + k + " Kasası", "SHULKER_BOX", "&f&l" + k + " Anahtarı", "TRIPWIRE_HOOK",
                        "&f&l✦ " + k.toUpperCase() + " ✦\n&7Sağ tık: &fAnahtarla aç\n&7Sol tık: &fÖdülleri gör");
                // addType config'i "types.<id>." ile okur; özgün anahtar adıyla yazılmışsa onu da dene
                Type t = types.get(id);
                String p = "types." + k + ".";
                t.name = plugin.getConfig().getString(p + "name", t.name);
                t.block = plugin.getConfig().getString(p + "block", t.block);
                t.keyName = plugin.getConfig().getString(p + "key-name", t.keyName);
                t.keyMaterial = plugin.getConfig().getString(p + "key-material", t.keyMaterial);
                t.holo = plugin.getConfig().getString(p + "hologram", t.holo);
            }
        }
    }

    public List<String> typeIds() {
        return new ArrayList<>(types.keySet());
    }

    public Type findType(String s) {
        return types.get(norm(s));
    }

    public Type defaultType() {
        Type t = findType(plugin.getConfig().getString("default-type", "oyuncu"));
        return t != null ? t : types.values().iterator().next();
    }

    private Type typeOf(Crate c) {
        Type t = types.get(c.type);
        return t != null ? t : defaultType();
    }

    private static String keyOf(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    // ---------- Anahtar ----------
    public ItemStack createKey(Type t, int amount) {
        Material m = Material.matchMaterial(t.keyMaterial);
        if (m == null) m = Material.TRIPWIRE_HOOK;
        ItemStack it = new ItemStack(m, amount);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(t.keyName));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Sadece " + t.name + " &7için geçerlidir."));
        lore.add(color("&7Spawn'daki kasaya &fsağ tıklayarak &7kullan."));
        lore.add(color("&8Tek kullanımlık"));
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, t.id);
        it.setItemMeta(meta);
        return it;
    }

    /** Anahtarın tipi; anahtar değilse null. Eski (tipsiz) anahtarlar varsayılan tip sayılır. */
    private String keyType(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        if (pdc.has(keyTag, PersistentDataType.STRING)) return pdc.get(keyTag, PersistentDataType.STRING);
        if (pdc.has(keyTag, PersistentDataType.BYTE)) return defaultType().id;
        return null;
    }

    // ---------- /kasakoy <tip> ----------
    public void place(Player p, Type type) {
        RayTraceResult r = p.rayTraceBlocks(6.0);
        if (r == null || r.getHitBlock() == null || r.getHitBlockFace() == null) {
            msg(p, "&cBir bloğa bakmalısın.");
            return;
        }
        BlockFace face = r.getHitBlockFace();
        Block target = r.getHitBlock().getRelative(face);
        if (crates.containsKey(keyOf(r.getHitBlock()))) {
            msg(p, "&cBurada zaten bir kasa var.");
            return;
        }
        if (!target.getType().isAir()) {
            msg(p, "&cKasayı koyacağın yer boş değil.");
            return;
        }

        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getStorageContents();
        List<ItemStack> loot = new ArrayList<>();
        for (int i = 0; i < c.length; i++) {
            ItemStack it = c[i];
            if (it == null || it.getType().isAir() || keyType(it) != null) continue;
            loot.add(it.clone());
            c[i] = null;
        }
        if (loot.isEmpty()) {
            msg(p, "&cEnvanterinde kasaya konacak eşya yok!");
            return;
        }
        inv.setStorageContents(c);

        Material m = Material.matchMaterial(type.block);
        if (m == null || !m.name().endsWith("SHULKER_BOX")) m = Material.SHULKER_BOX;
        target.setType(m, false);
        BlockData bd = target.getBlockData();
        if (bd instanceof Directional) {
            Directional dr = (Directional) bd;
            if (dr.getFaces().contains(face)) dr.setFacing(face);
            target.setBlockData(bd, false);
        }

        Crate cr = new Crate();
        cr.id = nextId++;
        cr.type = type.id;
        cr.world = target.getWorld().getName();
        cr.x = target.getX();
        cr.y = target.getY();
        cr.z = target.getZ();
        cr.items.addAll(loot);

        if (plugin.getConfig().getBoolean("crate.hologram", true)) {
            Location hl = target.getLocation().add(0.5, 1.5, 0.5);
            String text = color(type.holo);
            TextDisplay td = target.getWorld().spawn(hl, TextDisplay.class, t -> {
                t.setText(text);
                t.setBillboard(Display.Billboard.CENTER);
                t.getPersistentDataContainer().set(holoTag, PersistentDataType.BYTE, (byte) 1);
            });
            cr.holo = td.getUniqueId();
        }

        crates.put(keyOf(target), cr);
        saveAll();
        msg(p, color(type.name) + " &ayerleştirildi! &7İçine &e" + loot.size() + " &7eşya konuldu.");
        msg(p, "&7Anahtar vermek için: &e/kasaanahtarver " + type.id + " <adet> [oyuncu]");
    }

    // ---------- /kasasil ----------
    public void remove(Player p) {
        RayTraceResult r = p.rayTraceBlocks(6.0);
        Crate cr = (r == null || r.getHitBlock() == null) ? null : crates.get(keyOf(r.getHitBlock()));
        if (cr == null) {
            msg(p, "&cBir kasaya bakmalısın.");
            return;
        }
        Block b = r.getHitBlock();
        for (ItemStack it : cr.items) AtlasKasa.giveOrDrop(p, it.clone());
        removeHolo(cr);
        crates.remove(keyOf(b));
        b.setType(Material.AIR, false);
        saveAll();
        msg(p, "&eKasa silindi, içindeki &f" + cr.items.size() + " &eeşya sana geri verildi.");
    }

    private void removeHolo(Crate c) {
        if (c.holo == null) return;
        Entity e = Bukkit.getEntity(c.holo);
        if (e != null) e.remove();
    }

    // ---------- Etkileşim ----------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.LEFT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Crate c = crates.get(keyOf(b));
        if (c == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        if (a == Action.LEFT_CLICK_BLOCK) preview(p, c);
        else tryOpen(p, c);
    }

    private void tryOpen(Player p, Crate c) {
        if (c.items.isEmpty()) {
            msg(p, "&cBu kasanın içi boş.");
            return;
        }
        if (animating.contains(p.getUniqueId())) return;
        Type t = typeOf(c);
        ItemStack hand = p.getInventory().getItemInMainHand();
        String kt = keyType(hand);
        if (kt == null) {
            msg(p, "&cBu kasayı açmak için " + color(t.keyName) + " &cgerekli!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!kt.equals(t.id)) {
            msg(p, "&cBu kasa için " + color(t.keyName) + " &cgerekli, elindeki anahtar uymuyor!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand);
        }

        ItemStack winner = random(c).clone();
        // Ödül hemen kayıt altına alınır, animasyon bitince verilir (çökme/çıkışta kaybolmaz)
        pending.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(winner.clone());
        savePending();
        startAnimation(p, c, winner);
    }

    private ItemStack random(Crate c) {
        return c.items.get(ThreadLocalRandom.current().nextInt(c.items.size()));
    }

    // ---------- Menüler ----------
    private ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        it.setItemMeta(meta);
        return it;
    }

    private String itemName(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName()) return m.getDisplayName();
        return it.getType().name().toLowerCase().replace('_', ' ');
    }

    private void preview(Player p, Crate c) {
        if (c.items.isEmpty()) {
            msg(p, "&cBu kasanın içi boş.");
            return;
        }
        Type t = typeOf(c);
        int n = Math.min(54, c.items.size());
        int size = ((n - 1) / 9 + 1) * 9;
        PreviewHolder h = new PreviewHolder();
        Inventory inv = Bukkit.createInventory(h, size, color("&8" + strip(t.name) + " Ödülleri"));
        h.inv = inv;
        double chance = 100.0 / c.items.size();
        for (int i = 0; i < n; i++) {
            ItemStack it = c.items.get(i).clone();
            ItemMeta m = it.getItemMeta();
            List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(color("&7Kazanma şansı: &e%" + String.format(java.util.Locale.US, "%.1f", chance)));
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(i, it);
        }
        p.openInventory(inv);
    }

    private void startAnimation(Player p, Crate c, ItemStack winner) {
        animating.add(p.getUniqueId());
        Type t = typeOf(c);
        AnimHolder h = new AnimHolder();
        Inventory inv = Bukkit.createInventory(h, 27, color("&8" + strip(t.name) + " Açılıyor..."));
        h.inv = inv;
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        inv.setItem(4, named(Material.LIME_STAINED_GLASS_PANE, "&a▼"));
        inv.setItem(22, named(Material.LIME_STAINED_GLASS_PANE, "&a▲"));

        final int steps = 32;
        List<ItemStack> seq = new ArrayList<>();
        for (int i = 0; i < steps + 9; i++) seq.add(random(c).clone());
        seq.set(steps + 4, winner.clone());

        p.openInventory(inv);
        step(p, h, seq, 0, steps, t);
    }

    private int delay(int t) {
        if (t < 14) return 2;
        if (t < 22) return 3;
        if (t < 27) return 4;
        if (t < 30) return 6;
        return 9;
    }

    private void step(Player p, AnimHolder h, List<ItemStack> seq, int t, int steps, Type type) {
        boolean viewing = p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof AnimHolder;
        if (viewing) {
            for (int i = 0; i < 9; i++) h.inv.setItem(9 + i, seq.get(t + i).clone());
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f + t * 0.02f);
        }
        if (t >= steps) {
            finish(p, seq.get(steps + 4), type);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> step(p, h, seq, t + 1, steps, type), delay(t));
    }

    private void finish(Player p, ItemStack winner, Type type) {
        animating.remove(p.getUniqueId());
        if (!p.isOnline()) return; // ödül pending'de kalır, girince verilir
        deliver(p);
        String name = winner.getAmount() + "x " + itemName(winner);
        msg(p, "&a&lTebrikler! &7" + color(type.name) + " &7kasasından kazandın: &f" + name);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        if (plugin.getConfig().getBoolean("crate.broadcast", true)) {
            Bukkit.broadcastMessage(color(AtlasKasa.PREFIX + "&e" + p.getName() + " &7" + type.name + " &7kasasından &f" + name + " &7kazandı!"));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof AnimHolder)
                p.closeInventory();
        }, 50L);
    }

    private void deliver(Player p) {
        List<ItemStack> list = pending.remove(p.getUniqueId());
        if (list == null) return;
        for (ItemStack it : list) AtlasKasa.giveOrDrop(p, it);
        savePending();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!pending.containsKey(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !animating.contains(p.getUniqueId())) {
                deliver(p);
                msg(p, "&aKasadan kazandığın ödül envanterine verildi!");
            }
        }, 40L);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof PreviewHolder || h instanceof AnimHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof PreviewHolder || h instanceof AnimHolder) e.setCancelled(true);
    }

    // ---------- Koruma ----------
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (crates.containsKey(keyOf(e.getBlock()))) {
            e.setCancelled(true);
            msg(e.getPlayer(), "&cKasa kırılamaz. Silmek için &e/kasasil");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (keyType(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> crates.containsKey(keyOf(b)));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> crates.containsKey(keyOf(b)));
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (crates.containsKey(keyOf(b))) { e.setCancelled(true); return; }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (crates.containsKey(keyOf(b))) { e.setCancelled(true); return; }
    }

    @EventHandler
    public void onMove(InventoryMoveItemEvent e) {
        Location d = e.getDestination().getLocation();
        Location s = e.getSource().getLocation();
        if ((d != null && crates.containsKey(keyOf(d.getBlock()))) || (s != null && crates.containsKey(keyOf(s.getBlock()))))
            e.setCancelled(true);
    }

    // ---------- Kayıt ----------
    public void saveAll() {
        YamlConfiguration y = new YamlConfiguration();
        for (Crate c : crates.values()) {
            String p = "crates." + c.id + ".";
            y.set(p + "type", c.type);
            y.set(p + "world", c.world);
            y.set(p + "x", c.x);
            y.set(p + "y", c.y);
            y.set(p + "z", c.z);
            y.set(p + "holo", c.holo == null ? null : c.holo.toString());
            y.set(p + "items", c.items);
        }
        try {
            y.save(crateFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("crates.yml kaydedilemedi: " + ex.getMessage());
        }
        savePending();
    }

    private void savePending() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, List<ItemStack>> en : pending.entrySet())
            y.set("pending." + en.getKey(), en.getValue());
        try {
            y.save(pendingFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("pending.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    private void load() {
        if (crateFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(crateFile);
            ConfigurationSection root = y.getConfigurationSection("crates");
            if (root != null) {
                for (String k : root.getKeys(false)) {
                    ConfigurationSection s = root.getConfigurationSection(k);
                    if (s == null) continue;
                    try {
                        Crate c = new Crate();
                        c.id = Integer.parseInt(k);
                        c.type = norm(s.getString("type", defaultType().id));
                        c.world = s.getString("world");
                        c.x = s.getInt("x");
                        c.y = s.getInt("y");
                        c.z = s.getInt("z");
                        String hs = s.getString("holo");
                        if (hs != null) c.holo = UUID.fromString(hs);
                        List<?> raw = s.getList("items");
                        if (raw != null) for (Object o : raw) if (o instanceof ItemStack) c.items.add((ItemStack) o);
                        crates.put(c.world + ";" + c.x + ";" + c.y + ";" + c.z, c);
                        nextId = Math.max(nextId, c.id + 1);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (pendingFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(pendingFile);
            ConfigurationSection root = y.getConfigurationSection("pending");
            if (root != null) {
                for (String k : root.getKeys(false)) {
                    try {
                        List<?> raw = root.getList(k);
                        if (raw == null) continue;
                        List<ItemStack> l = new ArrayList<>();
                        for (Object o : raw) if (o instanceof ItemStack) l.add((ItemStack) o);
                        pending.put(UUID.fromString(k), l);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
