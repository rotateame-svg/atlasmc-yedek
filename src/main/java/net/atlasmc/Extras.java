package net.atlasmc;

import org.bukkit.plugin.PluginManager;

public class Extras {
    private final AtlasMC plugin;
    private SellManager sell;
    private KitManager kit;

    public Extras(AtlasMC plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        RtpManager rtp = new RtpManager(plugin);
        AntiCheat ac = new AntiCheat(plugin);
        AntiXray xray = new AntiXray(plugin, ac);
        sell = new SellManager(plugin);
        CombatManager combat = new CombatManager(plugin);
        kit = new KitManager(plugin);

        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(rtp, plugin);
        pm.registerEvents(ac, plugin);
        pm.registerEvents(xray, plugin);
        pm.registerEvents(sell, plugin);
        pm.registerEvents(combat, plugin);
        pm.registerEvents(kit, plugin);

        plugin.getCommand("rtp").setExecutor(rtp);
        plugin.getCommand("rtpnpcolustur").setExecutor(rtp);
        plugin.getCommand("sat").setExecutor(sell);
        for (String c : new String[]{"kitler", "kityetkiver", "kityetkial"}) {
            plugin.getCommand(c).setExecutor(kit);
            plugin.getCommand(c).setTabCompleter(kit);
        }
    }

    public void disable() {
        if (sell != null) sell.closeAll();
        if (kit != null) kit.save();
    }
}
