package io.github.nightmarepog.kordannotations

import com.ibm.icu.text.MessageFormat
import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.util.Locale

public fun interface TranslationProvider {
    public fun translate(locale: String, key: String, arguments: Map<String, Any?>): String
}

public class MapTranslationProvider(
    translations: Map<String, Map<String, String>>,
    private val fallbackLocale: String = "en",
) : TranslationProvider {
    private val translations: Map<String, Map<String, String>> = translations.mapValues { it.value.toMap() }

    override fun translate(locale: String, key: String, arguments: Map<String, Any?>): String {
        val normalizedLocale = locale.replace('-', '_')
        val language = normalizedLocale.substringBefore('_')
        val localized = translations[normalizedLocale]?.get(key)
            ?: translations[language]?.get(key)
            ?: translations[fallbackLocale]?.get(key)
            ?: return key
        return MessageFormat(localized, Locale.forLanguageTag(locale.replace('_', '-'))).format(arguments)
    }
}

public object YamlTranslations {
    public fun load(streams: Map<String, InputStream>, fallbackLocale: String = "en"): TranslationProvider {
        val yaml = Yaml()
        val translations = streams.mapValues { (_, stream) -> flatten(yaml.load<Any?>(stream)) }
        return MapTranslationProvider(translations, fallbackLocale)
    }

    public fun loadAll(streams: Map<String, List<InputStream>>, fallbackLocale: String = "en"): TranslationProvider {
        val yaml = Yaml()
        val translations = streams.mapValues { (_, localeStreams) ->
            localeStreams.fold(linkedMapOf<String, String>()) { merged, stream ->
                merged.apply { putAll(flatten(yaml.load<Any?>(stream))) }
            }
        }
        return MapTranslationProvider(translations, fallbackLocale)
    }

    private fun flatten(root: Any?): Map<String, String> {
        val result = linkedMapOf<String, String>()
        fun visit(prefix: String, value: Any?) {
            when (value) {
                is Map<*, *> -> value.forEach { (childKey, childValue) ->
                    val key = if (prefix.isEmpty()) childKey.toString() else "$prefix.$childKey"
                    visit(key, childValue)
                }
                null -> Unit
                else -> result[prefix] = value.toString()
            }
        }
        visit("", root)
        return result
    }
}

public object DefaultTranslations {
    public fun load(classLoader: ClassLoader = Thread.currentThread().contextClassLoader): TranslationProvider {
        val streams = listOfNotNull(
            classLoader.getResourceAsStream("kord-annotations/en.yml"),
            classLoader.getResourceAsStream("translations/en.yml"),
        )
        return YamlTranslations.loadAll(mapOf("en" to streams))
    }
}

public object EmptyTranslations : TranslationProvider {
    override fun translate(locale: String, key: String, arguments: Map<String, Any?>): String = key
}
