package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.atlasmc.AtlasMC.color;

/** Sohbet biçimi (rank etiketli), spam koruması, /msg /r, /mute /unmute, giriş-çıkış mesajları. */
public class ChatManager implements CommandExecutor, Listener {

    private final AtlasMC plugin;
    private final File muteFile;
    private final Map<UUID, Long> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastMsg = new HashMap<>();

    public ChatManager(AtlasMC plugin) {
        this.plugin = plugin;
        this.muteFile = new File(plugin.getDataFolder(), "mutes.yml");
        loadMutes();
    }

    // ---------- Sohbet ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        Long until = mutes.get(id);
        if (until != null) {
            if (until > System.currentTimeMillis()) {
                e.setCancelled(true);
                p.sendMessage(color(AtlasMC.PREFIX + "&cSusturulmuşsun, yazamazsın."
                        + (until == Long.MAX_VALUE ? "" : " &7Kalan: &e" + ((until - System.currentTimeMillis()) / 60000 + 1) + " dk")));
                return;
            }
            mutes.remove(id);
        }

        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("chat.cooldown-ms", 1500);
        Long last = lastChat.get(id);
        if (!Util.admin(p) && last != null && now - last < cd) {
            e.setCancelled(true);
            p.sendMessage(color(AtlasMC.PREFIX + "&cÇok hızlı yazıyorsun, biraz yavaş!"));
            return;
        }
        lastChat.put(id, now);

        if (p.hasPermission("atlasmc.chat.color") || Util.admin(p)) e.setMessage(color(e.getMessage()));

        String prefix = plugin.getTabPrefix(p).replace("%", "%%");
        e.setFormat(prefix + "%1$s" + color("&8 » &7") + "%2$s");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPlayedBefore()) {
            e.setJoinMessage(color("&b✦ &f" + p.getName() + " &7sunucuya ilk kez katıldı! &8(#" + Bukkit.getOfflinePlayers().length + ")"));
        } else {
            e.setJoinMessage(color("&a+ &7" + p.getName()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(color("&c- &7" + e.getPlayer().getName()));
        lastChat.remove(e.getPlayer().getUniqueId());
    }

    // ---------- Komutlar ----------
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase()) {
            case "msg":
                msg(s, a);
                break;
            case "r":
                reply(s, a);
                break;
            case "mute":
                mute(s, a);
                break;
            case "unmute":
                unmute(s, a);
                break;
            default:
                break;
        }
        return true;
    }

    private boolean muted(Player p) {
        Long u = mutes.get(p.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    private void send(Player from, Player to, String text) {
        from.sendMessage(color("&8[&7Ben &8→ &f" + to.getName() + "&8] &7" + text));
        to.sendMessage(color("&8[&f" + from.getName() + " &8→ &7Ben&8] &7" + text));
        lastMsg.put(from.getUniqueId(), to.getUniqueId());
        lastMsg.put(to.getUniqueId(), from.getUniqueId());
    }

    private void msg(CommandSender s, String[] a) {
        if (!(s instanceof Player)) { s.sendMessage("Sadece oyuncular kullanabilir."); return; }
        Player p = (Player) s;
        if (a.length < 2) { plugin.msg(p, "&cKullanım: &e/msg <oyuncu> <mesaj>"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t.equals(p)) { plugin.msg(p, "&cOyuncu bulunamadı."); return; }
        if (muted(p)) { plugin.msg(p, "&cSusturulmuşsun."); return; }
        send(p, t, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
    }

    private void reply(CommandSender s, String[] a) {
        if (!(s instanceof Player)) { s.sendMessage("Sadece oyuncular kullanabilir."); return; }
        Player p = (Player) s;
        if (a.length < 1) { plugin.msg(p, "&cKullanım: &e/r <mesaj>"); return; }
        UUID id = lastMsg.get(p.getUniqueId());
        Player t = id == null ? null : Bukkit.getPlayer(id);
        if (t == null) { plugin.msg(p, "&cYanıt verecek kimse yok."); return; }
        if (muted(p)) { plugin.msg(p, "&cSusturulmuşsun."); return; }
        send(p, t, String.join(" ", a));
    }

    private void mute(CommandSender s, String[] a) {
        if (!Util.admin(s)) { plugin.msg(s, "&cBu komutu kullanamazsın."); return; }
        if (a.length < 1) { plugin.msg(s, "&cKullanım: &e/mute <oyuncu> [dakika]"); return; }
        OfflinePlayer t = Util.findPlayer(a[0]);
        if (t == null) { plugin.msg(s, "&cOyuncu bulunamadı."); return; }
        long until = Long.MAX_VALUE;
        if (a.length > 1) {
            try {
                until = System.currentTimeMillis() + Long.parseLong(a[1]) * 60000L;
            } catch (NumberFormatException ex) {
                plugin.msg(s, "&cDakika bir sayı olmalı.");
                return;
            }
        }
        mutes.put(t.getUniqueId(), until);
        saveMutes();
        String dur = until == Long.MAX_VALUE ? "süresiz" : a[1] + " dk";
        plugin.msg(s, "&e" + t.getName() + " &7susturuldu &8(" + dur + ")");
        if (t.isOnline()) plugin.msg(t.getPlayer(), "&cSusturuldun! &7(" + dur + ")");
    }

    private void unmute(CommandSender s, String[] a) {
        if (!Util.admin(s)) { plugin.msg(s, "&cBu komutu kullanamazsın."); return; }
        if (a.length < 1) { plugin.msg(s, "&cKullanım: &e/unmute <oyuncu>"); return; }
        OfflinePlayer t = Util.findPlayer(a[0]);
        if (t == null || mutes.remove(t.getUniqueId()) == null) { plugin.msg(s, "&cBu oyuncu susturulmamış."); return; }
        saveMutes();
        plugin.msg(s, "&a" + t.getName() + " &7artık konuşabilir.");
        if (t.isOnline()) plugin.msg(t.getPlayer(), "&aSusturman kaldırıldı.");
    }

    private void saveMutes() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : mutes.entrySet()) y.set("mutes." + e.getKey(), e.getValue());
        try {
            y.save(muteFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("mutes.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    private void loadMutes() {
        if (!muteFile.exists()) return;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(muteFile).getConfigurationSection("mutes");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                mutes.put(UUID.fromString(k), s.getLong(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
