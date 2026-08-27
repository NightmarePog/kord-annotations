package io.github.nightmarepog.kordannotations

import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.entity.interaction.GuildInteraction
import dev.kord.rest.builder.message.create.InteractionResponseCreateBuilder
import kotlin.reflect.KClass
import java.util.ServiceLoader

public enum class ApplicationCommandType { CHAT_INPUT, USER, MESSAGE }
public enum class OptionType { STRING, INTEGER, NUMBER, BOOLEAN, USER, CHANNEL, ROLE, MENTIONABLE, ATTACHMENT }
public enum class InteractionContextType { GUILD, BOT_DM, PRIVATE_CHANNEL }
public enum class ReplyVisibility { PUBLIC, PRIVATE }
public enum class CooldownScope { USER, CHANNEL, GUILD, GLOBAL }
public enum class ComponentType { BUTTON, SELECT_MENU, MODAL }

public data class OptionDescriptor(
    public val name: String,
    public val description: String,
    public val type: OptionType,
    public val valueTypeName: String,
    public val required: Boolean,
    public val choices: List<String> = emptyList(),
    public val minimum: Long? = null,
    public val maximum: Long? = null,
    public val minimumLength: Int? = null,
    public val maximumLength: Int? = null,
    public val autocompleteProvider: KClass<out AutocompleteProvider>? = null,
    public val converterProvider: KClass<out OptionConverter<*>>? = null,
)

public data class ExecutionPolicy(
    public val visibility: ReplyVisibility = ReplyVisibility.PUBLIC,
    public val contexts: Set<InteractionContextType> = setOf(InteractionContextType.GUILD),
    public val cooldownSeconds: Int? = null,
    public val cooldownScope: CooldownScope = CooldownScope.USER,
    public val timeoutSeconds: Int? = 30,
    public val loadingResponseKey: String? = "kordAnnotations.loading",
    public val loadingResponseDelayMillis: Long = 2_000,
    public val observed: Boolean = false,
)

public data class GeneratedCheck(
    public val annotation: Annotation,
    public val checkerType: KClass<out AnnotationCheck<*>>,
)

public data class CommandDescriptor(
    public val name: String,
    public val description: String,
    public val type: ApplicationCommandType = ApplicationCommandType.CHAT_INPUT,
    public val parentName: String? = null,
    public val parentDescription: String? = null,
    public val options: List<OptionDescriptor> = emptyList(),
    public val execution: ExecutionPolicy = ExecutionPolicy(),
    public val checks: List<GeneratedCheck> = emptyList(),
)

public data class ComponentDescriptor(
    public val id: String,
    public val type: ComponentType,
    public val execution: ExecutionPolicy = ExecutionPolicy(),
)

public class CommandOptions internal constructor(private val values: Map<String, Any?>) {
    public operator fun get(name: String): Any? = values[name]
    public fun contains(name: String): Boolean = values.containsKey(name)

