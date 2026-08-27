package io.github.nightmarepog.kordannotations

import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.entity.interaction.GuildInteraction
import dev.kord.rest.builder.message.create.InteractionResponseCreateBuilder
import kotlin.reflect.KClass
import java.util.ServiceLoader

enum class ApplicationCommandType { CHAT_INPUT, USER, MESSAGE }
enum class OptionType { STRING, INTEGER, NUMBER, BOOLEAN, USER, CHANNEL, ROLE, MENTIONABLE, ATTACHMENT }
enum class InteractionContextType { GUILD, BOT_DM, PRIVATE_CHANNEL }
enum class ReplyVisibility { PUBLIC, PRIVATE }
enum class CooldownScope { USER, CHANNEL, GUILD, GLOBAL }
enum class ComponentType { BUTTON, SELECT_MENU, MODAL }

data class OptionDescriptor(
    val name: String,
    val description: String,
    val type: OptionType,
    val valueTypeName: String,
    val required: Boolean,
    val choices: List<String> = emptyList(),
    val minimum: Long? = null,
    val maximum: Long? = null,
    val minimumLength: Int? = null,
    val maximumLength: Int? = null,
    val autocompleteProvider: KClass<out AutocompleteProvider>? = null,
    val converterProvider: KClass<out OptionConverter<*>>? = null,
)

data class ExecutionPolicy(
    val visibility: ReplyVisibility = ReplyVisibility.PUBLIC,
    val contexts: Set<InteractionContextType> = setOf(InteractionContextType.GUILD),
    val cooldownSeconds: Int? = null,
    val cooldownScope: CooldownScope = CooldownScope.USER,
    val timeoutSeconds: Int? = 30,
    val loadingResponse: String? = "Working…",
    val loadingResponseDelayMillis: Long = 2_000,
)

data class GeneratedCheck(
    val annotation: Annotation,
    val checkerType: KClass<out AnnotationCheck<*>>,
)

data class CommandDescriptor(
    val name: String,
    val description: String,
    val type: ApplicationCommandType = ApplicationCommandType.CHAT_INPUT,
    val parentName: String? = null,
    val parentDescription: String? = null,
    val options: List<OptionDescriptor> = emptyList(),
    val execution: ExecutionPolicy = ExecutionPolicy(),
    val checks: List<GeneratedCheck> = emptyList(),
)

data class ComponentDescriptor(
    val id: String,
    val type: ComponentType,
    val execution: ExecutionPolicy = ExecutionPolicy(),
)

class CommandOptions internal constructor(private val values: Map<String, Any?>) {
    operator fun get(name: String): Any? = values[name]
    fun contains(name: String): Boolean = values.containsKey(name)

    fun <T : Any> require(name: String, type: KClass<T>): T {
        val value = values[name] ?: throw MissingCommandOptionException(name)
        if (!type.isInstance(value)) throw InvalidCommandOptionException(name, type.qualifiedName.orEmpty(), value::class.qualifiedName.orEmpty())
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    fun <T : Any> optional(name: String, type: KClass<T>): T? {
        val value = values[name] ?: return null
        if (!type.isInstance(value)) throw InvalidCommandOptionException(name, type.qualifiedName.orEmpty(), value::class.qualifiedName.orEmpty())
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    companion object {
        fun of(values: Map<String, Any?>): CommandOptions = CommandOptions(values.toMap())
    }
}

data class InteractionIdentity(
    val userId: String,
    val channelId: String? = null,
    val guildId: String? = null,
    val locale: String = "en",
)

data class InteractionResponse(val content: String, val visibility: ReplyVisibility = ReplyVisibility.PUBLIC)

interface ResponseController {
    val hasResponded: Boolean
    /** Sends the first response or edits the framework loading response after acknowledgement. */
    suspend fun respond(response: InteractionResponse)

    suspend fun respond(
        visibility: ReplyVisibility,
        builder: InteractionResponseCreateBuilder.() -> Unit,
    ): Unit = error("Rich Kord responses are unavailable from this response controller")
}

open class CommandContext(
    val command: CommandDescriptor,
    val identity: InteractionIdentity,
    val options: CommandOptions,
    private val responses: ResponseController,
    private val extensions: HandlerResolver? = null,
    val kordInteraction: ApplicationCommandInteraction? = null,
) {
    val hasResponded: Boolean get() = responses.hasResponded
    val user: User? get() = kordInteraction?.user
    val member: Member? get() = (kordInteraction as? GuildInteraction)?.user
    suspend fun respond(content: String, visibility: ReplyVisibility = command.execution.visibility) {
        responses.respond(InteractionResponse(content, visibility))
    }

    suspend fun respondPublic(builder: InteractionResponseCreateBuilder.() -> Unit) {
        responses.respond(ReplyVisibility.PUBLIC, builder)
    }

    suspend fun respondPrivate(builder: InteractionResponseCreateBuilder.() -> Unit) {
        responses.respond(ReplyVisibility.PRIVATE, builder)
    }
    suspend fun <T : Any> convert(
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

    suspend fun <T : Any> convertOptional(
        name: String,
        expectedType: KClass<T>,
        converterType: KClass<out OptionConverter<*>>,
    ): T? = if (options.contains(name)) convert(name, expectedType, converterType) else null
}

class ComponentContext(
    val component: ComponentDescriptor,
    val identity: InteractionIdentity,
    val values: List<String>,
    private val responses: ResponseController,
) {
    val hasResponded: Boolean get() = responses.hasResponded
    suspend fun respond(content: String, visibility: ReplyVisibility = component.execution.visibility) {
        responses.respond(InteractionResponse(content, visibility))
    }
}

data class GeneratedCommand(
    val ownerType: KClass<*>,
    val descriptor: CommandDescriptor,
    val invoke: suspend (owner: Any, context: CommandContext) -> Unit,
)

data class GeneratedComponent(
    val ownerType: KClass<*>,
    val descriptor: ComponentDescriptor,
    val invoke: suspend (owner: Any, context: ComponentContext) -> Unit,
)

interface CommandModule {
    val commands: List<GeneratedCommand>
    val components: List<GeneratedComponent>
}

object CommandModules {
    fun load(classLoader: ClassLoader = Thread.currentThread().contextClassLoader): List<CommandModule> =
        ServiceLoader.load(CommandModule::class.java, classLoader).toList()
}

fun interface HandlerResolver { fun resolve(type: KClass<*>): Any }
