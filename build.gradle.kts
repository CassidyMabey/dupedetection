plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.7.2"
}

group = "notauthorised"
version = "1.0.0"
description = "A plugin to detect and prevent item duplication exploits"

java {
    // Configure the java toolchain. This allows gradle to auto-provision JDK 21 on systems that only have JDK 8 installed for example.
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
}

dependencies {
    // Paper API
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