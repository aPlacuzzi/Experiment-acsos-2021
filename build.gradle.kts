import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    implementation("org.slf4j:slf4j-api:1.7.30")
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

val baseDataFileName: String? by project
val maxHeapRatioArg: String? by project
val timeArg: String? by project

fun makeTest(
    file: String,
    outputDir: String = "data",
    name: String = file,
    sampling: Double = 1.0,
    time: Double = Double.POSITIVE_INFINITY,
    vars: Set<String> = setOf(),
    maxHeap: Long? = null,
    taskSize: Int = 1024,
    threads: Int? = null,
    debug: Boolean = false,
    effects: String? = null
) {
    val time = timeArg?.toDouble() ?: time
    val maxHeapRatio: Double = maxHeapRatioArg?.toDouble() ?: 1.0
    val heap = if(threads != null) { threads*taskSize } else { maxHeap ?: (if (System.getProperty("os.name").toLowerCase().contains("linux")) {
        ByteArrayOutputStream().use { output ->
            exec {
                executable = "bash"
                args = listOf("-c", "cat /proc/meminfo | grep MemAvailable | grep -o '[0-9]*'")
                standardOutput = output
            }
            output.toString().trim().toLong() / 1024
        }
            .also { println("Detected ${it}MB RAM available.") }  * maxHeapRatio
    } else {
        // Guess 16GB RAM of which 2 used by the OS
        14.0 * 1024
    }).toLong()
    }

    val threadCount = threads ?: maxOf(1, minOf(Runtime.getRuntime().availableProcessors(), heap.toInt() / taskSize ))
    println("$name > Running on $threadCount threads and with maxHeapSize $heap")

    val today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    task<JavaExec>(name) {
        val datafilename = "${today}-" +  (baseDataFileName ?: name)
        dependsOn("build")
        classpath = sourceSets["main"].runtimeClasspath
        classpath("src/main/protelis")
        main = "it.unibo.alchemist.Alchemist"
        maxHeapSize = "${heap}m"
        jvmArgs("-XX:+AggressiveHeap")
        jvmArgs("-XX:-UseGCOverheadLimit")
        //jvmArgs("-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap") // https://stackoverflow.com/questions/38967991/why-are-my-gradle-builds-dying-with-exit-code-137
        if (debug) {
            jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044")
        }
        File(outputDir).mkdirs()
        args(
            "-y", "src/main/resources/yaml/${file}.yml",
            "-t", "$time",
            "-e", "$outputDir/$datafilename",
            "-p", threadCount,
            "-i", "$sampling"
        )
        if (vars.isNotEmpty()) {
            args("-b", "-var", *vars.toTypedArray())
        }
        if(effects != null){
            args("-g", effects)
        }
    }
    /*tasks {
        "runTests" {
            dependsOn("$name")
        }
    }*/
}


createTask("s", "s", "s")
createTask("clone", "clone", "gradient")
createTask("gradient", "gradient", "gradient")
createTask("crowdWithVirtuals", "crowdWithVirtuals", "caseStudy", "json")
createTask("runScafi", "crowdWarningScafi", "crowd")
createTask("runProtelis", "crowdWarningProtelis", "crowd")
makeTest(name="batch", file = "crowdWithVirtuals", time = 700.0, vars = setOf("random"), taskSize = 1500, threads = 1, sampling = 10.0)
makeTest(name="batch1", file = "crowdWithVirtuals1", time = 700.0, vars = setOf("random"), taskSize = 1500, threads = 1, sampling = 10.0, outputDir = "data1")
makeTest(name="batch2", file = "crowdWithVirtuals2", time = 700.0, vars = setOf("random"), taskSize = 1500, threads = 1, sampling = 10.0, outputDir = "data2")
makeTest(name="batch3", file = "crowdWithVirtuals3", time = 700.0, vars = setOf("random"), taskSize = 1500, threads = 1, sampling = 10.0, outputDir = "data3")//, effects = "caseStudy.json")