    public fun <T : Any> require(name: String, type: KClass<T>): T {
        val value = values[name] ?: throw MissingCommandOptionException(name)
        if (!type.isInstance(value)) throw InvalidCommandOptionException(name, type.qualifiedName.orEmpty(), value::class.qualifiedName.orEmpty())
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    public fun <T : Any> optional(name: String, type: KClass<T>): T? {
        val value = values[name] ?: return null
        if (!type.isInstance(value)) throw InvalidCommandOptionException(name, type.qualifiedName.orEmpty(), value::class.qualifiedName.orEmpty())
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    public companion object {
        public fun of(values: Map<String, Any?>): CommandOptions = CommandOptions(values.toMap())
    }
}

public data class InteractionIdentity(
    public val userId: String,
    public val channelId: String? = null,
    public val guildId: String? = null,
    public val locale: String = "en",
)

public data class InteractionResponse(public val content: String, public val visibility: ReplyVisibility = ReplyVisibility.PUBLIC)

public interface ResponseController {
    public val hasResponded: Boolean
    /** Sends the first response or edits the framework loading response after acknowledgement. */
    public suspend fun respond(response: InteractionResponse)

    public suspend fun respond(
        visibility: ReplyVisibility,
        builder: InteractionResponseCreateBuilder.() -> Unit,
    ): Unit = error("Rich Kord responses are unavailable from this response controller")
}

public open class CommandContext(
    public val command: CommandDescriptor,
    public val identity: InteractionIdentity,
    public val options: CommandOptions,
    private val responses: ResponseController,
    public val translations: TranslationProvider,
    private val extensions: HandlerResolver? = null,
    public val kordInteraction: ApplicationCommandInteraction? = null,
) {
    public val hasResponded: Boolean get() = responses.hasResponded
    public val user: User? get() = kordInteraction?.user
    public val member: Member? get() = (kordInteraction as? GuildInteraction)?.user
    public suspend fun respond(content: String, visibility: ReplyVisibility = command.execution.visibility) {
        responses.respond(InteractionResponse(content, visibility))
    }

    public suspend fun respondPublic(builder: InteractionResponseCreateBuilder.() -> Unit) {
        responses.respond(ReplyVisibility.PUBLIC, builder)
    }

    public suspend fun respondPrivate(builder: InteractionResponseCreateBuilder.() -> Unit) {
        responses.respond(ReplyVisibility.PRIVATE, builder)
    }
    public fun translate(key: String, arguments: Map<String, Any?> = emptyMap()): String =
        translations.translate(identity.locale, key, arguments)

    public suspend fun <T : Any> convert(
        name: String,
        expectedType: KClass<T>,
        converterType: KClass<out OptionConverter<*>>,
    ): T {
        val raw = options[name] ?: throw MissingCommandOptionException(name)
        val converterResolver = extensions ?: error("No extension resolver is available")
        @Suppress("UNCHECKED_CAST")
        val converted = (converterResolver.resolve(converterType) as OptionConverter<Any>).convert(raw, this)
        if (!expectedType.isInstance(converted)) {
            throw InvalidCommandOptionException(name, expectedType.qualifiedName.orEmpty(), converted::class.qualifiedName.orEmpty())
        }
        @Suppress("UNCHECKED_CAST")
        return converted as T
    }

    public suspend fun <T : Any> convertOptional(
        name: String,
        expectedType: KClass<T>,
        converterType: KClass<out OptionConverter<*>>,
    ): T? = if (options.contains(name)) convert(name, expectedType, converterType) else null
}

public class ComponentContext(
    public val component: ComponentDescriptor,
    public val identity: InteractionIdentity,
    public val values: List<String>,
    private val responses: ResponseController,
    public val translations: TranslationProvider,
) {
    public val hasResponded: Boolean get() = responses.hasResponded
    public suspend fun respond(content: String, visibility: ReplyVisibility = component.execution.visibility) {
        responses.respond(InteractionResponse(content, visibility))
    }
    public fun translate(key: String, arguments: Map<String, Any?> = emptyMap()): String =
        translations.translate(identity.locale, key, arguments)
}

public data class GeneratedCommand(
    public val ownerType: KClass<*>,
    public val descriptor: CommandDescriptor,
    public val invoke: suspend (owner: Any, context: CommandContext) -> Unit,
)

public data class GeneratedComponent(
    public val ownerType: KClass<*>,
    public val descriptor: ComponentDescriptor,
    public val invoke: suspend (owner: Any, context: ComponentContext) -> Unit,
)

public interface CommandModule {
    public val commands: List<GeneratedCommand>
    public val components: List<GeneratedComponent>
}

public object CommandModules {
    public fun load(classLoader: ClassLoader = Thread.currentThread().contextClassLoader): List<CommandModule> =
        ServiceLoader.load(CommandModule::class.java, classLoader).toList()
}

public fun interface HandlerResolver { public fun resolve(type: KClass<*>): Any }
