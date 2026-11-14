package net.atlasmc;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static net.atlasmc.AtlasMC.color;

/** /sat : Eşyaları menüye koy, menüyü kapatınca (ya da Sat'a basınca) otomatik satılır. */
public class SellManager implements CommandExecutor, Listener {

    private static class Holder implements InventoryHolder {
        Inventory inv;
        boolean cancel;
        @Override public Inventory getInventory() { return inv; }
    }

    private final AtlasMC plugin;

    public SellManager(AtlasMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, color("&8Eşya Sat"));
        ItemStack glass = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) h.inv.setItem(i, glass);
        refresh(h);
        p.openInventory(h.inv);
        plugin.msg(p, "&7Satmak istediğin eşyaları menüye koy, menüyü kapatınca satılır.");
        return true;
    }

    private ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(color(s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }

    private double sellable(Inventory inv, int[] count) {
        double total = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir() || it.hasItemMeta()) continue;
            double pr = Prices.get(plugin, it.getType());
            if (pr <= 0) continue;
            total += pr * it.getAmount();
            count[0] += it.getAmount();
        }
        return total;
    }

    private void refresh(Holder h) {
        int[] cnt = {0};
        double total = sellable(h.inv, cnt);
        h.inv.setItem(45, icon(Material.RED_STAINED_GLASS_PANE, "&c&lİptal",
                "&7Eşyaları satmadan geri al."));
        h.inv.setItem(49, icon(Material.EMERALD, "&a&lSat",
                "", "&7Satılacak eşya: &e" + cnt[0],
                "&7Toplam: &a$" + AtlasMC.money(total),
                "", "&a▸ Satmak için tıkla!"));
        h.inv.setItem(53, icon(Material.PAPER, "&eBilgi",
                "&7Fiyatı olmayan veya büyülü / isimli",
                "&7eşyalar satılmaz, geri verilir."));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        Holder h = (Holder) e.getInventory().getHolder();
        Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == e.getInventory() && e.getSlot() >= 45) {
            e.setCancelled(true);
            if (e.getSlot() == 49) Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            else if (e.getSlot() == 45) {
                h.cancel = true;
                Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refresh(h));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        for (int slot : e.getRawSlots()) if (slot >= 45 && slot < 54) { e.setCancelled(true); return; }
        Holder h = (Holder) e.getInventory().getHolder();
        Bukkit.getScheduler().runTask(plugin, () -> refresh(h));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        Holder h = (Holder) e.getInventory().getHolder();
        Player p = (Player) e.getPlayer();
        Inventory inv = e.getInventory();
        Economy eco = plugin.getEconomy();

        double total = 0;
        int sold = 0;
        boolean had = false;
        List<ItemStack> back = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            had = true;
            inv.setItem(i, null);
            if (!h.cancel && eco != null && !it.hasItemMeta()) {
                double pr = Prices.get(plugin, it.getType());
                if (pr > 0) {
                    total += pr * it.getAmount();
                    sold += it.getAmount();
                    continue;
                }
            }
            back.add(it);
        }
        for (ItemStack b : back) Items.giveOrDrop(p, b);
        if (total > 0) {
            eco.depositPlayer(p, total);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            plugin.msg(p, "&a" + sold + " eşya satıldı: &2+$" + AtlasMC.money(total));
            if (!back.isEmpty()) plugin.msg(p, "&7Satılamayan eşyalar geri verildi.");
        } else if (had && !h.cancel) {
            plugin.msg(p, "&cSatılabilir eşya bulunamadı, eşyaların geri verildi.");
        }
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) p.closeInventory();
    }
}
