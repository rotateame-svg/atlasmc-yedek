package net.atlasmc;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;

/** /bal /pay /baltop /top /gunluk */
public class MoneyManager implements CommandExecutor {

    private final AtlasMC plugin;
    private final File dailyFile;
    private final YamlConfiguration daily;

    private List<Map.Entry<String, Double>> cacheMoney = new ArrayList<>();
    private List<Map.Entry<String, Double>> cacheCrystal = new ArrayList<>();
    private long cacheMoneyTime, cacheCrystalTime;

    public MoneyManager(AtlasMC plugin) {
        this.plugin = plugin;
        this.dailyFile = new File(plugin.getDataFolder(), "daily.yml");
        this.daily = YamlConfiguration.loadConfiguration(dailyFile);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase()) {
            case "bal":
                bal(s, a);
                break;
            case "pay":
                pay(s, a);
                break;
            case "baltop":
                top(s, "para");
                break;
            case "top":
                top(s, a.length > 0 ? a[0].toLowerCase() : "para");
                break;
            case "gunluk":
                gunluk(s);
                break;
            default:
                break;
        }
        return true;
    }

    // ---------- /bal ----------
    private void bal(CommandSender s, String[] a) {
        Economy eco = plugin.getEconomy();
        if (eco == null) { plugin.msg(s, "&cEkonomi sistemi bulunamadı!"); return; }
        OfflinePlayer t;
        if (a.length > 0) {
            t = Util.findPlayer(a[0]);
            if (t == null) { plugin.msg(s, "&cOyuncu bulunamadı."); return; }
        } else if (s instanceof Player) {
            t = (Player) s;
        } else {
            plugin.msg(s, "&cOyuncu adı yaz.");
            return;
        }
        String name = t.getName() == null ? "?" : t.getName();
        plugin.msg(s, "&7" + name + " bakiyesi: &a$" + AtlasMC.money(eco.getBalance(t))
                + " &8| &7Kristal: &d" + plugin.getCrystals(t.getUniqueId()));
    }

    // ---------- /pay ----------
    private void pay(CommandSender s, String[] a) {
        if (!(s instanceof Player)) { s.sendMessage("Sadece oyuncular kullanabilir."); return; }
        Player p = (Player) s;
        Economy eco = plugin.getEconomy();
        if (eco == null) { plugin.msg(p, "&cEkonomi sistemi bulunamadı!"); return; }
        if (a.length < 2) { plugin.msg(p, "&cKullanım: &e/pay <oyuncu> <miktar> &7(10k, 2.5m yazabilirsin)"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t.equals(p)) { plugin.msg(p, "&cOyuncu bulunamadı."); return; }
        double amt;
        try {
            amt = Util.parseAmount(a[1]);
        } catch (NumberFormatException ex) {
            plugin.msg(p, "&cGeçersiz miktar.");
            return;
        }
        if (Double.isNaN(amt) || amt < 1 || amt > 1e12) { plugin.msg(p, "&cMiktar en az $1 olmalı."); return; }
        if (!eco.has(p, amt)) { plugin.msg(p, "&cYeterli paran yok!"); return; }
        if (!eco.withdrawPlayer(p, amt).transactionSuccess()) { plugin.msg(p, "&cÖdeme başarısız."); return; }
        eco.depositPlayer(t, amt);
        plugin.msg(p, "&a" + t.getName() + " &7oyuncusuna &a$" + AtlasMC.money(amt) + " &7gönderdin.");
        plugin.msg(t, "&a" + p.getName() + " &7sana &a$" + AtlasMC.money(amt) + " &7gönderdi.");
    }

    // ---------- /baltop, /top ----------
    private List<Map.Entry<String, Double>> moneyBoard() {
        long now = System.currentTimeMillis();
        if (now - cacheMoneyTime < 120000 && !cacheMoney.isEmpty()) return cacheMoney;
        Economy eco = plugin.getEconomy();
        List<Map.Entry<String, Double>> list = new ArrayList<>();
        if (eco != null) {
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() == null) continue;
                double b = eco.getBalance(op);
                if (b > 0) list.add(new AbstractMap.SimpleEntry<>(op.getName(), b));
            }
        }
        list.sort(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed());
        cacheMoney = list;
        cacheMoneyTime = now;
        return list;
    }

    private List<Map.Entry<String, Double>> crystalBoard() {
        long now = System.currentTimeMillis();
        if (now - cacheCrystalTime < 120000 && !cacheCrystal.isEmpty()) return cacheCrystal;
        List<Map.Entry<String, Double>> list = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() == null) continue;
            int k = plugin.getCrystals(op.getUniqueId());
            if (k > 0) list.add(new AbstractMap.SimpleEntry<>(op.getName(), (double) k));
        }
        list.sort(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed());
        cacheCrystal = list;
        cacheCrystalTime = now;
        return list;
    }

    private void top(CommandSender s, String type) {
        boolean crystal = type.equals("kristal");
        List<Map.Entry<String, Double>> list = crystal ? crystalBoard() : moneyBoard();
        plugin.msg(s, crystal ? "&d&lEn Çok Kristal" : "&a&lEn Zenginler");
        if (list.isEmpty()) {
            s.sendMessage(AtlasMC.color("&7Henüz veri yok."));
            return;
        }
        String[] medal = {"&6&l1.", "&7&l2.", "&c&l3."};
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Map.Entry<String, Double> e = list.get(i);
            String rank = i < 3 ? medal[i] : "&8" + (i + 1) + ".";
            String val = crystal ? "&d" + e.getValue().longValue() + " ✦" : "&a$" + AtlasMC.money(e.getValue());
            s.sendMessage(AtlasMC.color(rank + " &f" + e.getKey() + " &8- " + val));
        }
        s.sendMessage(AtlasMC.color("&8Diğer sıralamalar: &7/top para, /top kristal"));
    }

    // ---------- /gunluk ----------
    private void gunluk(CommandSender s) {
        if (!(s instanceof Player)) { s.sendMessage("Sadece oyuncular kullanabilir."); return; }
        Player p = (Player) s;
        Economy eco = plugin.getEconomy();
        String path = p.getUniqueId().toString();
        long now = System.currentTimeMillis();
        long last = daily.getLong(path + ".last", 0);
        long diff = now - last;
        long day = 24L * 3600000L;
        if (last > 0 && diff < day) {
            long left = (day - diff) / 60000;
            plugin.msg(p, "&cGünlük ödülünü zaten aldın. Tekrar almak için &e" + (left / 60) + "sa " + (left % 60) + "dk &cbekle.");
            return;
        }
        int streak = (last > 0 && diff < 2 * day) ? daily.getInt(path + ".streak", 0) + 1 : 1;
        double mult = 1.0 + 0.25 * (Math.min(streak, 7) - 1);
        double money = plugin.getConfig().getDouble("daily.money", 500) * mult;
        int crystals = (int) Math.round(plugin.getConfig().getInt("daily.crystals", 10) * mult);
        if (eco != null) eco.depositPlayer(p, money);
        plugin.addCrystals(p.getUniqueId(), crystals);
        daily.set(path + ".last", now);
        daily.set(path + ".streak", streak);
        try {
            daily.save(dailyFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("daily.yml kaydedilemedi: " + ex.getMessage());
        }
        plugin.msg(p, "&a&lGünlük ödül! &7Seri: &e" + streak + " gün &8(x" + String.format(java.util.Locale.US, "%.2f", mult) + ")");
        plugin.msg(p, "&a+$" + AtlasMC.money(money) + " &8| &d+" + crystals + " Kristal");
    }
}
