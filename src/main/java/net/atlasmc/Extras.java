package net.atlasmc;

import org.bukkit.plugin.PluginManager;

public class Extras {
    private final AtlasMC plugin;

    public Extras(AtlasMC plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        RtpManager rtp = new RtpManager(plugin);
        AntiCheat ac = new AntiCheat(plugin);
        AntiXray xray = new AntiXray(plugin, ac);

        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(rtp, plugin);
        pm.registerEvents(ac, plugin);
        pm.registerEvents(xray, plugin);

        plugin.getCommand("rtp").setExecutor(rtp);
        plugin.getCommand("rtpnpcolustur").setExecutor(rtp);
    }
}
