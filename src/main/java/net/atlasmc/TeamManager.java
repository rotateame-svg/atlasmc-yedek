package net.atlasmc;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.atlasmc.AtlasMC.color;

/**
 * Takım sistemi: /takim (olustur, davet, kabul, reddet, ayril, at, dagit, bilgi, liste, lider, ev, evkur, chat) ve /tc.
 * Takım arkadaşları birbirine vuramaz (config: team.friendly-fire), sohbette [TAKIM] etiketi görünür.
 */
public class TeamManager implements CommandExecutor, TabCompleter, Listener {

    private static class Team {
        String name;
        UUID leader;
        long created;
        Location home;
        final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    }

    private static class Invite {
        String team;
        UUID from;
        long expire;
    }

    private static TeamManager instance;

    /** Sohbette isim önüne gelen takım etiketi. */
    public static String chatTag(Player p) {
        if (instance == null) return "";
        Team t = instance.byPlayer.get(p.getUniqueId());
        return t == null ? "" : color("&8[&b" + t.name + "&8] ");
    }

    private final AtlasMC plugin;
    private final TeleportManager tele;
    private final File file;
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, Team> byPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Set<UUID> chatMode = ConcurrentHashMap.newKeySet();

    public TeamManager(AtlasMC plugin, TeleportManager tele) {
        this.plugin = plugin;
        this.tele = tele;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        instance = this;
        load();
    }

    private int max() {
        return plugin.getConfig().getInt("team.max-members", 5);
    }

    private String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    private void broadcast(Team t, String msg) {
        for (UUID id : t.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.msg(p, msg);
        }
    }

