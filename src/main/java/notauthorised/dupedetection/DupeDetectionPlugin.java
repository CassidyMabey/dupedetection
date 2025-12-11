package notauthorised.dupedetection;

import notauthorised.dupedetection.listeners.InventoryListener;
import notauthorised.dupedetection.managers.ExceptionManager;
import notauthorised.dupedetection.managers.StackDetectionManager;
import notauthorised.dupedetection.util.VersionCompatibility;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DupeDetectionPlugin extends JavaPlugin implements TabCompleter {

    private StackDetectionManager stackDetectionManager;
    private ExceptionManager exceptionManager;

    @Override
    public void onEnable() {
        // check version compatability
        if (!isVersionSupported()) {
            getLogger().severe("==============================================");
            getLogger().severe("UNSUPPORTED SERVER VERSION DETECTED!");
            getLogger().severe("This plugin supports Minecraft 1.18.2 - 1.21.8+");
            getLogger().severe("Your version: " + VersionCompatibility.getServerVersion());
            getLogger().severe("Plugin will be disabled for safety.");
            getLogger().severe("==============================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // log version
        VersionCompatibility.logVersionInfo(this);
        
        // warn about spigot
        if (VersionCompatibility.isSpigot()) {
            getLogger().warning("========================================");
            getLogger().warning("RUNNING ON SPIGOT - LIMITED FEATURES");
            getLogger().warning("For best performance and features,");
            getLogger().warning("consider switching to Paper or Purpur.");
            getLogger().warning("Some advanced features may not work.");
            getLogger().warning("========================================");
        }
        
        // save default config
        saveDefaultConfig();
        
        // initialize managers
        stackDetectionManager = new StackDetectionManager(this);
        exceptionManager = new ExceptionManager(this);
        
        // register event listeners
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        
        // register tab completion
        getCommand("dupedetection").setTabCompleter(this);
        
        getLogger().info("DupeDetection has been enabled!");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Large stack detection: " + (getConfig().getBoolean("large-stack-detection.enabled", true) ? "Enabled" : "Disabled"));
    }

    @Override
    public void onDisable() {
        getLogger().info("DupeDetection has been disabled!");
    }

    public ExceptionManager getExceptionManager() {
        return exceptionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("dupedetection")) {
            if (args.length == 0) {
                sender.sendMessage("§eDupeDetection §7v" + getDescription().getVersion());
                sender.sendMessage("§7Use §e/dupedetection help §7for commands");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "reload":
                    if (!sender.hasPermission("dupedetection.admin")) {
                        sender.sendMessage("§cYou don't have permission to use this command!");
                        return true;
                    }
                    reloadConfig();
                    stackDetectionManager.loadLimits();
                    sender.sendMessage("§aDupeDetection configuration reloaded!");
                    return true;

                case "info":
                    sender.sendMessage("§eDupeDetection §7v" + getDescription().getVersion());
                    sender.sendMessage("§7A plugin to detect and prevent item duplication exploits");
                    sender.sendMessage("§7Large stack detection: " + (getConfig().getBoolean("large-stack-detection.enabled", true) ? "§aEnabled" : "§cDisabled"));
                    sender.sendMessage("§7NBT duplicate detection: " + (getConfig().getBoolean("nbt-duplicate-detection.enabled", true) ? "§aEnabled" : "§cDisabled"));
                    return true;

                case "limits":
                    if (!sender.hasPermission("dupedetection.admin")) {
                        sender.sendMessage("§cYou don't have permission to use this command!");
                        return true;
                    }
                    showStackLimits(sender);
                    return true;

                case "setlimit":
                    if (!sender.hasPermission("dupedetection.admin")) {
                        sender.sendMessage("§cYou don't have permission to use this command!");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage: /dupedetection setlimit <material> <amount>");
                        return true;
                    }
                    setStackLimit(sender, args[1], args[2]);
                    return true;

                case "check":
                    if (!sender.hasPermission("dupedetection.admin")) {
                        sender.sendMessage("§cYou don't have permission to use this command!");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /dupedetection check <player>");
                        return true;
                    }
                    checkPlayer(sender, args[1]);
                    return true;

                case "nbtitems":
                    if (!sender.hasPermission("dupedetection.admin")) {
                        sender.sendMessage("§cYou don't have permission to use this command!");
                        return true;
                    }
                    showNBTItems(sender);
                    return true;

                case "exception":
                case "exceptions":
                    if (!sender.hasPermission("dupedetection.admin")) {
                        sender.sendMessage("§cYou don't have permission to use this command!");
                        return true;
                    }
                    handleExceptionCommand(sender, args);
                    return true;

                case "help":
                    sender.sendMessage("§eDupeDetection Commands:");
                    sender.sendMessage("§e/dupedetection info §7- Show plugin information");
                    if (sender.hasPermission("dupedetection.admin")) {
                        sender.sendMessage("§e/dupedetection reload §7- Reload configuration");
                        sender.sendMessage("§e/dupedetection limits §7- Show current stack limits");
                        sender.sendMessage("§e/dupedetection setlimit <material> <amount> §7- Set stack limit");
                        sender.sendMessage("§e/dupedetection check <player> §7- Check player inventory");
                        sender.sendMessage("§e/dupedetection nbtitems §7- Show monitored NBT items");
                        sender.sendMessage("§e/dupedetection exception add <player/uuid> §7- Add exception");
                        sender.sendMessage("§e/dupedetection exception remove <player/uuid> §7- Remove exception");
                        sender.sendMessage("§e/dupedetection exception list §7- List all exceptions");
                        sender.sendMessage("§e/dupedetection exception clear §7- Clear all exceptions");
                    }
                    return true;

                default:
                    sender.sendMessage("§cUnknown command! Use §e/dupedetection help");
                    return true;
            }
        }
        return false;
    }
    
    private void showStackLimits(CommandSender sender) {
        Map<Material, Integer> limits = stackDetectionManager.getMaterialLimits();
        
        if (limits.isEmpty()) {
            sender.sendMessage("§eNo stack limits configured.");
            return;
        }
        
        sender.sendMessage("§e=== Current Stack Limits ===");
        for (Map.Entry<Material, Integer> entry : limits.entrySet()) {
            sender.sendMessage("§7" + entry.getKey().name() + ": §a" + entry.getValue());
        }
        sender.sendMessage("§e========================");
    }
    
    private void setStackLimit(CommandSender sender, String materialName, String amountStr) {
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            int amount = Integer.parseInt(amountStr);
            
            if (amount < 0) {
                sender.sendMessage("§cAmount must be positive!");
                return;
            }
            
            // Uupdate config
            getConfig().set("large-stack-detection.limits." + material.name(), amount);
            saveConfig();
            
            //  reload limits
            stackDetectionManager.loadLimits();
            
            sender.sendMessage("§aSet stack limit for §e" + material.name() + " §ato §e" + amount);
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount: " + amountStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cInvalid material: " + materialName);
        }
    }
    
    private void checkPlayer(CommandSender sender, String playerName) {
        Player target = getServer().getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + playerName);
            return;
        }
        
        sender.sendMessage("§eChecking inventory of §a" + target.getName() + "§e...");
        stackDetectionManager.checkPlayerInventory(target);
        sender.sendMessage("§aInventory check completed!");
    }
    
    public StackDetectionManager getStackDetectionManager() {
        return stackDetectionManager;
    }
    
    private void showNBTItems(CommandSender sender) {
        List<String> monitoredItems = getConfig().getStringList("nbt-duplicate-detection.monitored-items");
        
        if (monitoredItems.isEmpty()) {
            sender.sendMessage("§eNo NBT items are being monitored.");
            return;
        }
        
        sender.sendMessage("§e=== NBT Monitored Items ===");
        sender.sendMessage("§7Minimum duplicates: §a" + getConfig().getInt("nbt-duplicate-detection.minimum-duplicates", 3));
        sender.sendMessage("§7Action: §a" + getConfig().getString("nbt-duplicate-detection.action", "WARN"));
        sender.sendMessage("§7Monitored items:");
        
        for (String item : monitoredItems) {
            sender.sendMessage("§7- §a" + item);
        }
        sender.sendMessage("§e========================");
    }
    
    private void handleExceptionCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /dupedetection exception <add/remove/list/clear> [player/uuid]");
            return;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "add":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /dupedetection exception add <player/uuid>");
                    return;
                }
                
                String addTarget = args[2];
                if (exceptionManager.addException(addTarget)) {
                    sender.sendMessage("§aAdded §e" + addTarget + " §ato dupe detection exceptions.");
                    sender.sendMessage("§7Total exceptions: §e" + exceptionManager.getExceptionCount());
                } else {
                    sender.sendMessage("§cFailed to add exception (may already exist).");
                }
                break;
                
            case "remove":
            case "delete":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /dupedetection exception remove <player/uuid>");
                    return;
                }
                
                String removeTarget = args[2];
                if (exceptionManager.removeException(removeTarget)) {
                    sender.sendMessage("§aRemoved §e" + removeTarget + " §afrom dupe detection exceptions.");
                    sender.sendMessage("§7Total exceptions: §e" + exceptionManager.getExceptionCount());
                } else {
                    sender.sendMessage("§cException not found: §e" + removeTarget);
                }
                break;
                
            case "list":
                var exceptions = exceptionManager.getExceptions();
                if (exceptions.isEmpty()) {
                    sender.sendMessage("§eNo dupe detection exceptions configured.");
                    return;
                }
                
                sender.sendMessage("§e=== Dupe Detection Exceptions ===");
                sender.sendMessage("§7Total: §e" + exceptions.size());
                for (String exception : exceptions) {
                    // check for uuid
                    try {
                        java.util.UUID.fromString(exception);
                        sender.sendMessage("§7- §a" + exception + " §7(UUID)");
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage("§7- §a" + exception + " §7(Username)");
                    }
                }
                sender.sendMessage("§e================================");
                break;
                
            case "clear":
                int count = exceptionManager.getExceptionCount();
                exceptionManager.clearExceptions();
                sender.sendMessage("§aCleared §e" + count + " §adupe detection exceptions.");
                break;
                
            case "reload":
                exceptionManager.loadExceptions();
                sender.sendMessage("§aReloaded dupe detection exceptions from file.");
                sender.sendMessage("§7Total exceptions: §e" + exceptionManager.getExceptionCount());
                break;
                
            default:
                sender.sendMessage("§cUnknown action! Use: add, remove, list, clear, reload");
                break;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("dupedetection")) {
            return null;
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // tab command completions
            List<String> subCommands = Arrays.asList("info", "help");
            
            if (sender.hasPermission("dupedetection.admin")) {
                subCommands = Arrays.asList("info", "reload", "limits", "setlimit", "check", "nbtitems", "exception", "exceptions", "help");
            }
            
            String input = args[0].toLowerCase();
            for (String cmd : subCommands) {
                if (cmd.toLowerCase().startsWith(input)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("exception") || subCommand.equals("exceptions")) {
                // exception sub-commands
                List<String> exceptionCommands = Arrays.asList("add", "remove", "list", "clear", "reload");
                String input = args[1].toLowerCase();
                for (String cmd : exceptionCommands) {
                    if (cmd.toLowerCase().startsWith(input)) {
                        completions.add(cmd);
                    }
                }
            } else if (subCommand.equals("setlimit")) {
                // material names
                String input = args[1].toUpperCase();
                for (Material material : Material.values()) {
                    if (material.name().startsWith(input)) {
                        completions.add(material.name());
                    }
                }
            } else if (subCommand.equals("check")) {
                // online players
                String input = args[1].toLowerCase();
                for (Player player : getServer().getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String action = args[1].toLowerCase();
            
            if ((subCommand.equals("exception") || subCommand.equals("exceptions")) && 
                (action.equals("add") || action.equals("remove"))) {
                // player names
                String input = args[2].toLowerCase();
                for (Player player : getServer().getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
                
                // show other exceptions
                if (action.equals("remove")) {
                    for (String exception : exceptionManager.getExceptions()) {
                        if (exception.toLowerCase().startsWith(input)) {
                            completions.add(exception);
                        }
                    }
                }
            }
        }
        
        return completions;
    }
    

    private boolean isVersionSupported() {
        return VersionCompatibility.is118() || 
               VersionCompatibility.is119() || 
               VersionCompatibility.is120() || 
               VersionCompatibility.is121();
    }
}