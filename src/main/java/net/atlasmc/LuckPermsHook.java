package net.atlasmc;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

/** LuckPerms yoksa sınıf hiç yüklenmez (AtlasMC içinde izole). */
public class LuckPermsHook {
    private final LuckPerms lp = LuckPermsProvider.get();

    /** LuckPerms prefix'i (yoksa null). */
    public String prefix(Player p) {
        User u = lp.getUserManager().getUser(p.getUniqueId());
        if (u == null) return null;
        return u.getCachedData().getMetaData().getPrefix();
    }

    /** Ana grubun LuckPerms ağırlığı (yoksa 0). */
    public int weight(Player p) {
        User u = lp.getUserManager().getUser(p.getUniqueId());
        if (u == null) return 0;
        Group g = lp.getGroupManager().getGroup(u.getPrimaryGroup());
        if (g == null) return 0;
        return g.getWeight().orElse(0);
    }

    public String primaryGroup(Player p) {
        User u = lp.getUserManager().getUser(p.getUniqueId());
        return u == null ? "default" : u.getPrimaryGroup();
    }
}
