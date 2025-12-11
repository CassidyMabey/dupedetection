package notauthorised.dupedetection.managers;

import notauthorised.dupedetection.DupeDetectionPlugin;
import notauthorised.dupedetection.discord.DiscordWebhookManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;

public class NBTDuplicateManager {
    
    private final DupeDetectionPlugin plugin;
    private final DiscordWebhookManager discordManager;
    private Set<Material> monitoredItems;
    private Map<Material, ItemConfig> itemConfigs;
    
    // make classes for each item cofnig
    private static class ItemConfig {
        final int minimumDuplicates;
        final String action;
        final boolean checkNbt;
        
        ItemConfig(int minimumDuplicates, String action, boolean checkNbt) {
            this.minimumDuplicates = minimumDuplicates;
            this.action = action;
            this.checkNbt = checkNbt;
        }
    }
    
    public NBTDuplicateManager(DupeDetectionPlugin plugin) {
        this.plugin = plugin;
        this.discordManager = new DiscordWebhookManager(plugin);
        this.itemConfigs = new HashMap<>();
        loadMonitoredItems();
    }
    
    public void loadMonitoredItems() {
        monitoredItems = new HashSet<>();
        itemConfigs = new HashMap<>();
        
        // load settings
        int defaultMinDupes = plugin.getConfig().getInt("nbt-duplicate-detection.default.minimum-duplicates", 3);
        String defaultAction = plugin.getConfig().getString("nbt-duplicate-detection.default.action", "WARN");
        boolean defaultCheckNbt = plugin.getConfig().getBoolean("nbt-duplicate-detection.default.check-nbt", true);
        
        // load individual items
        if (plugin.getConfig().isConfigurationSection("nbt-duplicate-detection.items")) {
            var itemsSection = plugin.getConfig().getConfigurationSection("nbt-duplicate-detection.items");
            
            for (String materialName : itemsSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    
                    // 
                    int minDupes = itemsSection.getInt(materialName + ".minimum-duplicates", defaultMinDupes);
                    String action = itemsSection.getString(materialName + ".action", defaultAction);
                    boolean checkNbt = itemsSection.getBoolean(materialName + ".check-nbt", defaultCheckNbt);
                    
                    // store config
                    itemConfigs.put(material, new ItemConfig(minDupes, action, checkNbt));
                    monitoredItems.add(material);
                    
                    if (plugin.getConfig().getBoolean("general.debug", false)) {
                        plugin.getLogger().info("Monitoring NBT duplicates for: " + material.name() + 
                            " (min: " + minDupes + ", action: " + action + ", checkNBT: " + checkNbt + ")");
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in NBT monitoring config: " + materialName);
                }
            }
        }
    }
    
    public void checkContainerForNBTDuplicates(Player player, org.bukkit.inventory.Inventory containerInventory, 
                                             Location containerLocation, InventoryType containerType) {
        if (!plugin.getConfig().getBoolean("nbt-duplicate-detection.enabled", true)) {
            return;
        }
        
        if (monitoredItems.isEmpty()) {
            return;
        }
        
        // group all items ina  container with the same NBT 
        Map<String, List<ItemStack>> nbtGroups = new HashMap<>();
        
        for (ItemStack item : containerInventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            
            // only check items which are looked for (didnt have this before lagged the server out)
            if (!monitoredItems.contains(item.getType())) {
                continue;
            }
            
            // create nbt signatre
            String nbtSignature = createNBTSignature(item);
            
            // skip normal items (otherwise every item would be a dupe)
            if (nbtSignature == null) {
                continue;
            }
            
            
            nbtGroups.computeIfAbsent(nbtSignature, k -> new ArrayList<>()).add(item);
        }
        
        // check for violations
        Map<String, List<ItemStack>> violations = new HashMap<>();
        for (Map.Entry<String, List<ItemStack>> entry : nbtGroups.entrySet()) {
            List<ItemStack> items = entry.getValue();
            int totalAmount = items.stream().mapToInt(ItemStack::getAmount).sum();
            
            // get the config & the items
            Material material = items.get(0).getType();
            ItemConfig config = itemConfigs.get(material);
            
            if (config != null && totalAmount >= config.minimumDuplicates) {
                violations.put(entry.getKey(), items);
            }
        }
        
        
        if (!violations.isEmpty()) {
            handleNBTViolations(player, violations, containerLocation, containerType);
        }
    }
    
