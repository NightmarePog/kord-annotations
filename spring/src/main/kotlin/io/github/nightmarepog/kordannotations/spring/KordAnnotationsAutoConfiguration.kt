package io.github.nightmarepog.kordannotations.spring

import dev.kord.core.Kord
import io.github.nightmarepog.kordannotations.CommandModule
import io.github.nightmarepog.kordannotations.CommandModules
import io.github.nightmarepog.kordannotations.DefaultTranslations
import io.github.nightmarepog.kordannotations.HandlerResolver
import io.github.nightmarepog.kordannotations.KordAnnotations
import io.github.nightmarepog.kordannotations.TranslationProvider
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

@AutoConfiguration
@EnableConfigurationProperties(KordAnnotationsProperties::class)
@Import(KordAnnotationsHandlerRegistrar::class)
public class KordAnnotationsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public fun generatedCommandModules(): List<CommandModule> = CommandModules.load()

    @Bean
    @ConditionalOnMissingBean
    public fun translationProvider(): TranslationProvider = DefaultTranslations.load()

    @Bean
    @ConditionalOnMissingBean
    public fun handlerResolver(applicationContext: ApplicationContext): HandlerResolver =
        SpringHandlerResolver(applicationContext)

    @Bean
    @ConditionalOnMissingBean
    public fun kordAnnotations(
        modules: List<CommandModule>,
        handlerResolver: HandlerResolver,
        translationProvider: TranslationProvider,
    ): KordAnnotations = KordAnnotations(modules, handlerResolver, translationProvider)

    @Bean
    @ConditionalOnBean(Kord::class)
    public fun kordAnnotationsInstaller(
        kord: Kord,
        kordAnnotations: KordAnnotations,
        properties: KordAnnotationsProperties,
    ): SmartInitializingSingleton = SmartInitializingSingleton {
        if (!properties.enabled) return@SmartInitializingSingleton
        kordAnnotations.install(kord)
        if (properties.syncGlobalCommands) {
            runBlocking { kordAnnotations.syncGlobalCommands(kord, properties.maximumSyncAttempts) }
        }
    }
}
