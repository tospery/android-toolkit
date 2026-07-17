import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    // 在根工程统一加载插件，子模块应用时不再重复加载 Kotlin 插件。
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

/**
 * 根据当前 Gradle 模块路径生成稳定的模块元数据。
 */
abstract class GenerateModuleMetadataTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val moduleMetadataPackage: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputFile = outputDirectory.get()
            .file("${moduleMetadataPackage.get().replace('.', '/')}/ModuleMetadata.kt")
            .asFile

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            |package ${moduleMetadataPackage.get()}
            |
            |/**
            | * 由 Gradle 根据当前模块路径生成，禁止手动修改。
            | */
            |internal object ModuleMetadata {
            |    const val path: String = "${modulePath.get()}"
            |}
            |
            """.trimMargin(),
        )
    }
}

/**
 * 为所有 Toolkit 子模块注册模块元数据生成任务。
 */
subprojects {
    val moduleProjectPath = path
    val moduleIdentifier = moduleProjectPath
        .trim(':')
        .replace(Regex("[^A-Za-z0-9_]"), "_")
        .ifBlank { "root" }
    val moduleMetadataPackageName = "com.tospery.buildmetadata.module_$moduleIdentifier"
    val generatedModuleMetadataDirectory = layout.buildDirectory.dir(
        "generated/source/module-metadata/kotlin",
    )

    val generateModuleMetadata = tasks.register<GenerateModuleMetadataTask>(
        "generateModuleMetadata",
    ) {
        modulePath.set(moduleProjectPath)
        moduleMetadataPackage.set(moduleMetadataPackageName)
        outputDirectory.set(generatedModuleMetadataDirectory)
    }

    // 将生成目录接入纯 JVM 模块的主源码集。
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            sourceSets.named("main") {
                java.srcDir(generateModuleMetadata.flatMap { it.outputDirectory })
            }
        }
    }

    // 将生成目录接入 Android Library 的 Kotlin 主源码集。
    plugins.withId("com.android.library") {
        extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants(selector().all()) { variant ->
                variant.sources.kotlin?.addGeneratedSourceDirectory(
                    generateModuleMetadata,
                    GenerateModuleMetadataTask::outputDirectory,
                )
            }
        }
    }

    // 确保所有 Kotlin 编译任务开始前已经生成模块元数据。
    tasks.configureEach {
        if (name.startsWith("compile") && name.endsWith("Kotlin")) {
            dependsOn(generateModuleMetadata)
        }
    }
}

/**
 * 单个 Toolkit 模块的 Maven 发布元数据。
 */
data class MavenModuleMetadata(
    val artifactId: String,
    val displayName: String,
    val description: String,
)

val toolkitGroupId = "com.tospery"
val toolkitVersion = "0.0.1"
val toolkitRepositoryUrl =
    "https://github.com/tospery/tospery-android-toolkit"

/**
 * Maven 发布模块白名单。
 *
 * Gradle 模块路径与 Maven artifactId 在此处集中映射。
 */
val toolkitPublicationModules = mapOf(
    ":base" to MavenModuleMetadata(
        artifactId = "base",
        displayName = "Tospery Base",
        description =
            "Pure Kotlin reusable base abstractions and common models.",
    ),
    ":core" to MavenModuleMetadata(
        artifactId = "core",
        displayName = "Tospery Core",
        description =
            "Reusable Android and Compose core utilities.",
    ),
    ":nav" to MavenModuleMetadata(
        artifactId = "nav",
        displayName = "Tospery Navigation",
        description =
            "Platform-independent URL and URI navigation abstractions.",
    ),
    ":net" to MavenModuleMetadata(
        artifactId = "net",
        displayName = "Tospery Network",
        description =
            "Platform-independent networking abstractions and models.",
    ),
    ":net:retrofit" to MavenModuleMetadata(
        artifactId = "net-retrofit",
        displayName = "Tospery Network Retrofit",
        description =
            "Retrofit, OkHttp and Moshi implementation for Tospery Network.",
    ),
    ":suite" to MavenModuleMetadata(
        artifactId = "suite",
        displayName = "Tospery Suite",
        description =
            "Reusable Android app utilities and Compose components.",
    ),
    ":github-model-core" to MavenModuleMetadata(
        artifactId = "github-model-core",
        displayName = "Tospery GitHub Model Core",
        description =
            "Reusable core models for GitHub integrations.",
    ),
    ":github-trending" to MavenModuleMetadata(
        artifactId = "github-trending",
        displayName = "Tospery GitHub Trending",
        description =
            "Kotlin API and HTML parser for GitHub Trending.",
    ),
)

/**
 * 只为发布白名单中的模块配置 Maven 坐标和 POM。
 */
subprojects {
    val publicationMetadata =
        toolkitPublicationModules[path] ?: return@subprojects

    group = toolkitGroupId
    version = toolkitVersion

    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<MavenPublishBaseExtension> {
        coordinates(
            groupId = toolkitGroupId,
            artifactId = publicationMetadata.artifactId,
            version = toolkitVersion,
        )

        pom {
            name.set(publicationMetadata.displayName)
            description.set(publicationMetadata.description)
            inceptionYear.set("2026")
            url.set(toolkitRepositoryUrl)

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set(
                        "https://www.apache.org/licenses/LICENSE-2.0.txt",
                    )
                    distribution.set("repo")
                }
            }

            developers {
                developer {
                    id.set("tospery")
                    name.set("Tospery")
                    url.set("https://github.com/tospery")
                }
            }

            scm {
                url.set(toolkitRepositoryUrl)
                connection.set(
                    "scm:git:git://github.com/tospery/" +
                        "tospery-android-toolkit.git",
                )
                developerConnection.set(
                    "scm:git:ssh://git@github.com/tospery/" +
                        "tospery-android-toolkit.git",
                )
            }
        }
    }
}
