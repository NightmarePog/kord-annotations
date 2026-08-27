package io.github.nightmarepog.kordannotations.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.Yaml

public abstract class GenerateTranslationsTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    public abstract val translationDirectory: DirectoryProperty

    @get:Input
    public abstract val fallbackLocale: Property<String>

    @get:Input
    public abstract val generatedPackage: Property<String>

    @get:Input
    public abstract val translationObjectName: Property<String>

    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun generate() {
        val directory = translationDirectory.asFile.get()
        val localeFiles = directory.listFiles { file -> file.extension == "yml" || file.extension == "yaml" }
            ?.sortedBy { it.name }
            .orEmpty()
        if (localeFiles.isEmpty()) {
            outputFile.asFile.get().apply { parentFile.mkdirs(); writeText(emptySource()) }
            return
        }
        val translations = localeFiles.associate { file -> file.nameWithoutExtension to flatten(Yaml().load(file.inputStream())) }
        val fallback = translations[fallbackLocale.get()]
            ?: throw GradleException("Missing fallback translation ${fallbackLocale.get()}.yml in $directory")
        translations.forEach { (locale, values) ->
            val missing = fallback.keys - values.keys
            if (missing.isNotEmpty()) throw GradleException("Translation $locale is missing keys: ${missing.sorted().joinToString()}")
        }
        outputFile.asFile.get().apply {
            parentFile.mkdirs()
            writeText(source(fallback.keys.sorted()))
        }
    }

    private fun source(keys: List<String>): String = buildString {
        appendLine("package ${generatedPackage.get()}")
        appendLine()
        appendLine("public object ${translationObjectName.get()} {")
        keys.forEach { key ->
            appendLine("    public const val ${key.constantName()}: String = ${key.literal()}")
        }
        appendLine("}")
    }

    private fun emptySource(): String = "package ${generatedPackage.get()}\n\npublic object ${translationObjectName.get()}\n"

    private fun flatten(root: Any?): Map<String, String> {
        val result = linkedMapOf<String, String>()
        fun visit(prefix: String, value: Any?) {
            when (value) {
                is Map<*, *> -> value.forEach { (name, child) -> visit(if (prefix.isEmpty()) name.toString() else "$prefix.$name", child) }
                null -> Unit
                else -> result[prefix] = value.toString()
            }
        }
        visit("", root)
        return result
    }

    private fun String.constantName(): String = split(Regex("[^A-Za-z0-9]+")).filter(String::isNotEmpty)
        .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
        .let { if (it.firstOrNull()?.isDigit() == true) "Key$it" else it }

    private fun String.literal(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
