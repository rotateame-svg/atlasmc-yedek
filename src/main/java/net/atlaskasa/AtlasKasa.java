package net.atlaskasa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class AtlasKasa extends JavaPlugin implements TabCompleter {

    public static final String PREFIX = "&d&lKasa &8» ";
    private CrateManager crates;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        crates = new CrateManager(this);
        getServer().getPluginManager().registerEvents(crates, this);
        for (String n : new String[]{"kasakoy", "kasasil", "kasaanahtarver"}) {
            PluginCommand pc = getCommand(n);
            if (pc == null) {
                getLogger().warning("plugin.yml'de komut yok: " + n);
                continue;
            }
            pc.setExecutor(this);
            pc.setTabCompleter(this);
        }
        getLogger().info("AtlasKasa aktif! Kasa tipleri: " + String.join(", ", crates.typeIds()));
    }

    @Override
    public void onDisable() {
        if (crates != null) crates.saveAll();
    }

    private static Integer tryInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.isOp() && !s.hasPermission("atlaskasa.admin")) {
            msg(s, "&cBu komutu kullanamazsın.");
            return true;
        }
        String name = cmd.getName().toLowerCase();

        if (name.equals("kasasil")) {
            if (!(s instanceof Player)) {
                msg(s, "&cBu komutu sadece oyuncular kullanabilir.");
                return true;
            }
            crates.remove((Player) s);
            return true;
        }

        if (name.equals("kasakoy")) {
            if (!(s instanceof Player)) {
                msg(s, "&cBu komutu sadece oyuncular kullanabilir.");
                return true;
            }
            if (a.length < 1) {
                msg(s, "&cKullanım: &e/kasakoy <tip>");
                msg(s, "&7Tipler: &f" + String.join("&7, &f", crates.typeIds()));
                return true;
            }
            CrateManager.Type t = crates.findType(a[0]);
            if (t == null) {
                msg(s, "&cBöyle bir kasa tipi yok. &7Tipler: &f" + String.join("&7, &f", crates.typeIds()));
                return true;
            }
            crates.place((Player) s, t);
            return true;
        }

        // /kasaanahtarver <tip> <adet> [oyuncu]   (eski kullanım: /kasaanahtarver <adet> [oyuncu])
        if (a.length < 1) {
            msg(s, "&cKullanım: &e/kasaanahtarver <tip> <adet> [oyuncu]");
            msg(s, "&7Tipler: &f" + String.join("&7, &f", crates.typeIds()));
            return true;
        }
        CrateManager.Type type;
        String amountArg;
        String targetArg;
        if (tryInt(a[0]) != null) {
            type = crates.defaultType();
            amountArg = a[0];
            targetArg = a.length > 1 ? a[1] : null;
        } else {
            type = crates.findType(a[0]);
            if (type == null) {
                msg(s, "&cBöyle bir kasa tipi yok. &7Tipler: &f" + String.join("&7, &f", crates.typeIds()));
                return true;
            }
            if (a.length < 2) {
                msg(s, "&cKullanım: &e/kasaanahtarver " + type.id + " <adet> [oyuncu]");
                return true;
            }
            amountArg = a[1];
            targetArg = a.length > 2 ? a[2] : null;
        }
        Integer amount = tryInt(amountArg);
        if (amount == null) {
            msg(s, "&cAdet bir sayı olmalı.");
            return true;
        }
        if (amount < 1 || amount > 2304) {
            msg(s, "&cAdet 1 ile 2304 arasında olmalı.");
            return true;
        }
        Player target;
        if (targetArg != null) {
            target = Bukkit.getPlayerExact(targetArg);
            if (target == null) {
                msg(s, "&cOyuncu çevrimiçi değil.");
                return true;
            }
        } else if (s instanceof Player) {
            target = (Player) s;
        } else {
            msg(s, "&cKonsoldan kullanırken oyuncu adı yaz.");
            return true;
        }
        int left = amount;
        while (left > 0) {
            ItemStack key = crates.createKey(type, Math.min(64, left));
            giveOrDrop(target, key);
            left -= key.getAmount();
        }
        msg(target, "&a" + amount + " adet " + color(type.keyName) + " &aaldın!");
        if (target != s) msg(s, "&a" + target.getName() + " oyuncusuna &e" + amount + " &aadet " + color(type.keyName) + " &averildi.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String n = cmd.getName().toLowerCase();
        if (a.length == 1 && (n.equals("kasakoy") || n.equals("kasaanahtarver"))) {
            for (String id : crates.typeIds()) if (id.startsWith(a[0].toLowerCase())) out.add(id);
        } else if (a.length == 3 && n.equals("kasaanahtarver")) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(a[2].toLowerCase())) out.add(p.getName());
        }
        return out;
    }

    // ---------- Yardımcılar ----------
    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String strip(String s) {
        return s.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    public static void msg(CommandSender s, String m) {
        s.sendMessage(color(PREFIX + m));
    }

    public static void giveOrDrop(Player p, ItemStack it) {
        p.getInventory().addItem(it).values()
                .forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }
}
