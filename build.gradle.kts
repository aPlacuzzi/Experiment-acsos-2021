import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    java
    scala
    id("com.github.johnrengelman.shadow") version "4.0.3"
    idea
    kotlin("jvm") version "1.3.50"
    `maven`
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
    implementation(kotlin("stdlib"))

    implementation("commons-io:commons-io:_")
    implementation("org.danilopianini:jirf:_")
    implementation("com.graphhopper:graphhopper-core:_")
    implementation("com.graphhopper:graphhopper-reader-osm:_"){
        exclude(module = "slf4j-log4j12")
    }
    implementation("org.danilopianini:thread-inheritable-resource-loader:_")
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
        group = alchemistGroup // This is for better organization when running ./gradlew tasks
        val datafilename = "${today}-" +  (baseDataFileName ?: name)
        dependsOn("build")
        classpath = sourceSets["main"].runtimeClasspath
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
            args("-g",  "src/main/resources/${effects}")
        }
    }
    /*tasks {
        "runTests" {
            dependsOn("$name")
        }
    }*/
}

makeTest(name="caseStudyTd1000", file = "crowdWithVirtualsTd", time = 1000.0, vars = setOf("random", "gridStep"), taskSize = 1500, sampling = 1.0)
makeTest(name="caseStudyTd1000seed0", file = "crowdWithVirtualsTd", time = 1000.0, vars = setOf("gridStep"), taskSize = 1500, sampling = 1.0)
makeTest(name="GUIwithVirtual", file = "crowdWithVirtualsTd", time = 1000.0, taskSize = 1500, sampling = 500.0, effects = "caseStudy.json")
makeTest(name="GUIwithoutVirtual", file = "crowdWithoutVirtualsTd", time = 1000.0, taskSize = 1500, sampling = 500.0, effects = "caseStudy.json")
