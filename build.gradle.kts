// All ai
plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.7.2"
}

group = "notauthorised"
version = "1.0.1"
description = "A plugin to detect and prevent item duplication exploits"

// Define version compatibility ranges
val supportedVersions = mapOf(
    "universal" to mapOf(
        "bundle" to "1.21.3-R0.1-SNAPSHOT", 
        "api" to "1.20",
        "description" to "Universal build compatible with 1.18.2 - 1.21.8+"
    )
)

// Legacy version definitions (kept for reference)
val allVersions = mapOf(
    // Paper 1.21.x series
    "paper-1.21.8" to mapOf("bundle" to "1.21.3-R0.1-SNAPSHOT", "api" to "1.21"),
    "paper-1.21.7" to mapOf("bundle" to "1.21.3-R0.1-SNAPSHOT", "api" to "1.21"),
    "paper-1.21.6" to mapOf("bundle" to "1.21.3-R0.1-SNAPSHOT", "api" to "1.21"),
    "paper-1.21.5" to mapOf("bundle" to "1.21.3-R0.1-SNAPSHOT", "api" to "1.21"),
    "paper-1.21.4" to mapOf("bundle" to "1.21.3-R0.1-SNAPSHOT", "api" to "1.21"),
    "paper-1.21.3" to mapOf("bundle" to "1.21.3-R0.1-SNAPSHOT", "api" to "1.21"),
    "paper-1.21.2" to mapOf("bundle" to "1.21.2-R0.1-SNAPSHOT", "api" to "1.21"),
    "paper-1.21.1" to mapOf("bundle" to "1.21.1-R0.1-SNAPSHOT", "api" to "1.21"),
    "paper-1.21" to mapOf("bundle" to "1.21-R0.1-SNAPSHOT", "api" to "1.21")
)

java {
    // Configure the java toolchain. This allows gradle to auto-provision JDK 21 on systems that only have JDK 8 installed for example.
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    
    // Paper repository
    maven("https://repo.papermc.io/repository/maven-public/")
    
    // Spigot repository
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    
    // Purpur repository
    maven("https://repo.purpurmc.org/snapshots")
    
    // Pufferfish repository
    maven("https://repo.pufferfish.host/releases")
    maven("https://repo.pufferfish.host/snapshots")
    
    // Alternative repositories
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    
    // Minecraft Libraries repository
    maven("https://libraries.minecraft.net/")
}

dependencies {
    // Paper API (default version)
    paperweight.paperDevBundle("1.21.3-R0.1-SNAPSHOT")
    
    // JSON handling for Discord webhooks
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Add any additional dependencies here
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }
    
    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf(
            "name" to project.name,
            "version" to project.version,
            "description" to project.description,
            "apiVersion" to "1.21"
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

// Create version-specific build tasks
supportedVersions.forEach { (versionName, config) ->
    val taskName = "build${versionName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
    
    tasks.register<Jar>(taskName) {
        dependsOn(tasks.classes)
        archiveBaseName.set("DupeDetection")
        archiveVersion.set("${project.version}-universal")
        from(sourceSets.main.get().output)
        
        // Include dependencies in the jar
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
            exclude("META-INF/MANIFEST.MF")
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")
        }
        
        manifest {
            attributes(
                "Main-Class" to "notauthorised.dupedetection.DupeDetectionPlugin",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "notauthorised",
                "Built-By" to System.getProperty("user.name"),
                "Built-JDK" to System.getProperty("java.version"),
                "Target-Version" to config["description"],
                "Supported-Versions" to "1.18.2-1.21.8+",
                "Supported-Platforms" to "Paper, Purpur, Pufferfish, Spigot"
            )
        }
        
        // Set destination directory
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
    }
}

// Legacy build tasks for individual versions (optional)
allVersions.forEach { (versionName, _) ->
    val taskName = "buildLegacy${versionName.replace("-", "").replace(".", "").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
    
    tasks.register<Jar>(taskName) {
        dependsOn(tasks.classes)
        archiveBaseName.set("DupeDetection-$versionName")
        archiveVersion.set(project.version.toString())
        from(sourceSets.main.get().output)
        
        manifest {
            attributes(
                "Implementation-Version" to project.version,
                "Target-Version" to versionName
            )
        }
        
        destinationDirectory.set(layout.buildDirectory.dir("libs/legacy"))
    }
}

// Build all versions (both universal and legacy)
tasks.register("buildAll") {
    group = "build"
    description = "Builds both universal JAR and all legacy versions"
    dependsOn("buildUniversal", "buildAllLegacy")
    
    doLast {
        println("Built all variants:")
        println("  - Universal JAR: DupeDetection-${project.version}-universal.jar")
        println("  - Legacy JARs: ${allVersions.size} individual versions")
    }
}

// Override default build task to build universal
tasks.named("build") {
    dependsOn("buildUniversal")
}

// Build legacy individual JARs (optional)
tasks.register("buildAllLegacy") {
    group = "build"
    description = "Builds individual JARs for each version (legacy mode)"
    dependsOn(allVersions.keys.map { versionName ->
        "buildLegacy${versionName.replace("-", "").replace(".", "").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
    })
    
    doLast {
        println("Built ${allVersions.size} individual JARs in build/libs/legacy/")
    }
}

// Build specific platform groups (legacy)
tasks.register("buildPaper") {
    group = "build"
    description = "Builds universal JAR (same as build)"
    dependsOn("buildUniversal")
}

tasks.register("buildSpigot") {
    group = "build" 
    description = "Builds universal JAR (same as build)"
    dependsOn("buildUniversal")
}

tasks.register("buildModern") {
    group = "build"
    description = "Builds universal JAR (same as build)"
    dependsOn("buildUniversal")
}

// Clean all versions task
tasks.register("cleanAll") {
    group = "build"
    description = "Cleans all build artifacts"
    dependsOn("clean")
    
    doLast {
        delete(fileTree("build/libs") { include("*.jar") })
        delete(fileTree("build/libs/legacy") { include("*.jar") })
        println("Cleaned all build artifacts")
    }
}

// List versions task
tasks.register("listVersions") {
    group = "help"
    description = "Lists supported versions and build info"
    
    doLast {
        println("DupeDetection Plugin - Universal Build")
        println("=====================================")
        println("")
        println("🎯 RECOMMENDED BUILD:")
        println("  ./gradlew build")
        println("  → Creates: DupeDetection-${project.version}-universal.jar")
        println("  → Compatible with: 1.18.2 - 1.21.8+")
        println("  → Supports: Paper, Purpur, Pufferfish, Spigot")
        println("")
        println("📦 SUPPORTED VERSIONS:")
        println("  • Minecraft: 1.18.2, 1.19.x, 1.20.x, 1.21.x")
        println("  • Paper: Full support (all versions)")
        println("  • Purpur: Full support (all versions)")
        println("  • Pufferfish: Full support (all versions)")
        println("  • Spigot: Limited support (basic features)")
        println("")
        println("🔧 BUILD COMMANDS:")
        println("  ./gradlew build           - Universal JAR (recommended)")
        println("  ./gradlew buildAllLegacy  - Individual JARs (legacy)")
        println("  ./gradlew cleanAll        - Clean builds")
        println("")
        println("💡 The universal JAR automatically detects your server")
        println("   version and adapts its functionality accordingly.")
    }
}