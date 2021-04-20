plugins {
    java
    scala
    id("com.github.johnrengelman.shadow") version "4.0.3"
    idea
    kotlin("jvm") version "1.3.50"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("it.unibo.alchemist:alchemist:_")
    implementation("it.unibo.alchemist:alchemist-maps:_")
    implementation("it.unibo.alchemist:alchemist-swingui:_")
    implementation("it.unibo.alchemist:alchemist-incarnation-protelis:_")
    implementation("it.unibo.alchemist:alchemist-incarnation-scafi:_")
    implementation("org.scala-lang:scala-library:2.13.4")
    implementation("it.unibo.scafi:scafi-core_2.13:_")
    //implementation("org.protelis:protelis-lang:_")
    implementation(kotlin("stdlib"))
}

tasks.withType<ScalaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

sourceSets.getByName("main") {
    resources {
        srcDirs("src/main/protelis")
    }
}

val alchemistGroup = "Run Alchemist"

fun createTask(name: String, fileName: String, effectsFile: String, extensionEffectsFile: String = "aes") = tasks.register<JavaExec>(name) {
    group = alchemistGroup // This is for better organization when running ./gradlew tasks
    description = "Launches simulation" // Just documentation
    main = "it.unibo.alchemist.Alchemist" // The class to launch
    classpath = sourceSets["main"].runtimeClasspath // The classpath to use
    // These are the program arguments
    args(
        "-y", "src/main/resources/yaml/${fileName}.yml",
        "-g", "src/main/resources/${effectsFile}.${extensionEffectsFile}"
    )
}

createTask("s", "s", "s")
createTask("clone", "clone", "gradient")
createTask("gradient", "gradient", "gradient")
createTask("crowdWithVirtuals", "crowdWithVirtuals", "gradientWithDest", "json")
createTask("runScafi", "crowdWarningScafi", "crowd")
createTask("runProtelis", "crowdWarningProtelis", "crowd")
