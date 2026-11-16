package net.atlasmc;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

final class Util {
    private Util() {}

    /** "atlasmc.homes.5" gibi izinlerden en büyük sayıyı bulur. */
    static int permNumber(Player p, String prefix, int def) {
        int best = def;
        for (PermissionAttachmentInfo pai : p.getEffectivePermissions()) {
            if (!pai.getValue()) continue;
            String s = pai.getPermission();
            if (s.startsWith(prefix)) {
                try {
                    best = Math.max(best, Integer.parseInt(s.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return best;
    }

    static boolean admin(CommandSender s) {
        return s.isOp() || s.hasPermission("atlasmc.admin");
    }

    static boolean can(CommandSender s, String cmd) {
        return admin(s) || s.hasPermission("atlasmc.cmd." + cmd);
    }

    static void bar(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(AtlasMC.color(text)));
    }

    static void putLoc(ConfigurationSection s, String path, Location l) {
        s.set(path + ".world", l.getWorld().getName());
        s.set(path + ".x", l.getX());
        s.set(path + ".y", l.getY());
        s.set(path + ".z", l.getZ());
        s.set(path + ".yaw", (double) l.getYaw());
        s.set(path + ".pitch", (double) l.getPitch());
    }

    static Location getLoc(ConfigurationSection s, String path) {
        if (s == null || !s.contains(path + ".world")) return null;
        World w = Bukkit.getWorld(s.getString(path + ".world", ""));
        if (w == null) return null;
        return new Location(w, s.getDouble(path + ".x"), s.getDouble(path + ".y"), s.getDouble(path + ".z"),
                (float) s.getDouble(path + ".yaw"), (float) s.getDouble(path + ".pitch"));
    }

    /** 10k, 2.5m, 1b gibi yazımları destekler. */
    static double parseAmount(String s) {
        String t = s.trim().toLowerCase().replace(",", ".");
        double mult = 1;
        if (t.endsWith("k")) { mult = 1_000; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("m")) { mult = 1_000_000; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("b")) { mult = 1_000_000_000; t = t.substring(0, t.length() - 1); }
        return Double.parseDouble(t) * mult;
    }

    /** Çevrimiçi oyuncu ya da sunucuya girmiş çevrimdışı oyuncu; yoksa null. */
    static OfflinePlayer findPlayer(String name) {
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return on;
        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off.hasPlayedBefore() ? off : null;
    }
}
