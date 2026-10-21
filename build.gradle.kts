plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "9.0.1"
}

group = "io.github.md5sha256"
version = "1.1.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven("https://repo.nexomc.com/releases")
}

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("io.papermc.paper:paper-api:26.3.build.206-stable")
    implementation("org.incendo:cloud-paper:2.0.1")
    implementation("com.github.stefvanschie.inventoryframework:IF:0.12.2")
    implementation("org.spongepowered:configurate-yaml:4.1.2")
    compileOnly("com.nexomc:nexo:1.10.0")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("io.papermc.paper:paper-api:26.3.build.206-stable")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("commons-lang:commons-lang:2.6")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(25))

tasks {
    compileJava {
        options.release.set(25)
    }
    processResources {
        val version = project.version
        filesMatching("paper-plugin.yml") {
            expand("version" to version)
        }
    }
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin"s jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.3")
    }
    shadowJar {
        val base = "io.github.md5sha256.recipeviewer.libraries"
        relocate("org.incendo", "${base}.org.incendo")
        relocate("com.github.stefvanschie", "${base}.com.github.stefvanschie")
        relocate("org.spongepowered.configurate", "${base}.org.spongepowered.configurate")
        relocate("org.yaml.snakeyaml", "${base}.org.yaml.snakeyaml")
        relocate("io.leangen.geantyref", "${base}.io.leangen.geantyref")
    }
}
tasks.test {
    useJUnitPlatform()
    workingDir(layout.buildDirectory.dir("test-runtime"))
    doFirst { workingDir.mkdirs() }
}

// Supply a newline-separated classpath to exercise an exact installed Paper build
// without starting a server or reading plugin data.
providers.gradleProperty("paperRuntimeClasspath").orNull?.let { path ->
    tasks.test {
        classpath = files(file(path).readLines().filter { it.isNotBlank() }) + classpath
        systemProperty("recipeviewer.actualPaper", "true")
    }
}

tasks.build { dependsOn(tasks.shadowJar) }
