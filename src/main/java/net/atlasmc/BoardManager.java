package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import static net.atlasmc.AtlasMC.color;

public class BoardManager implements Listener {

    private static final int LINES = 12;
    private final AtlasMC plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public BoardManager(AtlasMC plugin) {
        this.plugin = plugin;
        for (Player p : Bukkit.getOnlinePlayers()) create(p);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!boards.containsKey(p.getUniqueId())) create(p);
                update(p);
            }
        }, 20L, 20L);
    }

    private String entry(int i) {
        return ChatColor.COLOR_CHAR + Integer.toHexString(i) + ChatColor.RESET;
    }

    private void create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("atlas", "dummy", color(plugin.getConfig().getString("scoreboard.title", "&b&lATLASMC")));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < LINES; i++) {
            Team t = sb.registerNewTeam("l" + i);
            t.addEntry(entry(i));
            o.getScore(entry(i)).setScore(LINES - i);
        }
        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
        update(p);
    }

    private void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        if (p.getScoreboard() != sb) p.setScoreboard(sb);

        String[] lines = {
                "&8&m-------------------",
                "&fSW: &a" + plugin.getConfig().getString("scoreboard.sw-name", "Survival"),
                "&fOyuncu: &e" + p.getName(),
                "&fRank: " + plugin.getRank(p),
                "",
                "&fPara: &a$" + AtlasMC.money(plugin.getMoney(p)),
                "&fKristal: &d" + plugin.getCrystals(p.getUniqueId()) + " ✦",
                "&fSüre: &b" + AtlasMC.time(plugin.playTicks(p)),
                "&fÖldürme: &c" + plugin.kills(p),
                "&fÖlme: &4" + plugin.deaths(p),
                "",
                "&e" + plugin.getConfig().getString("scoreboard.ip", "oyna.atlasmc.net")
        };
        for (int i = 0; i < LINES; i++) {
            Team t = sb.getTeam("l" + i);
            if (t != null) t.setPrefix(color(lines[i]));
        }

        // Tab listesi (config: tab.header / tab.footer / tab.name-format)
        String ip = plugin.getConfig().getString("tab.ip", "oyna.atlasmc.net");
        String header = plugin.getConfig().getString("tab.header", "&b&lAtlas Mc");
        String footer = plugin.getConfig().contains("tab.footer")
                ? plugin.getConfig().getString("tab.footer")
                : "&fPing: &a{ping}ms\n&e" + ip;
        p.setPlayerListHeaderFooter(color(tabVars(header, p)), color(tabVars(footer, p)));
        p.setPlayerListName(null);
        updateTags(sb);
    }

    /** Her oyuncunun scoreboard'una rank takımlarını kurar (tab etiketi + sıralama). */
    private void updateTags(Scoreboard sb) {
        Set<String> keep = new HashSet<>();
        for (Player t : Bukkit.getOnlinePlayers()) {
            String n = t.getName();
            String tn = plugin.sortKey(t) + (n.length() > 12 ? n.substring(0, 12) : n);
            keep.add(tn);
            Team team = sb.getTeam(tn);
            if (team == null) team = sb.registerNewTeam(tn);
            String prefix = plugin.getTabPrefix(t);
            if (!prefix.equals(team.getPrefix())) team.setPrefix(prefix);
            if (!team.hasEntry(n)) team.addEntry(n);
        }
        for (Team tm : new ArrayList<>(sb.getTeams())) {
            String nm = tm.getName();
            if (nm.length() > 3 && Character.isDigit(nm.charAt(0)) && !keep.contains(nm)) tm.unregister();
        }
    }

    private String tabVars(String s, Player p) {
        return s.replace("{ping}", String.valueOf(p.getPing()))
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{player}", p.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> create(e.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }
}
