package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.atlasmc.AtlasMC.color;

/**
 * Yetkili sohbeti.
 *  /achat           : açık/kapalı (açıkken yazdığın her mesaj sadece yetkililere gider)
 *  /achat <mesaj>   : tek seferlik yetkili mesajı
 * Yetki: operatör, atlasmc.admin veya atlasmc.achat
 */
public class StaffChat implements CommandExecutor, Listener {

    private final AtlasMC plugin;
    private final Set<UUID> mode = ConcurrentHashMap.newKeySet();

    public StaffChat(AtlasMC plugin) {
        this.plugin = plugin;
    }

    private boolean staff(CommandSender s) {
        return Util.admin(s) || s.hasPermission("atlasmc.achat");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!staff(s)) {
            plugin.msg(s, "&cBu komutu kullanamazsın.");
            return true;
        }
        if (a.length == 0) {
            if (!(s instanceof Player)) {
                plugin.msg(s, "&cKullanım: &e/achat <mesaj>");
                return true;
            }
            Player p = (Player) s;
            if (mode.remove(p.getUniqueId())) {
                plugin.msg(p, "&eYetkili sohbeti &ckapatıldı&e. Mesajların herkese gider.");
            } else {
                mode.add(p.getUniqueId());
                plugin.msg(p, "&aYetkili sohbeti &2açıldı&a. Mesajların sadece yetkililere gider. &7Kapatmak için &e/achat");
            }
            return true;
        }
        send(s, String.join(" ", a));
        return true;
    }

    private void send(CommandSender s, String message) {
        String who;
        if (s instanceof Player) {
            Player p = (Player) s;
            StaffStats.addAchat(p.getUniqueId());
            who = plugin.getTabPrefix(p) + p.getName();
        } else {
            who = color("&4Konsol");
        }
        String line = color("&c&l[YETKİLİ] &8» ") + who + color("&8: &b") + color(message);
        for (Player o : Bukkit.getOnlinePlayers()) if (staff(o)) o.sendMessage(line);
        Bukkit.getConsoleSender().sendMessage(line);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!mode.contains(p.getUniqueId())) return;
        if (!staff(p)) {
            mode.remove(p.getUniqueId());
            return;
        }
        e.setCancelled(true);
        send(p, e.getMessage());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mode.remove(e.getPlayer().getUniqueId());
    }
}
