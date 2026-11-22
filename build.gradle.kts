plugins {
    java
}

group = "dev.chainvein"
version = "0.3.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") {
        content {
            includeGroup("io.papermc.paper")
            includeGroup("com.mojang")
            includeGroup("net.md-5")
        }
    }
    maven("https://repo.helpch.at/releases/") {
        content { includeGroup("me.clip") }
    }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    val placeholderApiJar = providers.gradleProperty("placeholderApiJar")
    if (placeholderApiJar.isPresent) {
        compileOnly(files(placeholderApiJar.get()))
    } else {
        compileOnly("me.clip:placeholderapi:2.12.3") { isTransitive = false }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}
