package notauthorised.dupedetection.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import notauthorised.dupedetection.DupeDetectionPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhookManager {
    
    private final DupeDetectionPlugin plugin;
    private final HttpClient httpClient;
    private final Gson gson;
    
    public DiscordWebhookManager(DupeDetectionPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }
    
    public void sendViolationAlert(Player player, Material material, int totalAmount, int limit, 
                                 Location containerLocation, InventoryType containerType) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) {
            return;
        }
        
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url");
        if (webhookUrl == null || webhookUrl.contains("YOUR_WEBHOOK")) {
            plugin.getLogger().warning("[Dupe Detector] Webhook not set");
            return;
        }
        
        // send ascynchronously to not lag the main netty thread
        CompletableFuture.runAsync(() -> {
            try {
                sendWebhook(webhookUrl, player, material, totalAmount, limit, containerLocation, containerType);
            } catch (Exception e) {
                plugin.getLogger().severe("[Dupe Detector] Failed to send a detected dupe: " + e.getMessage());
                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    public void sendMultipleViolationAlert(Player player, Map<Material, Integer> violations, Map<Material, Integer> limits,
                                         Location containerLocation, InventoryType containerType) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) {
            return;
        }
        
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url");
        if (webhookUrl == null || webhookUrl.contains("YOUR_WEBHOOK")) {
            plugin.getLogger().warning("Discord webhook URL not configured properly!");
            return;
        }
        
        // Send webhook asynchronously to avoid blocking the main thread
        CompletableFuture.runAsync(() -> {
            try {
                sendMultipleViolationWebhook(webhookUrl, player, violations, limits, containerLocation, containerType);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to send Discord webhook: " + e.getMessage());
                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    public void sendNBTViolationAlert(Player player, Map<String, java.util.List<org.bukkit.inventory.ItemStack>> violations, 
                                    int minimumDuplicates, Location containerLocation, InventoryType containerType) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) {
            return;
        }
        
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url");
        if (webhookUrl == null || webhookUrl.contains("YOUR_WEBHOOK")) {
            plugin.getLogger().warning("Discord webhook URL not configured properly!");
            return;
        }
        
        // Send webhook asynchronously to avoid blocking the main thread
        CompletableFuture.runAsync(() -> {
            try {
                sendNBTViolationWebhook(webhookUrl, player, violations, minimumDuplicates, containerLocation, containerType);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to send Discord NBT webhook: " + e.getMessage());
                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    private void sendMultipleViolationWebhook(String webhookUrl, Player player, Map<Material, Integer> violations, 
                                            Map<Material, Integer> limits, Location containerLocation, 
                                            InventoryType containerType) throws IOException, InterruptedException {
        
        JsonObject payload = createMultipleViolationPayload(player, violations, limits, containerLocation, containerType);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 204) {
            plugin.getLogger().warning("Discord webhook returned status code: " + response.statusCode());
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().warning("Response body: " + response.body());
            }
        }
    }
    
    private void sendNBTViolationWebhook(String webhookUrl, Player player, Map<String, java.util.List<org.bukkit.inventory.ItemStack>> violations,
                                       int minimumDuplicates, Location containerLocation, InventoryType containerType) 
                                       throws IOException, InterruptedException {
        
        JsonObject payload = createNBTViolationPayload(player, violations, minimumDuplicates, containerLocation, containerType);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 204) {
            plugin.getLogger().warning("Discord NBT webhook returned status code: " + response.statusCode());
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().warning("Response body: " + response.body());
            }
        }
    }
    
    private void sendWebhook(String webhookUrl, Player player, Material material, int totalAmount, int limit,
                           Location containerLocation, InventoryType containerType) throws IOException, InterruptedException {
        
        JsonObject payload = createWebhookPayload(player, material, totalAmount, limit, containerLocation, containerType);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 204) {
            plugin.getLogger().warning("Discord webhook returned status code: " + response.statusCode());
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().warning("Response body: " + response.body());
            }
        }
    }
    
    private JsonObject createWebhookPayload(Player player, Material material, int totalAmount, int limit,
                                          Location containerLocation, InventoryType containerType) {
        JsonObject payload = new JsonObject();
        
        // Add mention if enabled
        if (plugin.getConfig().getBoolean("discord.mention-everyone", true)) {
            payload.addProperty("content", "@everyone");
        }
        
        // Add username and avatar
        String username = plugin.getConfig().getString("discord.username", "DupeDetection Bot");
        payload.addProperty("username", username);
        
        String avatarUrl = plugin.getConfig().getString("discord.avatar-url");
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            payload.addProperty("avatar_url", avatarUrl);
        }
        
        // Create embed
        JsonArray embeds = new JsonArray();
        JsonObject embed = createViolationEmbed(player, material, totalAmount, limit, containerLocation, containerType);
        embeds.add(embed);
        payload.add("embeds", embeds);
        
        return payload;
    }
    
    private JsonObject createMultipleViolationPayload(Player player, Map<Material, Integer> violations, 
                                                     Map<Material, Integer> limits, Location containerLocation, 
                                                     InventoryType containerType) {
        JsonObject payload = new JsonObject();
        
        // Add mention if enabled
        if (plugin.getConfig().getBoolean("discord.mention-everyone", true)) {
            payload.addProperty("content", "@everyone");
        }
        
        // Add username and avatar
        String username = plugin.getConfig().getString("discord.username", "DupeDetection Bot");
        payload.addProperty("username", username);
        
        String avatarUrl = plugin.getConfig().getString("discord.avatar-url");
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            payload.addProperty("avatar_url", avatarUrl);
        }
        
        // Create embed
        JsonArray embeds = new JsonArray();
        JsonObject embed = createMultipleViolationEmbed(player, violations, limits, containerLocation, containerType);
        embeds.add(embed);
        payload.add("embeds", embeds);
        
        return payload;
    }
    
    private JsonObject createViolationEmbed(Player player, Material material, int totalAmount, int limit,
                                         Location containerLocation, InventoryType containerType) {
        JsonObject embed = new JsonObject();
        
        // Title and description
        embed.addProperty("title", "🚨 Dupe Detection Alert");
        embed.addProperty("description", "Suspicious item stacking detected!");
        embed.addProperty("color", 0xFF0000); // Red color
        
        // Add timestamp
        embed.addProperty("timestamp", Instant.now().toString());
        
        // Add fields
        JsonArray fields = new JsonArray();
        
        // Player field
        JsonObject playerField = new JsonObject();
        playerField.addProperty("name", "👤 Player");
        playerField.addProperty("value", "**" + player.getName() + "**" + System.lineSeparator() + "UUID: `" + player.getUniqueId() + "`");
        playerField.addProperty("inline", true);
        fields.add(playerField);
        
        // Violation details
        JsonObject violationField = new JsonObject();
        violationField.addProperty("name", "⚠️ Violation Details");
        violationField.addProperty("value", "**Item:** " + formatMaterialName(material) + 
                System.lineSeparator() + "**Amount:** " + totalAmount + 
                System.lineSeparator() + "**Limit:** " + limit + 
                System.lineSeparator() + "**Excess:** " + (totalAmount - limit));
        violationField.addProperty("inline", true);
        fields.add(violationField);
        
        // Container information
        JsonObject containerField = new JsonObject();
        containerField.addProperty("name", "📦 Container Details");
        
        // Check if it's a trapped chest by looking at the block material
        String containerTypeStr = formatContainerType(containerType);
        if (containerLocation != null && containerLocation.getBlock().getType() == Material.TRAPPED_CHEST) {
            containerTypeStr = "⚠️ Trapped Chest";
        }
        
        containerField.addProperty("value", "**Type:** " + containerTypeStr + 
                System.lineSeparator() + "**World:** " + containerLocation.getWorld().getName() + 
                System.lineSeparator() + "**Location:** " + containerLocation.getBlockX() + ", " + 
                containerLocation.getBlockY() + ", " + containerLocation.getBlockZ());
        containerField.addProperty("inline", false);
        fields.add(containerField);
        
        // Teleport command field
        JsonObject teleportField = new JsonObject();
        teleportField.addProperty("name", "🎯 Quick Actions");
        String teleportCommand = "```" + System.lineSeparator() +
                               "/tp " + containerLocation.getBlockX() + " " + 
                               containerLocation.getBlockY() + " " + containerLocation.getBlockZ() +
                               System.lineSeparator() + "```";
        teleportField.addProperty("value", "**Teleport Command:**" + System.lineSeparator() + teleportCommand);
        teleportField.addProperty("inline", false);
        fields.add(teleportField);
        
        // Server info
        JsonObject serverField = new JsonObject();
        serverField.addProperty("name", "🖥️ Server Information");
        serverField.addProperty("value", "**Server:** " + plugin.getServer().getName() + 
                System.lineSeparator() + "**Time:** <t:" + (System.currentTimeMillis() / 1000) + ":F>");
        serverField.addProperty("inline", false);
        fields.add(serverField);
        
        embed.add("fields", fields);
        
        // Add footer
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "DupeDetection v" + plugin.getDescription().getVersion());
        embed.add("footer", footer);
        
        // Add thumbnail (player head)
        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", "https://crafatar.com/avatars/" + player.getUniqueId() + "?size=64&overlay");
        embed.add("thumbnail", thumbnail);
        
        return embed;
    }
    
    private JsonObject createMultipleViolationEmbed(Player player, Map<Material, Integer> violations, 
                                                   Map<Material, Integer> limits, Location containerLocation, 
                                                   InventoryType containerType) {
        JsonObject embed = new JsonObject();
        
        // Title and description
        embed.addProperty("title", "🚨 Multiple Dupe Detection Alerts");
        embed.addProperty("description", "Multiple suspicious item stacks detected in same container!");
        embed.addProperty("color", 0xFF0000); // Red color
        
        // Add timestamp
        embed.addProperty("timestamp", Instant.now().toString());
        
        // Add fields
        JsonArray fields = new JsonArray();
        
        // Player field
        JsonObject playerField = new JsonObject();
        playerField.addProperty("name", "👤 Player");
        playerField.addProperty("value", "**" + player.getName() + "**" + System.lineSeparator() + "UUID: `" + player.getUniqueId() + "`");
        playerField.addProperty("inline", true);
        fields.add(playerField);
        
        // Violation details - create a list of all violations
        JsonObject violationField = new JsonObject();
        violationField.addProperty("name", "⚠️ Violations Found");
        StringBuilder violationText = new StringBuilder();
        for (Map.Entry<Material, Integer> entry : violations.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();
            int limit = limits.get(material);
            int excess = amount - limit;
            violationText.append("**").append(formatMaterialName(material)).append(":** ")
                        .append(amount).append(" (limit: ").append(limit).append(", excess: ").append(excess).append(")")
                        .append(System.lineSeparator());
        }
        violationField.addProperty("value", violationText.toString());
        violationField.addProperty("inline", true);
        fields.add(violationField);
        
        // Container information
        JsonObject containerField = new JsonObject();
        containerField.addProperty("name", "📦 Container Details");
        
        // Check if it's a trapped chest by looking at the block material
        String containerTypeStr = formatContainerType(containerType);
        if (containerLocation != null && containerLocation.getBlock().getType() == Material.TRAPPED_CHEST) {
            containerTypeStr = "⚠️ Trapped Chest";
        }
        
        String containerInfo = "**Type:** " + containerTypeStr + System.lineSeparator() +
                              "**World:** " + containerLocation.getWorld().getName() + System.lineSeparator() +
                              "**Location:** " + containerLocation.getBlockX() + ", " + 
                              containerLocation.getBlockY() + ", " + containerLocation.getBlockZ();
        containerField.addProperty("value", containerInfo);
        containerField.addProperty("inline", false);
        fields.add(containerField);
        
        // Teleport command field
        JsonObject teleportField = new JsonObject();
        teleportField.addProperty("name", "🎯 Quick Actions");
        String teleportCommand = "```" + System.lineSeparator() +
                               "/tp " + containerLocation.getBlockX() + " " + 
                               containerLocation.getBlockY() + " " + containerLocation.getBlockZ() +
                               System.lineSeparator() + "```";
        teleportField.addProperty("value", "**Teleport Command:**" + System.lineSeparator() + teleportCommand);
        teleportField.addProperty("inline", false);
        fields.add(teleportField);
        
        // Server info
        JsonObject serverField = new JsonObject();
        serverField.addProperty("name", "🖥️ Server Information");
        serverField.addProperty("value", "**Server:** " + plugin.getServer().getName() + 
                System.lineSeparator() + "**Time:** <t:" + (System.currentTimeMillis() / 1000) + ":F>");
        serverField.addProperty("inline", false);
        fields.add(serverField);
        
        embed.add("fields", fields);
        
        // Add footer
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "DupeDetection v" + plugin.getDescription().getVersion());
        embed.add("footer", footer);
        
        // Add thumbnail (player head)
        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", "https://crafatar.com/avatars/" + player.getUniqueId() + "?size=64&overlay");
        embed.add("thumbnail", thumbnail);
        
        return embed;
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
    
    private String formatContainerType(InventoryType type) {
        switch (type) {
            case CHEST:
                return "📦 Chest";
            case BARREL:
                return "🛢️ Barrel";
            case HOPPER:
                return "⬇️ Hopper";
            case DROPPER:
                return "📤 Dropper";
            case DISPENSER:
                return "🎯 Dispenser";
            case SHULKER_BOX:
                return "📦 Shulker Box";
            case ENDER_CHEST:
                return "🌌 Ender Chest";
            case FURNACE:
                return "🔥 Furnace";
            case BLAST_FURNACE:
                return "🔥 Blast Furnace";
            case SMOKER:
                return "🔥 Smoker";
            case BREWING:
                return "🧪 Brewing Stand";
            case LECTERN:
                return "📚 Lectern";
            case COMPOSTER:
                return "🌱 Composter";
            default:
                return "📦 " + type.name().replace("_", " ");
        }
    }
    
    private JsonObject createNBTViolationPayload(Player player, Map<String, java.util.List<org.bukkit.inventory.ItemStack>> violations,
                                               int minimumDuplicates, Location containerLocation, InventoryType containerType) {
        JsonObject payload = new JsonObject();
        
        // Add mention if enabled
        if (plugin.getConfig().getBoolean("discord.mention-everyone", true)) {
            payload.addProperty("content", "@everyone");
        }
        
        // Add username and avatar
        String username = plugin.getConfig().getString("discord.username", "DupeDetection Bot");
        payload.addProperty("username", username);
        
        String avatarUrl = plugin.getConfig().getString("discord.avatar-url");
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            payload.addProperty("avatar_url", avatarUrl);
        }
        
        // Create embed
        JsonArray embeds = new JsonArray();
        JsonObject embed = createNBTViolationEmbed(player, violations, minimumDuplicates, containerLocation, containerType);
        embeds.add(embed);
        payload.add("embeds", embeds);
        
        return payload;
    }
    
    private JsonObject createNBTViolationEmbed(Player player, Map<String, java.util.List<org.bukkit.inventory.ItemStack>> violations,
                                             int minimumDuplicates, Location containerLocation, InventoryType containerType) {
        JsonObject embed = new JsonObject();
        
        // Title and description
        embed.addProperty("title", "🔍 NBT Duplicate Detection Alert");
        embed.addProperty("description", "Identical NBT items detected - possible duplication exploit!");
        embed.addProperty("color", 0xFF4500); // Orange-red color for NBT violations
        
        // Add timestamp
        embed.addProperty("timestamp", Instant.now().toString());
        
        // Add fields
        JsonArray fields = new JsonArray();
        
        // Player field
        JsonObject playerField = new JsonObject();
        playerField.addProperty("name", "👤 Player");
        playerField.addProperty("value", "**" + player.getName() + "**" + System.lineSeparator() + "UUID: `" + player.getUniqueId() + "`");
        playerField.addProperty("inline", true);
        fields.add(playerField);
        
        // Detection threshold
        JsonObject thresholdField = new JsonObject();
        thresholdField.addProperty("name", "⚙️ Detection Settings");
        thresholdField.addProperty("value", "**Minimum Duplicates:** " + minimumDuplicates + System.lineSeparator() + 
                                          "**Detection Type:** Identical NBT Data");
        thresholdField.addProperty("inline", true);
        fields.add(thresholdField);
        
        // NBT violations - show each group of identical items
        JsonObject violationsField = new JsonObject();
        violationsField.addProperty("name", "🚨 Identical Items Found");
        StringBuilder violationText = new StringBuilder();
        
        int groupNum = 1;
        for (Map.Entry<String, java.util.List<org.bukkit.inventory.ItemStack>> entry : violations.entrySet()) {
            java.util.List<org.bukkit.inventory.ItemStack> items = entry.getValue();
            int totalAmount = items.stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
            Material material = items.get(0).getType();
            
            // Get item details
            org.bukkit.inventory.ItemStack sample = items.get(0);
            String itemDetails = getItemDetails(sample);
            
            violationText.append("**Group ").append(groupNum++).append(":** ")
                        .append(totalAmount).append("x ").append(formatMaterialName(material))
                        .append(System.lineSeparator())
                        .append("*").append(itemDetails).append("*")
                        .append(System.lineSeparator()).append(System.lineSeparator());
        }
        
        violationsField.addProperty("value", violationText.toString());
        violationsField.addProperty("inline", false);
        fields.add(violationsField);
        
        // Container information
        JsonObject containerField = new JsonObject();
        containerField.addProperty("name", "📦 Container Details");
        
        String containerTypeStr = formatContainerType(containerType);
        if (containerLocation != null && containerLocation.getBlock().getType() == Material.TRAPPED_CHEST) {
            containerTypeStr = "⚠️ Trapped Chest";
        }
        
        String containerInfo = "**Type:** " + containerTypeStr + System.lineSeparator() +
                              "**World:** " + containerLocation.getWorld().getName() + System.lineSeparator() +
                              "**Location:** " + containerLocation.getBlockX() + ", " + 
                              containerLocation.getBlockY() + ", " + containerLocation.getBlockZ();
        containerField.addProperty("value", containerInfo);
        containerField.addProperty("inline", false);
        fields.add(containerField);
        
        // Teleport command field
        JsonObject teleportField = new JsonObject();
        teleportField.addProperty("name", "🎯 Quick Actions");
        String teleportCommand = "```" + System.lineSeparator() +
                               "/tp " + containerLocation.getBlockX() + " " + 
                               containerLocation.getBlockY() + " " + containerLocation.getBlockZ() +
                               System.lineSeparator() + "```";
        teleportField.addProperty("value", "**Teleport Command:**" + System.lineSeparator() + teleportCommand);
        teleportField.addProperty("inline", false);
        fields.add(teleportField);
        
        // Server info
        JsonObject serverField = new JsonObject();
        serverField.addProperty("name", "🖥️ Server Information");
        serverField.addProperty("value", "**Server:** " + plugin.getServer().getName() + 
                System.lineSeparator() + "**Time:** <t:" + (System.currentTimeMillis() / 1000) + ":F>");
        serverField.addProperty("inline", false);
        fields.add(serverField);
        
        embed.add("fields", fields);
        
        // Add footer
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "DupeDetection v" + plugin.getDescription().getVersion() + " • NBT Duplicate Detection");
        embed.add("footer", footer);
        
        // Add thumbnail (player head)
        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", "https://crafatar.com/avatars/" + player.getUniqueId() + "?size=64&overlay");
        embed.add("thumbnail", thumbnail);
        
        return embed;
    }
    
    private String getItemDetails(org.bukkit.inventory.ItemStack item) {
        StringBuilder details = new StringBuilder();
        
        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            
            if (meta.hasDisplayName()) {
                details.append("Name: ").append(meta.getDisplayName()).append(" ");
            }
            
            if (meta.hasEnchants()) {
                details.append("Enchants: ").append(meta.getEnchants().size()).append(" ");
            }
            
            if (meta instanceof org.bukkit.inventory.meta.Damageable) {
                org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) meta;
                if (damageable.hasDamage()) {
                    details.append("Damage: ").append(damageable.getDamage()).append(" ");
                }
            }
        }
        
        return details.length() > 0 ? details.toString().trim() : "Standard item";
    }
}