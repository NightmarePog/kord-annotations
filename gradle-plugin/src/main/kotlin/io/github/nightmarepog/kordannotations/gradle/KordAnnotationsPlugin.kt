package io.github.nightmarepog.kordannotations.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Configures Kotlin/JVM, KSP, and matching Kord Annotations dependencies. */
class KordAnnotationsPlugin : Plugin<Project> {
    /** Applies and configures the command-generation toolchain on [project]. */
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("com.google.devtools.ksp")
        val extension = project.extensions.create("kordAnnotations", KordAnnotationsExtension::class.java)

        project.dependencies.add("implementation", "$GROUP:kord-annotations-core:$PUBLICATION_VERSION")
        project.dependencies.add("ksp", "$GROUP:kord-annotations-processor:$PUBLICATION_VERSION")

        project.extensions.configure(KspExtension::class.java) { ksp ->
            ksp.arg("kordAnnotations.moduleName", extension.moduleName)
            ksp.arg("kordAnnotations.generatedPackage", extension.generatedPackage)
        }
    }

    private companion object {
        const val GROUP = "io.github.nightmarepog"
        val PUBLICATION_VERSION =
            checkNotNull(KordAnnotationsPlugin::class.java.getResourceAsStream("/kord-annotations-version.txt")) {
                "Missing kord-annotations-version.txt"
            }.bufferedReader().use { reader -> reader.readText().trim() }
    }
}
