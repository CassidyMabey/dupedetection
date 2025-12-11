package notauthorised.dupedetection.util;

import org.bukkit.Bukkit;


public class VersionCompatibility {
    
    private static final String SERVER_VERSION = Bukkit.getBukkitVersion();
    private static final String NMS_VERSION = getNMSVersionSafe();
    
    // version detection
    private static final boolean IS_1_18 = SERVER_VERSION.contains("1.18");
    private static final boolean IS_1_19 = SERVER_VERSION.contains("1.19");
    private static final boolean IS_1_20 = SERVER_VERSION.contains("1.20");
    private static final boolean IS_1_21 = SERVER_VERSION.contains("1.21");
    
    // platform detection
    private static final boolean IS_PAPER = checkPaper();
    private static final boolean IS_PURPUR = checkPurpur();
    private static final boolean IS_PUFFERFISH = checkPufferfish();
    private static final boolean IS_SPIGOT = checkSpigot();
    
    /**
     * Safely get NMS version without throwing exceptions
     */
    private static String getNMSVersionSafe() {
        try {
            String[] parts = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
            if (parts.length > 3) {
                return parts[3];
            } else {
                // Modern Paper doesn't use versioned packages
                return "modern";
            }
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    // get version
    public static String getServerVersion() {
        return SERVER_VERSION;
    }
    
    
    public static String getNMSVersion() {
        return NMS_VERSION;
    }
    
    // check 1.18
    public static boolean is118() {
        return IS_1_18;
    }
    
    // check 1.19
    public static boolean is119() {
        return IS_1_19;
    }
    
    // check 1.20
    public static boolean is120() {
        return IS_1_20;
    }
    
    // check 1.21
    public static boolean is121() {
        return IS_1_21;
    }
    
    // check 1.20 or 1.21
    public static boolean isModern() {
        return IS_1_20 || IS_1_21;
    }
    
    // check 1.18 or 1.19
    public static boolean isLegacy() {
        return IS_1_18 || IS_1_19;
    }
    
    // check if its paper
    private static boolean checkPaper() {
        return hasClass("io.papermc.paper.configuration.Configuration") ||
               hasClass("com.destroystokyo.paper.PaperConfig");
    }
    
    // check if its purpur
    private static boolean checkPurpur() {
        return hasClass("org.purpurmc.purpur.PurpurConfig");
    }
    
    // check if its pufferfish
    private static boolean checkPufferfish() {
        return hasClass("gg.pufferfish.pufferfish.PufferfishConfig");
    }
    
    // check if its spigot
    private static boolean checkSpigot() {
        return hasClass("org.spigotmc.SpigotConfig") && !checkPaper();
    }
    
    // check if running paper
    public static boolean isPaper() {
        return IS_PAPER;
    }
    
    // check if running purpur
    public static boolean isPurpur() {
        return IS_PURPUR;
    }
    
    // check if its running pufferfish
    public static boolean isPufferfish() {
        return IS_PUFFERFISH;
    }
    
    // check its running spigot
    public static boolean isSpigot() {
        return IS_SPIGOT;
    }
    
    // get platform name
    public static String getPlatform() {
        if (IS_PURPUR) return "Purpur";
        if (IS_PUFFERFISH) return "Pufferfish";
        if (IS_PAPER) return "Paper";
        if (IS_SPIGOT) return "Spigot";
        return "Unknown";
    }
    
    // check if support advanced features
    public static boolean supportsAdvancedFeatures() {
        return IS_PAPER || IS_PURPUR || IS_PUFFERFISH;
    }
    
    // check if nbt support
    public static boolean hasNBTSupport() {
        return true;
    }
    
    // check if discord is supported
    public static boolean supportsDiscordFeatures() {
        return true; 
    }
    
    // get compatibility info
    public static String getCompatibilityInfo() {
        return String.format("Running %s %s (Compatible: %s)", 
            getPlatform(), 
            getServerVersion(), 
            supportsAdvancedFeatures() ? "Full" : "Limited"
        );
    }
    
    // check if class exists
    private static boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    // log version
    public static void logVersionInfo(org.bukkit.plugin.Plugin plugin) {
        plugin.getLogger().info("=== Version Compatibility Info ===");
        plugin.getLogger().info("Platform: " + getPlatform());
        plugin.getLogger().info("Version: " + getServerVersion());
        plugin.getLogger().info("NMS: " + getNMSVersion());
        plugin.getLogger().info("Advanced Features: " + (supportsAdvancedFeatures() ? "Enabled" : "Limited"));
        plugin.getLogger().info("================================");
    }
}