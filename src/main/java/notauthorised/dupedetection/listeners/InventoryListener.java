package notauthorised.dupedetection.listeners;

import notauthorised.dupedetection.DupeDetectionPlugin;
import notauthorised.dupedetection.managers.NBTDuplicateManager;
import notauthorised.dupedetection.managers.StackDetectionManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryListener implements Listener {
    
    private final DupeDetectionPlugin plugin;
    private final StackDetectionManager stackDetectionManager;
    private final NBTDuplicateManager nbtDuplicateManager;
    
    // cooldown so that it does not lag the entire server 5 seconds per check 
    private static final long INVENTORY_COOLDOWN = 5000;
    private final Map<UUID, Long> inventoryCooldowns = new HashMap<>();
    
    public InventoryListener(DupeDetectionPlugin plugin) {
        this.plugin = plugin;
        this.stackDetectionManager = new StackDetectionManager(plugin);
        this.nbtDuplicateManager = new NBTDuplicateManager(plugin);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        
        // check if the user has permissions to bypass
        if (player.hasPermission("dupedetection.bypass")) {
            return;
        }
        
        // Check if the player is exempted (this will literally only be me)
        if (plugin.getExceptionManager().isExempt(player)) {
            return;
        }
        
        // check if the large stack detection is enabled
        if (!plugin.getConfig().getBoolean("large-stack-detection.enabled", true)) {
            return;
        }
        
        checkPlayerInventory(player);
        
        // storage blocks
        if (isStorageBlock(event.getInventory().getType())) {
            // get location so i can TP if its a dupe
            Location containerLocation = null;
            InventoryType containerType = event.getInventory().getType();
            InventoryHolder holder = event.getInventory().getHolder();
            
            if (holder instanceof Block) {
                Block block = (Block) holder;
                containerLocation = block.getLocation();
                //if (block.getType() == org.bukkit.Material.TRAPPED_CHEST) {
                //   
                //}
            } else if (holder instanceof org.bukkit.block.BlockState) {
                org.bukkit.block.BlockState blockState = (org.bukkit.block.BlockState) holder;
                containerLocation = blockState.getLocation();
            }
            
            // if you cant get the holders location get where they are looking at instead
            if (containerLocation == null) {
                Block targetBlock = player.getTargetBlockExact(5);
                if (targetBlock != null && isStorageBlock(targetBlock.getType())) {
                    containerLocation = targetBlock.getLocation();
                }
            }
            
            // lastly if its the players location
            if (containerLocation == null) {
                containerLocation = player.getLocation();
            }
            
            
            checkContainer(player, event.getInventory(), containerLocation, containerType);
        } else if (event.getInventory().getType() == InventoryType.PLAYER) {
            // 
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().info("Player " + player.getName() + " closed inventory");
            }
        }
    }
    
    private void checkPlayerInventory(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // check cooldown 
        Long lastCheck = inventoryCooldowns.get(playerId);
        if (lastCheck != null && (currentTime - lastCheck) < INVENTORY_COOLDOWN) {
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().info("Player " + player.getName() + " inventory check on cooldown");
            }
            return; 
        }
        
        // set cooldown
        inventoryCooldowns.put(playerId, currentTime);
        
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Checking player inventory for " + player.getName());
        }
        
        // check for dupe 
        stackDetectionManager.checkPlayerInventory(player);
        nbtDuplicateManager.checkPlayerInventoryForNBTDuplicates(player);
    }
    
    private void checkContainer(Player player, org.bukkit.inventory.Inventory inventory, Location containerLocation, InventoryType containerType) {
        // check container contents
        stackDetectionManager.checkContainerInventory(player, inventory, containerLocation, containerType);
        
        // check for NBt duplicates (lots of items with the same data to them)
        nbtDuplicateManager.checkContainerForNBTDuplicates(player, inventory, containerLocation, containerType);
    }
    
    private boolean isStorageBlock(InventoryType type) {
        switch (type) {
            case CHEST:
            case BARREL:
            case HOPPER:
            case DROPPER:
            case DISPENSER:
            case SHULKER_BOX:
            case ENDER_CHEST:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case BREWING:
            case LECTERN:
            case COMPOSTER:
                return true;
            default:
                return false;
        }
    }
    
    private boolean isStorageBlock(org.bukkit.Material material) {
        switch (material) {
            case CHEST:
            case TRAPPED_CHEST:
            case BARREL:
            case HOPPER:
            case DROPPER:
            case DISPENSER:
            case SHULKER_BOX:
            case ENDER_CHEST:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case BREWING_STAND:
            case LECTERN:
            case COMPOSTER:
            case CAULDRON:
            case WATER_CAULDRON:
            case LAVA_CAULDRON:
            case POWDER_SNOW_CAULDRON:

            case WHITE_SHULKER_BOX:
            case ORANGE_SHULKER_BOX:
            case MAGENTA_SHULKER_BOX:
            case LIGHT_BLUE_SHULKER_BOX:
            case YELLOW_SHULKER_BOX:
            case LIME_SHULKER_BOX:
            case PINK_SHULKER_BOX:
            case GRAY_SHULKER_BOX:
            case LIGHT_GRAY_SHULKER_BOX:
            case CYAN_SHULKER_BOX:
            case PURPLE_SHULKER_BOX:
            case BLUE_SHULKER_BOX:
            case BROWN_SHULKER_BOX:
            case GREEN_SHULKER_BOX:
            case RED_SHULKER_BOX:
            case BLACK_SHULKER_BOX:
                return true;
            default:
                return false;
        }
    }
}