    // ---------- Komutlar ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        Player p = (Player) s;
        if (c.getName().equalsIgnoreCase("tc")) {
            chat(p, a);
            return true;
        }
        String sub = a.length == 0 ? "yardim" : a[0].toLowerCase();
        String[] r = a.length == 0 ? new String[0] : Arrays.copyOfRange(a, 1, a.length);
        switch (sub) {
            case "olustur": case "create": create(p, r); break;
            case "davet": case "invite": invite(p, r); break;
            case "kabul": case "katil": case "accept": accept(p, r); break;
            case "reddet": case "deny": deny(p); break;
            case "ayril": case "leave": leave(p); break;
            case "at": case "kick": kick(p, r); break;
            case "dagit": case "disband": disband(p); break;
            case "bilgi": case "info": info(p, r); break;
            case "liste": case "list": list(p); break;
            case "lider": case "leader": leader(p, r); break;
            case "ev": case "home": home(p); break;
            case "evkur": case "sethome": setHome(p); break;
            case "chat": case "sohbet": chat(p, r); break;
            default: help(p); break;
        }
        return true;
    }

    private void help(Player p) {
        String[] l = {
                "&b&lTakım Komutları",
                "&e/takim olustur <isim> &7- Takım kur (3-12 harf/rakam)",
                "&e/takim davet <oyuncu> &7- Oyuncuyu davet et (lider)",
                "&e/takim kabul [takım] &7- Daveti kabul et  &e/takim reddet",
                "&e/takim ayril &7- Takımdan ayrıl  &e/takim at <oyuncu> &7- Üyeyi at (lider)",
                "&e/takim lider <oyuncu> &7- Liderliği devret  &e/takim dagit &7- Takımı dağıt",
                "&e/takim bilgi [takım] &7- Takım bilgisi  &e/takim liste &7- Takımlar",
                "&e/takim evkur &7- Takım evi kur (lider)  &e/takim ev &7- Takım evine git",
                "&e/takim chat [mesaj] &7- Takım sohbeti aç/kapat veya tek mesaj  &e/tc <mesaj>"
        };
        for (String s : l) p.sendMessage(color(s));
    }

    private void create(Player p, String[] a) {
        if (byPlayer.containsKey(p.getUniqueId())) { plugin.msg(p, "&cZaten bir takımdasın."); return; }
        if (a.length < 1 || !a[0].matches("[A-Za-z0-9]{3,12}")) {
            plugin.msg(p, "&cKullanım: &e/takim olustur <isim> &7(3-12 harf veya rakam)");
            return;
        }
        if (teams.containsKey(a[0].toLowerCase())) { plugin.msg(p, "&cBu isimde bir takım zaten var."); return; }
        double cost = plugin.getConfig().getDouble("team.create-cost", 0);
        if (cost > 0) {
            Economy eco = plugin.getEconomy();
            if (eco == null || !eco.has(p, cost)) {
                plugin.msg(p, "&cYeterli paran yok! Gerekli: &a$" + AtlasMC.money(cost));
                return;
            }
            eco.withdrawPlayer(p, cost);
        }
        Team t = new Team();
        t.name = a[0];
        t.leader = p.getUniqueId();
        t.created = System.currentTimeMillis();
        t.members.add(p.getUniqueId());
        teams.put(t.name.toLowerCase(), t);
        byPlayer.put(p.getUniqueId(), t);
        save();
        plugin.msg(p, "&a" + t.name + " &7takımı kuruldu! Oyuncu davet etmek için &e/takim davet <oyuncu>");
    }

    private void invite(Player p, String[] a) {
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null) { plugin.msg(p, "&cBir takımda değilsin."); return; }
        if (!t.leader.equals(p.getUniqueId())) { plugin.msg(p, "&cSadece lider davet edebilir."); return; }
        if (a.length < 1) { plugin.msg(p, "&cKullanım: &e/takim davet <oyuncu>"); return; }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target == null || target.equals(p)) { plugin.msg(p, "&cOyuncu bulunamadı."); return; }
        if (byPlayer.containsKey(target.getUniqueId())) { plugin.msg(p, "&cBu oyuncu zaten bir takımda."); return; }
        if (t.members.size() >= max()) { plugin.msg(p, "&cTakım dolu! &7(en fazla " + max() + " üye)"); return; }
        Invite inv = new Invite();
        inv.team = t.name;
        inv.from = p.getUniqueId();
        inv.expire = System.currentTimeMillis() + 60000L;
        invites.put(target.getUniqueId(), inv);
        plugin.msg(p, "&a" + target.getName() + " &7oyuncusuna davet gönderildi. &8(60 sn)");
        plugin.msg(target, "&e" + p.getName() + " &7seni &b" + t.name + " &7takımına davet etti.");
        plugin.msg(target, "&a/takim kabul &7ile katıl, &c/takim reddet &7ile reddet.");
    }

    private void accept(Player p, String[] a) {
        Invite inv = invites.get(p.getUniqueId());
        if (inv == null || inv.expire < System.currentTimeMillis()) {
            invites.remove(p.getUniqueId());
            plugin.msg(p, "&cBekleyen davetin yok.");
            return;
        }
        if (byPlayer.containsKey(p.getUniqueId())) { plugin.msg(p, "&cZaten bir takımdasın."); return; }
        Team t = teams.get(inv.team.toLowerCase());
        if (t == null) { invites.remove(p.getUniqueId()); plugin.msg(p, "&cBu takım artık yok."); return; }
        if (t.members.size() >= max()) { plugin.msg(p, "&cTakım dolu!"); return; }
        invites.remove(p.getUniqueId());
        t.members.add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), t);
        save();
        broadcast(t, "&a" + p.getName() + " &7takıma katıldı! &8(" + t.members.size() + "/" + max() + ")");
    }

    private void deny(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null) { plugin.msg(p, "&cBekleyen davetin yok."); return; }
        plugin.msg(p, "&eDavet reddedildi.");
        Player from = Bukkit.getPlayer(inv.from);
        if (from != null) plugin.msg(from, "&c" + p.getName() + " &7davetini reddetti.");
    }

    private void leave(Player p) {
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null) { plugin.msg(p, "&cBir takımda değilsin."); return; }
        if (t.leader.equals(p.getUniqueId())) {
            if (t.members.size() > 1) {
                plugin.msg(p, "&cLidersin! Önce &e/takim lider <oyuncu> &cile devret ya da &e/takim dagit &cde.");
                return;
            }
            removeTeam(t);
            plugin.msg(p, "&eTakımdan ayrıldın, takım dağıldı.");
            return;
        }
        t.members.remove(p.getUniqueId());
        byPlayer.remove(p.getUniqueId());
        chatMode.remove(p.getUniqueId());
        save();
        plugin.msg(p, "&eTakımdan ayrıldın.");
        broadcast(t, "&e" + p.getName() + " &7takımdan ayrıldı.");
    }

    private UUID memberByName(Team t, String name) {
        for (UUID id : t.members) if (nameOf(id).equalsIgnoreCase(name)) return id;
        return null;
    }

    private void kick(Player p, String[] a) {
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null || !t.leader.equals(p.getUniqueId())) { plugin.msg(p, "&cSadece takım lideri üye atabilir."); return; }
        if (a.length < 1) { plugin.msg(p, "&cKullanım: &e/takim at <oyuncu>"); return; }
        UUID id = memberByName(t, a[0]);
        if (id == null || id.equals(p.getUniqueId())) { plugin.msg(p, "&cBu oyuncu takımında değil."); return; }
        t.members.remove(id);
        byPlayer.remove(id);
        chatMode.remove(id);
        save();
        broadcast(t, "&c" + nameOf(id) + " &7takımdan atıldı.");
        Player kicked = Bukkit.getPlayer(id);
        if (kicked != null) plugin.msg(kicked, "&c" + t.name + " &7takımından atıldın.");
    }

    private void disband(Player p) {
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null || !t.leader.equals(p.getUniqueId())) { plugin.msg(p, "&cSadece takım lideri takımı dağıtabilir."); return; }
        broadcast(t, "&c" + t.name + " &7takımı lider tarafından dağıtıldı.");
        removeTeam(t);
    }

    private void removeTeam(Team t) {
        for (UUID id : t.members) {
            byPlayer.remove(id);
            chatMode.remove(id);
        }
        teams.remove(t.name.toLowerCase());
        save();
    }

    private void leader(Player p, String[] a) {
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null || !t.leader.equals(p.getUniqueId())) { plugin.msg(p, "&cSadece takım lideri liderliği devredebilir."); return; }
        if (a.length < 1) { plugin.msg(p, "&cKullanım: &e/takim lider <oyuncu>"); return; }
        UUID id = memberByName(t, a[0]);
        if (id == null || id.equals(p.getUniqueId())) { plugin.msg(p, "&cBu oyuncu takımında değil."); return; }
        t.leader = id;
        save();
        broadcast(t, "&e" + nameOf(id) + " &7artık takımın yeni lideri!");
    }

    private void info(Player p, String[] a) {
        Team t = a.length > 0 ? teams.get(a[0].toLowerCase()) : byPlayer.get(p.getUniqueId());
        if (t == null) { plugin.msg(p, a.length > 0 ? "&cBöyle bir takım yok." : "&cBir takımda değilsin."); return; }
        List<String> names = new ArrayList<>();
        for (UUID id : t.members) {
            boolean on = Bukkit.getPlayer(id) != null;
            names.add((on ? "&a" : "&7") + nameOf(id) + (id.equals(t.leader) ? " &6★" : ""));
        }
        String date = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(t.created), ZoneId.of("Europe/Istanbul")));
        p.sendMessage(color("&b&lTakım: &f" + t.name));
        p.sendMessage(color("&7Lider: &e" + nameOf(t.leader) + " &8| &7Üye: &f" + t.members.size() + "&7/&f" + max()
                + " &8| &7Kuruluş: &f" + date));
        p.sendMessage(color("&7Üyeler: " + String.join("&8, ", names)));
        p.sendMessage(color("&7Takım evi: " + (t.home != null ? "&aVar" : "&cYok")));
    }

    private void list(Player p) {
        if (teams.isEmpty()) { plugin.msg(p, "&7Henüz hiç takım yok."); return; }
        List<Team> all = new ArrayList<>(teams.values());
        all.sort(Comparator.comparingInt((Team t) -> t.members.size()).reversed());
        p.sendMessage(color("&b&lTakımlar &8(" + all.size() + ")"));
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            Team t = all.get(i);
            p.sendMessage(color("&e" + (i + 1) + ". &f" + t.name + " &8- &7" + t.members.size() + " üye &8| &7Lider: &e" + nameOf(t.leader)));
        }
    }

    private void setHome(Player p) {
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null || !t.leader.equals(p.getUniqueId())) { plugin.msg(p, "&cSadece takım lideri takım evi kurabilir."); return; }
        t.home = p.getLocation();
        save();
        broadcast(t, "&aTakım evi ayarlandı. &7Gitmek için &e/takim ev");
    }

    private void home(Player p) {
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null) { plugin.msg(p, "&cBir takımda değilsin."); return; }
        if (t.home == null) { plugin.msg(p, "&cTakım evi kurulmamış."); return; }
        tele.tp(p, t.home.clone(), "Takım evi");
    }

    // ---------- Takım sohbeti ----------
    private void chat(Player p, String[] a) {
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null) { plugin.msg(p, "&cBir takımda değilsin."); return; }
        if (a.length == 0) {
            if (chatMode.remove(p.getUniqueId())) plugin.msg(p, "&eTakım sohbeti &ckapatıldı&e.");
            else {
                chatMode.add(p.getUniqueId());
                plugin.msg(p, "&aTakım sohbeti &2açıldı&a. Mesajların sadece takımına gider. &7Kapatmak için &e/takim chat");
            }
            return;
        }
        sendTeam(p, t, String.join(" ", a));
    }

    private void sendTeam(Player p, Team t, String msg) {
        String line = color("&b&l[TAKIM] &8» &f" + p.getName() + "&8: &7") + msg;
        for (UUID id : t.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) m.sendMessage(line);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!chatMode.contains(p.getUniqueId())) return;
        Team t = byPlayer.get(p.getUniqueId());
        if (t == null) {
            chatMode.remove(p.getUniqueId());
            return;
        }
        e.setCancelled(true);
        sendTeam(p, t, e.getMessage());
    }

    // ---------- Takım arkadaşına vurma ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (plugin.getConfig().getBoolean("team.friendly-fire", false)) return;
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Entity d = e.getDamager();
        Player attacker = null;
        if (d instanceof Player) attacker = (Player) d;
        else if (d instanceof Projectile && ((Projectile) d).getShooter() instanceof Player)
            attacker = (Player) ((Projectile) d).getShooter();
        if (attacker == null || attacker.equals(victim)) return;
        Team ta = byPlayer.get(attacker.getUniqueId());
        if (ta != null && ta == byPlayer.get(victim.getUniqueId())) {
            e.setCancelled(true);
            Util.bar(attacker, "&cTakım arkadaşına vuramazsın!");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        chatMode.remove(e.getPlayer().getUniqueId());
        invites.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (c.getName().equalsIgnoreCase("tc")) return out;
        if (a.length == 1) {
            for (String k : new String[]{"olustur", "davet", "kabul", "reddet", "ayril", "at", "dagit", "bilgi",
                    "liste", "lider", "ev", "evkur", "chat"})
                if (k.startsWith(a[0].toLowerCase())) out.add(k);
        } else if (a.length == 2 && Arrays.asList("davet", "at", "lider").contains(a[0].toLowerCase())) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
        }
        return out;
    }

    // ---------- Kayıt ----------
    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Team t : teams.values()) {
            String p = "teams." + t.name.toLowerCase() + ".";
            y.set(p + "name", t.name);
            y.set(p + "leader", t.leader.toString());
            y.set(p + "created", t.created);
            List<String> ms = new ArrayList<>();
            for (UUID id : t.members) ms.add(id.toString());
            y.set(p + "members", ms);
            if (t.home != null) Util.putLoc(y, "teams." + t.name.toLowerCase() + ".home", t.home);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("teams.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("teams");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null) continue;
            try {
                Team t = new Team();
                t.name = s.getString("name", k);
                t.leader = UUID.fromString(s.getString("leader"));
                t.created = s.getLong("created");
                for (String m : s.getStringList("members")) t.members.add(UUID.fromString(m));
                if (t.members.isEmpty()) continue;
                t.home = Util.getLoc(root, k + ".home");
                teams.put(t.name.toLowerCase(), t);
                for (UUID id : t.members) byPlayer.put(id, t);
            } catch (Exception ignored) {
            }
        }
    }
}
