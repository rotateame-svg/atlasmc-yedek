package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.atlasmc.AtlasMC.color;

/** /ec /craft /feed /heal /fly /gm /clearchat /bc /invsee /repair /hat /ping /rapor /yardim */
public class UtilManager implements CommandExecutor {

    private final AtlasMC plugin;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public UtilManager(AtlasMC plugin) {
        this.plugin = plugin;
    }

    private boolean cooldown(Player p, String cmd, long seconds) {
        if (Util.admin(p)) return false;
        String key = p.getUniqueId() + ":" + cmd;
        long now = System.currentTimeMillis();
        Long next = cooldowns.get(key);
        if (next != null && next > now) {
            plugin.msg(p, "&cBu komutu tekrar kullanmak için &e" + ((next - now) / 1000 + 1) + " sn &cbeklemelisin.");
            return true;
        }
        cooldowns.put(key, now + seconds * 1000L);
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase();

        // Konsoldan da çalışabilenler
        if (n.equals("bc")) { broadcast(s, a); return true; }
        if (n.equals("yardim")) { help(s); return true; }

        if (!(s instanceof Player)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        Player p = (Player) s;

        switch (n) {
            case "ec":
                if (!need(p, "ec")) break;
                p.openInventory(p.getEnderChest());
                break;
            case "craft":
                if (!need(p, "craft")) break;
                p.openWorkbench(null, true);
                break;
            case "feed":
                if (!need(p, "feed") || cooldown(p, "feed", 120)) break;
                p.setFoodLevel(20);
                p.setSaturation(20f);
                plugin.msg(p, "&aAçlığın giderildi.");
                break;
            case "heal":
                if (!need(p, "heal") || cooldown(p, "heal", 300)) break;
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.setFireTicks(0);
                plugin.msg(p, "&aCanın yenilendi.");
                break;
            case "fly": {
                if (!need(p, "fly")) break;
                boolean on = !p.getAllowFlight();
                p.setAllowFlight(on);
                if (!on) p.setFlying(false);
                plugin.msg(p, on ? "&aUçuş açıldı." : "&eUçuş kapatıldı.");
                break;
            }
            case "gm":
                gm(p, a);
                break;
            case "clearchat":
                if (!Util.admin(p)) { plugin.msg(p, "&cBu komutu kullanamazsın."); break; }
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (Util.admin(o)) continue;
                    for (int i = 0; i < 100; i++) o.sendMessage(" ");
                }
                Bukkit.broadcastMessage(color(AtlasMC.PREFIX + "&aSohbet temizlendi."));
                break;
            case "invsee": {
                if (!Util.admin(p)) { plugin.msg(p, "&cBu komutu kullanamazsın."); break; }
                if (a.length < 1) { plugin.msg(p, "&cKullanım: &e/invsee <oyuncu>"); break; }
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null) { plugin.msg(p, "&cOyuncu çevrimiçi değil."); break; }
                p.openInventory(t.getInventory());
                break;
            }
            case "repair": {
                if (!need(p, "repair") || cooldown(p, "repair", 3600)) break;
                ItemStack it = p.getInventory().getItemInMainHand();
                ItemMeta m = it.getItemMeta();
                if (it.getType().isAir() || !(m instanceof Damageable) || ((Damageable) m).getDamage() <= 0) {
                    cooldowns.remove(p.getUniqueId() + ":repair");
                    plugin.msg(p, "&cElindeki eşya onarılacak durumda değil.");
                    break;
                }
                ((Damageable) m).setDamage(0);
                it.setItemMeta(m);
                plugin.msg(p, "&aEşyan onarıldı.");
                break;
            }
            case "hat": {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { plugin.msg(p, "&cElinde bir eşya tutmalısın."); break; }
                ItemStack old = p.getInventory().getHelmet();
                p.getInventory().setHelmet(hand.clone());
                p.getInventory().setItemInMainHand(old);
                plugin.msg(p, "&aEşyayı başına taktın.");
                break;
            }
            case "ping": {
                Player t = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : p;
                if (t == null) { plugin.msg(p, "&cOyuncu bulunamadı."); break; }
                plugin.msg(p, "&7" + t.getName() + " ping: &a" + t.getPing() + "ms");
                break;
            }
            case "rapor": {
                if (a.length < 2) { plugin.msg(p, "&cKullanım: &e/rapor <oyuncu> <sebep>"); break; }
                if (cooldown(p, "rapor", 60)) break;
                String reason = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                String text = color("&c&lRAPOR &8» &f" + p.getName() + " &7→ &c" + a[0] + " &8| &7" + reason);
                for (Player o : Bukkit.getOnlinePlayers()) if (Util.admin(o)) o.sendMessage(text);
                plugin.getLogger().info("[RAPOR] " + p.getName() + " -> " + a[0] + ": " + reason);
                plugin.msg(p, "&aRaporun yetkililere iletildi, teşekkürler.");
                break;
            }
            default:
                break;
        }
        return true;
    }

    private boolean need(Player p, String cmd) {
        if (Util.can(p, cmd)) return true;
        plugin.msg(p, "&cBu komutu kullanmak için yetkin yok.");
        return false;
    }

    private void gm(Player p, String[] a) {
        if (!Util.admin(p)) { plugin.msg(p, "&cBu komutu kullanamazsın."); return; }
        if (a.length < 1) { plugin.msg(p, "&cKullanım: &e/gm <0|1|2|3> [oyuncu]"); return; }
        GameMode mode;
        switch (a[0].toLowerCase()) {
            case "0": case "s": case "survival": mode = GameMode.SURVIVAL; break;
            case "1": case "c": case "creative": mode = GameMode.CREATIVE; break;
            case "2": case "a": case "adventure": mode = GameMode.ADVENTURE; break;
            case "3": case "sp": case "spectator": mode = GameMode.SPECTATOR; break;
            default:
                plugin.msg(p, "&cGeçersiz mod. 0=Survival 1=Creative 2=Adventure 3=Spectator");
                return;
        }
        Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : p;
        if (t == null) { plugin.msg(p, "&cOyuncu çevrimiçi değil."); return; }
        t.setGameMode(mode);
        plugin.msg(p, "&a" + t.getName() + " &7oyun modu: &e" + mode.name().toLowerCase());
    }

    private void broadcast(CommandSender s, String[] a) {
        if (!Util.admin(s)) { plugin.msg(s, "&cBu komutu kullanamazsın."); return; }
        if (a.length < 1) { plugin.msg(s, "&cKullanım: &e/bc <mesaj>"); return; }
        Bukkit.broadcastMessage(color("&b&lDUYURU &8» &f" + String.join(" ", a)));
    }

    private void help(CommandSender s) {
        String[] lines = {
                "&b&lAtlas MC &8- &7Komutlar",
                "&e/shop &7- Market  &e/sat &7- Eşya sat  &e/order &7- Siparişler  &e/ah &7- Pazar",
                "&e/kitler &7- Kitler  &e/kristalmarket &7- Kristal market",
                "&e/rtp &7- Rastgele ışınlan  &e/afk &7- AFK odası  &e/spawn &7- Spawn",
                "&e/sethome /home /homes /delhome &7- Evler  &e/tpa /tpaccept /tpdeny &7- Işınlanma isteği",
                "&e/warp /warps &7- Warplar  &e/back &7- Son konum",
                "&e/bal /pay /baltop /top &7- Para ve sıralamalar",
                "&e/msg /r &7- Özel mesaj  &e/rapor &7- Oyuncu bildir  &e/ping &7- Ping",
                "&e/seen &7- Son görülme  &e/kurallar &7- Kurallar  &e/discord &7- Discord  &e/gunluk &7- 30 günlük ödül takvimi"
        };
        for (String l : lines) s.sendMessage(color(l));
    }
}
