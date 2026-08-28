package io.github.nightmarepog.kordannotations.spring

import dev.kord.core.Kord
import io.github.nightmarepog.kordannotations.CommandModule
import io.github.nightmarepog.kordannotations.CommandModules
import io.github.nightmarepog.kordannotations.HandlerResolver
import io.github.nightmarepog.kordannotations.KordAnnotations
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/** Discovers generated modules and connects Kord Annotations to a Spring-managed [Kord] client. */
@AutoConfiguration
@EnableConfigurationProperties(KordAnnotationsProperties::class)
@Import(KordAnnotationsHandlerRegistrar::class)
class KordAnnotationsAutoConfiguration {
    /** Loads generated modules when the application does not provide its own list. */
    @Bean
    @ConditionalOnMissingBean
    fun generatedCommandModules(): List<CommandModule> = CommandModules.load()

    /** Resolves handlers from Spring when no other resolver is configured. */
    @Bean
    @ConditionalOnMissingBean
    fun handlerResolver(applicationContext: ApplicationContext): HandlerResolver =
        SpringHandlerResolver(applicationContext)

    /** Creates the runtime from discovered modules and the configured resolver. */
    @Bean
    @ConditionalOnMissingBean
    fun kordAnnotations(
        modules: List<CommandModule>,
        handlerResolver: HandlerResolver,
    ): KordAnnotations = KordAnnotations(modules, handlerResolver)

    /** Installs listeners and optionally synchronizes commands after singleton creation. */
    @Bean
    @ConditionalOnBean(Kord::class)
    fun kordAnnotationsInstaller(
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
