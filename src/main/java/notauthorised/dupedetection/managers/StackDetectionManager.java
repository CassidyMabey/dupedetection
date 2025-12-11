package notauthorised.dupedetection.managers;

import notauthorised.dupedetection.DupeDetectionPlugin;
import notauthorised.dupedetection.discord.DiscordWebhookManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class StackDetectionManager {
    
    private final DupeDetectionPlugin plugin;
    private final DiscordWebhookManager discordManager;
    private Map<Material, Integer> materialLimits;
    
    public StackDetectionManager(DupeDetectionPlugin plugin) {
        this.plugin = plugin;
        this.discordManager = new DiscordWebhookManager(plugin);
        loadLimits();
    }
    
    public void loadLimits() {
        materialLimits = new HashMap<>();
        ConfigurationSection limitsSection = plugin.getConfig().getConfigurationSection("large-stack-detection.limits");
        
        if (limitsSection != null) {
            for (String materialName : limitsSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    int limit = limitsSection.getInt(materialName);
                    materialLimits.put(material, limit);
                    
                    if (plugin.getConfig().getBoolean("general.debug", false)) {
                        plugin.getLogger().info("Loaded limit for " + material.name() + ": " + limit);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in config: " + materialName);
                }
            }
        }
    }
    
    public void checkPlayerInventory(Player player, Location containerLocation, InventoryType containerType) {
        if (materialLimits.isEmpty()) {
            return;
        }
        
        PlayerInventory inventory = player.getInventory();
        Map<Material, Integer> totalItemCounts = new HashMap<>();
        
        // count items in all inventories
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                Material material = item.getType();
                totalItemCounts.put(material, totalItemCounts.getOrDefault(material, 0) + item.getAmount());
            }
        }
        
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) {
                Material material = item.getType();
                totalItemCounts.put(material, totalItemCounts.getOrDefault(material, 0) + item.getAmount());
            }
        }
        
        ItemStack offhand = inventory.getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            Material material = offhand.getType();
            totalItemCounts.put(material, totalItemCounts.getOrDefault(material, 0) + offhand.getAmount());
        }
        
        // check for violations
        for (Map.Entry<Material, Integer> entry : totalItemCounts.entrySet()) {
            Material material = entry.getKey();
            int totalPlayerAmount = entry.getValue();
            
            if (materialLimits.containsKey(material)) {
                int limit = materialLimits.get(material);
                
                if (totalPlayerAmount > limit) {
                    handleViolation(player, material, totalPlayerAmount, limit, containerLocation, containerType);
                }
            }
        }
    }
    
    public void checkContainerInventory(Player player, org.bukkit.inventory.Inventory containerInventory, Location containerLocation, InventoryType containerType) {
        if (materialLimits.isEmpty()) {
            return;
        }
        
        Map<Material, Integer> totalItemCounts = new HashMap<>();
        Map<Material, Integer> violations = new HashMap<>();
        
        // count items in the container
        for (ItemStack item : containerInventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                Material material = item.getType();
                totalItemCounts.put(material, totalItemCounts.getOrDefault(material, 0) + item.getAmount());
            }
        }
        
        // look for vioaltions
        for (Map.Entry<Material, Integer> entry : totalItemCounts.entrySet()) {
            Material material = entry.getKey();
            int totalContainerAmount = entry.getValue();
            
            if (materialLimits.containsKey(material)) {
                int limit = materialLimits.get(material);
                
                if (totalContainerAmount > limit) {
                    violations.put(material, totalContainerAmount);
                }
            }
        }
        
        // send discord message
        if (!violations.isEmpty()) {
            handleMultipleViolations(player, violations, containerLocation, containerType);
        }
    }
    
    // legacy 
    public void checkPlayerInventory(Player player) {
        checkPlayerInventory(player, player.getLocation(), InventoryType.PLAYER);
    }
    
    private void handleViolation(Player player, Material material, int totalAmount, int limit, 
                               Location containerLocation, InventoryType containerType) {
        String action = plugin.getConfig().getString("large-stack-detection.action", "WARN").toUpperCase();
        
        String message = String.format("Large stack detected: %s accessed container with %d %s (limit: %d)", 
                player.getName(), totalAmount, material.name(), limit);
        
        // log
        if (plugin.getConfig().getBoolean("logging.log-suspicious", true)) {
            plugin.getLogger().log(Level.WARNING, message);
        }
        
        // send to discord webhook
        discordManager.sendViolationAlert(player, material, totalAmount, limit, containerLocation, containerType);
        
        switch (action) {
            case "REMOVE_EXCESS":
                int excessAmount = totalAmount - limit;
                removeExcessItems(player, material, excessAmount);
                notifyAdmins(message + " - Excess items detected but not removed from container");
                break;
                
            case "BAN":
                player.kickPlayer("§cDupe Detection: Excessive items detected (" + totalAmount + " " + material.name() + ")");
                notifyAdmins(message + " - Player kicked");
                break;
                
            case "WARN":
            case "LOG":
            default:
                notifyAdmins(message);
                break;
        }
    }
    
    private void handleMultipleViolations(Player player, Map<Material, Integer> violations, 
                                        Location containerLocation, InventoryType containerType) {
        String action = plugin.getConfig().getString("large-stack-detection.action", "WARN").toUpperCase();
        
        // create summary
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Multiple large stacks detected: ").append(player.getName()).append(" accessed container with: ");
        for (Map.Entry<Material, Integer> entry : violations.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();
            int limit = materialLimits.get(material);
            logMessage.append(amount).append(" ").append(material.name()).append(" (limit: ").append(limit).append("), ");
        }
        
        // log the violation
        if (plugin.getConfig().getBoolean("logging.log-suspicious", true)) {
            plugin.getLogger().log(Level.WARNING, logMessage.toString());
        }
        
        // single discord notification
        discordManager.sendMultipleViolationAlert(player, violations, materialLimits, containerLocation, containerType);
        
        // handle each violation
        for (Map.Entry<Material, Integer> entry : violations.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();
            int limit = materialLimits.get(material);
            
            switch (action) {
                case "REMOVE_EXCESS":
                    int excessAmount = amount - limit;
                    removeExcessItems(player, material, excessAmount);
                    break;
                    
                case "BAN":
                    player.kickPlayer("§cDupe Detection: Excessive items detected (" + amount + " " + material.name() + ")");
                    break;
            }
        }
        
        // notify ops online
        notifyAdmins("Multiple violations detected for " + player.getName() + " - see Discord for details");
    }
    
    private void removeExcessItems(Player player, Material material, int excessAmount) {
        PlayerInventory inventory = player.getInventory();
        int remaining = excessAmount;
        
        // remove from main inventory
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == material) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    inventory.setItem(i, null);
                    remaining -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
            }
        }
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
    
    public Map<Material, Integer> getMaterialLimits() {
        return new HashMap<>(materialLimits);
    }
}