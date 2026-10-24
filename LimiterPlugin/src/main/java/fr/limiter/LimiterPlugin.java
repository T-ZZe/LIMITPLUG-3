package fr.limiter;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerLaunchProjectileEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class LimiterPlugin extends JavaPlugin implements Listener {

    // ===== Valeurs modifiables =====
    private static final int COOLDOWN_PERLE_TICKS = 5 * 20;
    private static final int COOLDOWN_MACE_TICKS = 5 * 20;
    private final Map<Enchantment, Integer> caps = new HashMap<>();

    @Override
    public void onEnable() {
        caps.put(Enchantment.PROTECTION, 3);
        caps.put(Enchantment.FEATHER_FALLING, 3);
        caps.put(Enchantment.SHARPNESS, 4);
        caps.put(Enchantment.RIPTIDE, 2);
        caps.put(Enchantment.LUNGE, 2);
        caps.put(Enchantment.DENSITY, 3);
        caps.put(Enchantment.BREACH, 2);

        getServer().getPluginManager().registerEvents(this, this);

        // Verification periodique : armure netherite + enchantements de l'inventaire
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                limitNetheriteArmor(p);
                sanitizeInventory(p);
            }
        }, 20L, 10L);
    }

    // ===== Armure netherite : 1 piece max =====
    private boolean isNetherite(ItemStack i) {
        if (i == null) return false;
        return switch (i.getType()) {
            case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> true;
            default -> false;
        };
    }

    private void limitNetheriteArmor(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean kept = false;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot s : slots) {
            ItemStack it = inv.getItem(s);
            if (!isNetherite(it)) continue;
            if (!kept) { kept = true; continue; }
            inv.setItem(s, null);
            Map<Integer, ItemStack> left = inv.addItem(it);
            left.values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
            p.sendMessage("§cTu ne peux porter qu'une seule pièce d'armure en netherite.");
        }
    }

    // ===== Enchantements =====
    private void sanitizeInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && sanitize(it)) inv.setItem(i, it);
        }
    }

    /** Retourne true si l'item a ete modifie. */
    private boolean sanitize(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        boolean changed = false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // Mace incassable
        if (item.getType() == Material.MACE && !meta.isUnbreakable()) {
            meta.setUnbreakable(true);
            changed = true;
        }

        boolean sword = Tag.ITEMS_SWORDS.isTagged(item.getType());
        Map<Enchantment, Integer> current = meta instanceof EnchantmentStorageMeta esm
                ? esm.getStoredEnchants() : meta.getEnchants();

        for (Map.Entry<Enchantment, Integer> e : new HashMap<>(current).entrySet()) {
            Enchantment ench = e.getKey();
            int lvl = e.getValue();
            boolean remove = sword && ench.equals(Enchantment.FIRE_ASPECT);
            Integer cap = caps.get(ench);
            boolean cut = cap != null && lvl > cap;
            if (!remove && !cut) continue;
            changed = true;
            if (meta instanceof EnchantmentStorageMeta esm) {
                esm.removeStoredEnchant(ench);
                if (!remove) esm.addStoredEnchant(ench, cap, true);
            } else {
                meta.removeEnchant(ench);
                if (!remove) meta.addEnchant(ench, cap, true);
            }
        }
        if (changed) item.setItemMeta(meta);
        return changed;
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack r = e.getResult();
        if (r != null && sanitize(r)) e.getInventory().setRepairCost(e.getInventory().getRepairCost());
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack r = e.getInventory().getResult();
        if (r != null) {
            sanitize(r);
            e.getInventory().setResult(r);
        }
    }

    // ===== Cooldowns =====
    @EventHandler
    public void onPearl(PlayerLaunchProjectileEvent e) {
        if (e.getItemStack().getType() == Material.ENDER_PEARL) {
            e.getPlayer().setCooldown(Material.ENDER_PEARL, COOLDOWN_PERLE_TICKS);
        }
    }

    @EventHandler
    public void onMace(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p
                && p.getInventory().getItemInMainHand().getType() == Material.MACE) {
            p.setCooldown(Material.MACE, COOLDOWN_MACE_TICKS);
        }
    }
}
