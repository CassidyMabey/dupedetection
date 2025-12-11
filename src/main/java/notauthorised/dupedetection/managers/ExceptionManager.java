package notauthorised.dupedetection.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import notauthorised.dupedetection.DupeDetectionPlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ExceptionManager {
    
    private final DupeDetectionPlugin plugin;
    private final File exceptionsFile;
    private final Gson gson;
    private Set<String> exceptions;
    
    public ExceptionManager(DupeDetectionPlugin plugin) {
        this.plugin = plugin;
        this.exceptionsFile = new File(plugin.getDataFolder(), "exceptions.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.exceptions = new HashSet<>();
        
        loadExceptions();
    }
    
    public void loadExceptions() {
        if (!exceptionsFile.exists()) {
            // create file
            saveExceptions();
            return;
        }
        
        try (FileReader reader = new FileReader(exceptionsFile)) {
            Type setType = new TypeToken<Set<String>>(){}.getType();
            Set<String> loadedExceptions = gson.fromJson(reader, setType);
            
            if (loadedExceptions != null) {
                this.exceptions = loadedExceptions;
                plugin.getLogger().info("Loaded " + exceptions.size() + " dupe detection exceptions");
            } else {
                this.exceptions = new HashSet<>();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[Dupe Detector] failed to get exeptions: " + e.getMessage());
            this.exceptions = new HashSet<>();
        }
    }
    
    public void saveExceptions() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(exceptionsFile)) {
                gson.toJson(exceptions, writer);
                plugin.getLogger().info("Saved " + exceptions.size() + " dupe detection exceptions");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[Dupe Detector] Failed to save exceptions: " + e.getMessage());
        }
    }
    
    public boolean isExempt(Player player) {
        if (player == null) {
            return false;
        }
        
        // use uuid
        String uuid = player.getUniqueId().toString();
        if (exceptions.contains(uuid)) {
            return true;
        }
        
        // if uuid doesnt work use username
        String username = player.getName();
        if (exceptions.contains(username)) {
            return true;
        }
        
        // then just not be case sensitive which doesnt really matter for mc
        return exceptions.stream().anyMatch(exception -> 
            exception.equalsIgnoreCase(username));
    }
    
    public boolean addException(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        
        identifier = identifier.trim();
        
        try {
            UUID uuid = UUID.fromString(identifier);
            identifier = uuid.toString();
        } catch (IllegalArgumentException e) {
            // means its a username
        }
        
        boolean added = exceptions.add(identifier);
        if (added) {
            saveExceptions();
        }
        return added;
    }
    
    public boolean removeException(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        
        identifier = identifier.trim();
        
        try {
            UUID uuid = UUID.fromString(identifier);
            identifier = uuid.toString();
        } catch (IllegalArgumentException e) {
        }
        
        boolean removed = exceptions.remove(identifier);
        
        // case insensitive
        if (!removed) {
            final String finalIdentifier = identifier;
            removed = exceptions.removeIf(exception -> exception.equalsIgnoreCase(finalIdentifier));
        }
        
        if (removed) {
            saveExceptions();
        }
        return removed;
    }
    
    public Set<String> getExceptions() {
        return new HashSet<>(exceptions);
    }
    
    public void clearExceptions() {
        exceptions.clear();
        saveExceptions();
    }
    
    public int getExceptionCount() {
        return exceptions.size();
    }
}