    public void checkPlayerInventoryForNBTDuplicates(Player player) {
        if (!plugin.getConfig().getBoolean("nbt-duplicate-detection.enabled", true)) {
            return;
        }
        
        // same thing as above just with player inventorys as i didnt want to do it in 1 function
        org.bukkit.inventory.PlayerInventory playerInventory = player.getInventory();
        org.bukkit.inventory.ItemStack[] contents = playerInventory.getStorageContents();
        
        
        Map<String, List<ItemStack>> nbtGroups = new HashMap<>();
        
        for (org.bukkit.inventory.ItemStack item : contents) {
            if (item == null || item.getType() == org.bukkit.Material.AIR) {
                continue;
            }
            
            if (!monitoredItems.contains(item.getType())) {
                continue;
            }
            
            String nbtSignature = createNBTSignature(item);
            
            if (nbtSignature == null) {
                continue;
            }
            
            nbtGroups.computeIfAbsent(nbtSignature, k -> new ArrayList<>()).add(item);
        }
        
        Map<String, List<ItemStack>> violations = new HashMap<>();
        for (Map.Entry<String, List<ItemStack>> entry : nbtGroups.entrySet()) {
            List<ItemStack> items = entry.getValue();
            int totalAmount = items.stream().mapToInt(ItemStack::getAmount).sum();
            
            Material material = items.get(0).getType();
            ItemConfig config = itemConfigs.get(material);
            
            if (config != null && totalAmount >= config.minimumDuplicates) {
                violations.put(entry.getKey(), items);
            }
        }
        
        if (!violations.isEmpty()) {
            handleNBTViolations(player, violations, player.getLocation(), InventoryType.PLAYER);
        }
    }
    
    private String createNBTSignature(ItemStack item) {
        Material material = item.getType();
        ItemConfig config = itemConfigs.get(material);
        
        if (config == null) {
            return null;
        }
        
        StringBuilder signature = new StringBuilder();
        boolean hasSpecialData = false;
        
        // add a material
        signature.append(item.getType().name()).append(":");
        
        
        if (!config.checkNbt) {
            return signature.toString();
        }
        
        // get all item meta data 
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            
            // display name
            if (meta.hasDisplayName()) {
                signature.append("name:").append(meta.getDisplayName()).append(";");
                hasSpecialData = true;
            }
            
            // lore
            if (meta.hasLore()) {
                signature.append("lore:").append(meta.getLore().toString()).append(";");
                hasSpecialData = true;
            }
            
            // enchants
            if (meta.hasEnchants()) {
                signature.append("enchants:").append(meta.getEnchants().toString()).append(";");
                hasSpecialData = true;
            }
            
            // custom model data
            if (meta.hasCustomModelData()) {
                signature.append("cmd:").append(meta.getCustomModelData()).append(";");
                hasSpecialData = true;
            }
            
            // damage 
            if (meta instanceof org.bukkit.inventory.meta.Damageable) {
                org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) meta;
                if (damageable.hasDamage()) {
                    signature.append("dmg:").append(damageable.getDamage()).append(";");
                    hasSpecialData = true;
                }
            }
            
