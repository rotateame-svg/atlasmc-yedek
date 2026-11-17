package net.atlasmc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.atlasmc.AtlasMC.color;

/** /siralama : Para, kristal, oynama süresi, öldürme, ölüm, kırılan blok sıralamaları (menü). */
public class RankingManager implements CommandExecutor, Listener {

    private enum Cat {
        PARA("Para", Material.GOLD_INGOT),
        KRISTAL("Kristal", Material.AMETHYST_SHARD),
        SURE("Oynama Süresi", Material.CLOCK),
        OLDURME("Oyuncu Öldürme", Material.DIAMOND_SWORD),
        MOB("Mob Öldürme", Material.BONE),
        OLUM("Ölüm", Material.SKELETON_SKULL),
        BLOK("Kırılan Blok", Material.DIAMOND_PICKAXE);

        final String title;
        final Material icon;

        Cat(String title, Material icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static class Row {
        UUID id;
        String name;
        final double[] v = new double[Cat.values().length];
    }

    private static class MainHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static class TopHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final int[] MAIN_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] TOP_SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};

    private final AtlasMC plugin;
    private List<Row> rows = new ArrayList<>();
    private long time = 0;

    public RankingManager(AtlasMC plugin) {
        this.plugin = plugin;
    }

    // ---------- Veri ----------
    private static long num(JsonObject o, String... keys) {
        if (o == null) return 0;
        for (String k : keys) {
            if (!o.has(k)) continue;
            try {
                return o.get(k).getAsLong();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private void readStats(File f, Row r) {
        try (Reader in = new FileReader(f)) {
            JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
            JsonObject stats = root.has("stats") ? root.getAsJsonObject("stats") : root;
            JsonObject custom = stats.has("minecraft:custom") ? stats.getAsJsonObject("minecraft:custom") : null;
            r.v[Cat.SURE.ordinal()] = num(custom, "minecraft:play_time", "minecraft:play_one_minute") / 1200.0;
            r.v[Cat.OLDURME.ordinal()] = num(custom, "minecraft:player_kills");
            r.v[Cat.MOB.ordinal()] = num(custom, "minecraft:mob_kills");
            r.v[Cat.OLUM.ordinal()] = num(custom, "minecraft:deaths");
            if (stats.has("minecraft:mined")) {
                long sum = 0;
                for (Map.Entry<String, JsonElement> e : stats.getAsJsonObject("minecraft:mined").entrySet()) {
                    try {
                        sum += e.getValue().getAsLong();
                    } catch (Exception ignored) {
                    }
                }
                r.v[Cat.BLOK.ordinal()] = sum;
            }
        } catch (Exception ignored) {
        }
    }

    private List<Row> rows() {
        long now = System.currentTimeMillis();
        if (!rows.isEmpty() && now - time < 300000) return rows;
        File dir = new File(Bukkit.getWorlds().get(0).getWorldFolder(), "stats");
        Economy eco = plugin.getEconomy();
        List<Row> list = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() == null) continue;
            Row r = new Row();
            r.id = op.getUniqueId();
            r.name = op.getName();
            r.v[Cat.PARA.ordinal()] = eco == null ? 0 : eco.getBalance(op);
            r.v[Cat.KRISTAL.ordinal()] = plugin.getCrystals(r.id);
            File f = new File(dir, r.id + ".json");
            if (f.exists()) readStats(f, r);
            Player on = Bukkit.getPlayer(r.id);
            if (on != null) {
                r.v[Cat.SURE.ordinal()] = plugin.playTicks(on) / 1200.0;
                r.v[Cat.OLDURME.ordinal()] = plugin.kills(on);
                r.v[Cat.OLUM.ordinal()] = plugin.deaths(on);
            }
            list.add(r);
        }
        rows = list;
        time = now;
        return rows;
    }

    private List<Row> sorted(Cat c) {
        List<Row> list = new ArrayList<>();
        for (Row r : rows()) if (r.v[c.ordinal()] > 0) list.add(r);
        list.sort(Comparator.comparingDouble((Row r) -> r.v[c.ordinal()]).reversed());
        return list;
    }

    private String format(Cat c, double v) {
        switch (c) {
            case PARA: return "&a$" + AtlasMC.money(v);
            case KRISTAL: return "&d" + (long) v + " ✦";
            case SURE: {
                long m = (long) v;
                return "&b" + (m / 60) + "sa " + (m % 60) + "dk";
            }
            default: return "&f" + (long) v;
        }
    }

    // ---------- Komut ----------
    private Cat parse(String s) {
        switch (s.toLowerCase()) {
            case "para": case "money": case "zengin": return Cat.PARA;
            case "kristal": case "crystal": return Cat.KRISTAL;
            case "sure": case "süre": case "oynama": case "playtime": return Cat.SURE;
            case "oldurme": case "öldürme": case "pvp": case "kill": return Cat.OLDURME;
            case "mob": return Cat.MOB;
            case "olum": case "ölüm": case "death": return Cat.OLUM;
            case "blok": case "kazma": return Cat.BLOK;
            default: return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        Player p = (Player) s;
        if (c.getName().equalsIgnoreCase("baltop")) {
            openTop(p, Cat.PARA);
        } else if (a.length > 0 && parse(a[0]) != null) {
            openTop(p, parse(a[0]));
        } else {
            openMain(p);
        }
        return true;
    }

    // ---------- Menüler ----------
    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(color(s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }

    private void openMain(Player p) {
        MainHolder h = new MainHolder();
        h.inv = Bukkit.createInventory(h, 27, color("&8Sıralamalar"));
        ItemStack g = icon(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>());
        for (int i = 0; i < 27; i++) h.inv.setItem(i, g);
        Cat[] cats = Cat.values();
        for (int i = 0; i < cats.length; i++)
            h.inv.setItem(MAIN_SLOTS[i], icon(cats[i].icon, "&e&l" + cats[i].title,
                    List.of("&7İlk 10 oyuncuyu gör.", "", "&a▸ Tıkla!")));
        p.openInventory(h.inv);
    }

    private void openTop(Player p, Cat c) {
        TopHolder h = new TopHolder();
        h.inv = Bukkit.createInventory(h, 36, color("&8Sıralama: " + c.title));
        ItemStack g = icon(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>());
        for (int i = 0; i < 36; i++) h.inv.setItem(i, g);

        List<Row> list = sorted(c);
        String[] col = {"&6", "&7", "&c"};
        for (int i = 0; i < TOP_SLOTS.length && i < list.size(); i++) {
            Row r = list.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(r.id));
            sm.setDisplayName(color((i < 3 ? col[i] : "&e") + "&l#" + (i + 1) + " &f" + r.name));
            sm.setLore(List.of(color("&7" + c.title + ": " + format(c, r.v[c.ordinal()]))));
            head.setItemMeta(sm);
            h.inv.setItem(TOP_SLOTS[i], head);
        }

        int pos = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(p.getUniqueId())) { pos = i; break; }
        List<String> own = new ArrayList<>();
        if (pos >= 0) {
            own.add("&7Sıran: &e#" + (pos + 1) + "&7/" + list.size());
            own.add("&7Değerin: " + format(c, list.get(pos).v[c.ordinal()]));
        } else {
            own.add("&7Bu sıralamada henüz yoksun.");
        }
        h.inv.setItem(4, icon(Material.PAPER, "&b&lSenin Durumun", own));
        h.inv.setItem(31, icon(Material.BARRIER, "&cGeri Dön", new ArrayList<>()));
        if (list.isEmpty()) h.inv.setItem(13, icon(Material.BARRIER, "&7Henüz veri yok", new ArrayList<>()));
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof MainHolder || h instanceof TopHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof MainHolder || holder instanceof TopHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        Player p = (Player) e.getWhoClicked();
        int s = e.getSlot();
        if (holder instanceof MainHolder) {
            Cat[] cats = Cat.values();
            for (int i = 0; i < cats.length; i++) {
                if (s == MAIN_SLOTS[i]) {
                    final Cat c = cats[i];
                    Bukkit.getScheduler().runTask(plugin, () -> openTop(p, c));
                    return;
                }
            }
        } else if (s == 31) {
            Bukkit.getScheduler().runTask(plugin, () -> openMain(p));
        }
    }
}
