import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    java
    id("com.gradleup.shadow") version "8.3.9"
}

val pluginName: String by project
val pluginMain: String by project
val pluginWebsite: String by project
val paperApiVersion: String by project
val apiVersion: String by project

group = project.group
version = project.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
        content {
            includeGroup("io.papermc.paper")
            includeGroup("com.mojang")
            includeGroup("net.md-5")
        }
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
        content { includeGroup("me.clip") }
    }
}

val hikariVersion = "6.3.0"
val sqliteVersion = "3.49.1.0"
val junitVersion = "6.0.3"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("org.jetbrains:annotations:26.0.2")

    // Shaded + relocated. slf4j is provided by the server.
    implementation("com.zaxxer:HikariCP:$hikariVersion") {
        exclude(group = "org.slf4j")
    }

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testCompileOnly("org.jetbrains:annotations:26.0.2")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-deprecation", "-Xlint:-removal", "-Xlint:-serial", "-Xlint:-processing", "-Xlint:-classfile"))
}

// ---------------------------------------------------------------------------
// Resource pack: zipped from /resourcepack and bundled inside the plugin jar.
// ---------------------------------------------------------------------------
val zipResourcePack by tasks.registering(Zip::class) {
    group = "build"
    description = "Packs the default LifeCore resource pack."
    from(layout.projectDirectory.dir("resourcepack"))
    archiveFileName.set("$pluginName-ResourcePack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/resourcepack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    val props = mapOf(
        "name" to pluginName,
        "version" to project.version.toString(),
        "main" to pluginMain,
        "website" to pluginWebsite,
        "apiVersion" to apiVersion,
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        filter<ReplaceTokens>("tokens" to props, "beginToken" to "@", "endToken" to "@")
    }
    from(zipResourcePack) { into("resourcepack") }
}

tasks.shadowJar {
    archiveBaseName.set(pluginName)
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "${project.group}.lifecore.libs.hikari")
    exclude("META-INF/maven/**", "META-INF/versions/**/module-info.class", "module-info.class")
    mergeServiceFiles()
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
