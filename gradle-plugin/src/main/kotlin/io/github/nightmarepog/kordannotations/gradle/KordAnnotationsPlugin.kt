package io.github.nightmarepog.kordannotations.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

public class KordAnnotationsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("com.google.devtools.ksp")
        val extension = project.extensions.create("kordAnnotations", KordAnnotationsExtension::class.java)
        extension.translationDirectory.convention(project.layout.projectDirectory.dir("src/main/resources/translations"))

        project.dependencies.add("implementation", "$GROUP:kord-annotations-core:$VERSION")
        project.dependencies.add("ksp", "$GROUP:kord-annotations-processor:$VERSION")

        project.extensions.configure(KspExtension::class.java) { ksp ->
            ksp.arg("kordAnnotations.moduleName", extension.moduleName)
            ksp.arg("kordAnnotations.generatedPackage", extension.generatedPackage)
        }

        val outputDirectory = project.layout.buildDirectory.dir("generated/kordAnnotations/translations")
        val translationFilePath = extension.generatedPackage.zip(extension.translationObjectName) { packageName, objectName ->
            "${packageName.replace('.', '/')}/$objectName.kt"
        }
        val generateTranslations = project.tasks.register("generateKordAnnotationTranslations", GenerateTranslationsTask::class.java) { task ->
            task.translationDirectory.set(extension.translationDirectory)
            task.fallbackLocale.set(extension.fallbackLocale)
            task.generatedPackage.set(extension.generatedPackage)
            task.translationObjectName.set(extension.translationObjectName)
            task.outputFile.set(outputDirectory.zip(translationFilePath) { root, path ->
                root.file(path)
            })
        }
        project.extensions.configure(KotlinJvmProjectExtension::class.java) { kotlin ->
            kotlin.sourceSets.named("main") { sourceSet -> sourceSet.kotlin.srcDir(outputDirectory) }
        }
        project.tasks.matching { it.name == "compileKotlin" || it.name.startsWith("ksp") }
            .configureEach { it.dependsOn(generateTranslations) }
    }

    private companion object {
        const val GROUP = "io.github.nightmarepog"
        const val VERSION = "0.1.0-SNAPSHOT"
    }
}
