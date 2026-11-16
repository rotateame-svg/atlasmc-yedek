package net.atlasmc;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

public class Extras {
    private final AtlasMC plugin;
    private SellManager sell;
    private KitManager kit;
    private int announceIndex = 0;

    public Extras(AtlasMC plugin) {
        this.plugin = plugin;
    }

    private void bind(CommandExecutor ex, String... names) {
        for (String n : names) {
            PluginCommand pc = plugin.getCommand(n);
            if (pc == null) {
                plugin.getLogger().warning("plugin.yml'de komut tanımlı değil: " + n);
                continue;
            }
            pc.setExecutor(ex);
            if (ex instanceof TabCompleter) pc.setTabCompleter((TabCompleter) ex);
        }
    }

    public void enable() {
        PluginManager pm = plugin.getServer().getPluginManager();

        RtpManager rtp = new RtpManager(plugin);
        AntiCheat ac = new AntiCheat(plugin);
        AntiXray xray = new AntiXray(plugin, ac);
        sell = new SellManager(plugin);
        CombatManager combat = new CombatManager(plugin);
        kit = new KitManager(plugin);
        TeleportManager tp = new TeleportManager(plugin, combat);
        MoneyManager money = new MoneyManager(plugin);
        ChatManager chat = new ChatManager(plugin);
        UtilManager util = new UtilManager(plugin);
        SpawnProtect spawnProtect = new SpawnProtect(plugin);
        StaffChat staffChat = new StaffChat(plugin);
        DailyManager daily = new DailyManager(plugin);
        SmpManager smp = new SmpManager(plugin);

        pm.registerEvents(rtp, plugin);
        pm.registerEvents(ac, plugin);
        pm.registerEvents(xray, plugin);
        pm.registerEvents(sell, plugin);
        pm.registerEvents(combat, plugin);
        pm.registerEvents(kit, plugin);
        pm.registerEvents(tp, plugin);
        pm.registerEvents(chat, plugin);
        pm.registerEvents(spawnProtect, plugin);
        pm.registerEvents(staffChat, plugin);
        pm.registerEvents(daily, plugin);
        pm.registerEvents(smp, plugin);

        bind(rtp, "rtp", "rtpnpcolustur");
        bind(sell, "sat");
        bind(kit, "kitler", "kityetkiver", "kityetkial");
        bind(tp, "spawn", "setspawn", "sethome", "home", "delhome", "homes", "tpa", "tpahere", "tpaccept",
                "tpdeny", "tpcancel", "back", "warp", "setwarp", "delwarp", "warps");
        bind(money, "bal", "pay", "baltop", "top");
        bind(daily, "gunluk");
        bind(smp, "vanish", "seen", "kurallar", "discord");
        bind(chat, "msg", "r", "mute", "unmute");
        bind(util, "ec", "craft", "feed", "heal", "fly", "gm", "clearchat", "bc", "invsee", "repair", "hat",
                "ping", "rapor", "yardim");
        bind(staffChat, "achat");

        startAnnouncer();
    }

    private void startAnnouncer() {
        if (!plugin.getConfig().getBoolean("announcer.enabled", true)) return;
        List<String> msgs = new ArrayList<>(plugin.getConfig().getStringList("announcer.messages"));
        if (msgs.isEmpty()) {
            msgs.add("Günlük ödülünü almayı unutma: &e/gunluk");
            msgs.add("Kitlerini almak için &e/kitler &7yaz.");
            msgs.add("Hile veya kural ihlali gördün mü? &e/rapor <oyuncu> <sebep>");
            msgs.add("Komut listesi için &e/yardim &7yaz.");
        }
        long ticks = Math.max(30, plugin.getConfig().getLong("announcer.interval-seconds", 300)) * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            String m = msgs.get(announceIndex % msgs.size());
            announceIndex++;
            Bukkit.broadcastMessage(AtlasMC.color("&b&lATLAS &8» &7" + m));
        }, ticks, ticks);
    }

    public void disable() {
        if (sell != null) sell.closeAll();
        if (kit != null) kit.save();
    }
}