            // book (doesnt really matter but it can alert me to book crashes)
            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                org.bukkit.inventory.meta.EnchantmentStorageMeta bookMeta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) meta;
                if (bookMeta.hasStoredEnchants()) {
                    signature.append("stored:").append(bookMeta.getStoredEnchants().toString()).append(";");
                    hasSpecialData = true;
                }
            }
            
            // potions
            if (meta instanceof org.bukkit.inventory.meta.PotionMeta) {
                org.bukkit.inventory.meta.PotionMeta potionMeta = (org.bukkit.inventory.meta.PotionMeta) meta;
                if (potionMeta.hasCustomEffects()) {
                    signature.append("effects:").append(potionMeta.getCustomEffects().toString()).append(";");
                    hasSpecialData = true;
                }
            }
        }
        
        if (config.checkNbt && !hasSpecialData) {
            return null;
        }
        
        return signature.toString();
    }
    
    private void handleNBTViolations(Player player, Map<String, List<ItemStack>> violations, 
                                   Location containerLocation, InventoryType containerType) {
        // create log message
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("NBT duplicates detected: ").append(player.getName()).append(" accessed container with identical items: ");
        
        for (Map.Entry<String, List<ItemStack>> entry : violations.entrySet()) {
            List<ItemStack> items = entry.getValue();
            int totalAmount = items.stream().mapToInt(ItemStack::getAmount).sum();
            Material material = items.get(0).getType();
            ItemConfig config = itemConfigs.get(material);
            
            logMessage.append(totalAmount).append(" identical ").append(material.name()).append(", ");
            
            // get the action
            String action = config != null ? config.action : "WARN";
            
            // log the violation
            if (plugin.getConfig().getBoolean("logging.log-suspicious", true)) {
                plugin.getLogger().log(Level.WARNING, logMessage.toString());
            }
        }
        
        // send discord webhook
        ItemStack firstItem = violations.values().iterator().next().get(0);
        ItemConfig firstConfig = itemConfigs.get(firstItem.getType());
        int minimumDuplicates = firstConfig != null ? firstConfig.minimumDuplicates : 3;
        
        discordManager.sendNBTViolationAlert(player, violations, minimumDuplicates, containerLocation, containerType);
        
        for (Map.Entry<String, List<ItemStack>> entry : violations.entrySet()) {
            List<ItemStack> items = entry.getValue();
            int totalAmount = items.stream().mapToInt(ItemStack::getAmount).sum();
            Material material = items.get(0).getType();
            ItemConfig config = itemConfigs.get(material);
            String action = config != null ? config.action.toUpperCase() : "WARN";
            
            switch (action) {
                case "REMOVE_EXCESS":
                    int excess = totalAmount - config.minimumDuplicates;
                    int toRemove = excess;
                    for (ItemStack item : items) {
                        if (toRemove <= 0) {
                            break;
                        }
                        
                        int itemAmount = item.getAmount();
                        if (itemAmount <= toRemove) {
                            toRemove -= itemAmount;
                            item.setAmount(0);
                        } else {
                            item.setAmount(itemAmount - toRemove);
                            toRemove = 0;
                        }
                    }
                    player.sendMessage("§cDupe Detection: Removed " + excess + " excess identical items (" + material.name() + ")");
                    break;
                    
                case "BAN":
                    player.kickPlayer("§cDupe Detection: Identical NBT items detected (" + totalAmount + " " + material.name() + ")");
                    break;
            }
        }
        
        // say to all ops online
        notifyAdmins("NBT duplicate violation detected for " + player.getName() + " - see Discord for details");
    }
    
    private void notifyAdmins(String message) {
        plugin.getServer().getOnlinePlayers().forEach(player -> {
            if (player.isOp() || player.hasPermission("dupedetection.admin")) {
                player.sendMessage("§c§lDUPE DETECTION §8» §7" + message);
            }
        });
    }
    
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        StringBuilder formatted = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : name.toCharArray()) {
            if (capitalizeNext) {
                formatted.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                formatted.append(c);
            }
            
            if (c == ' ') {
                capitalizeNext = true;
            }
        }
        
        return formatted.toString();
    }
    
    public Set<Material> getMonitoredItems() {
        return new HashSet<>(monitoredItems);
    }
}