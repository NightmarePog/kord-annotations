package io.github.nightmarepog.kordannotations

import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.entity.interaction.GuildInteraction
import dev.kord.rest.builder.message.create.InteractionResponseCreateBuilder
import kotlin.reflect.KClass
import java.util.ServiceLoader

/** The Discord application-command shape represented by a [CommandDescriptor]. */
enum class ApplicationCommandType {
    /** A slash command with typed options. */
    CHAT_INPUT,

    /** A command shown in a user's context menu. */
    USER,

    /** A command shown in a message's context menu. */
    MESSAGE,
}

/** The Discord option type accepted before optional conversion. */
enum class OptionType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    USER,
    CHANNEL,
    ROLE,
    MENTIONABLE,
    ATTACHMENT,
}

/** A Discord context in which an application command may be installed. */
enum class InteractionContextType {
    GUILD,
    BOT_DM,
    PRIVATE_CHANNEL,
}

/** Controls who can see an interaction response. */
enum class ReplyVisibility {
    /** Everyone with access to the channel can see the response. */
    PUBLIC,

    /** Only the invoking user can see the response. */
    PRIVATE,
}

/** Selects the identity used to group cooldowns. */
enum class CooldownScope {
    USER,
    CHANNEL,
    GUILD,
    GLOBAL,
}

/** The Discord component handled by a [ComponentDescriptor]. */
enum class ComponentType {
    BUTTON,
    SELECT_MENU,
    MODAL,
}

/**
 * Generated metadata for one Discord command option.
 *
 * Null bounds are not sent to Discord. [autocompleteProvider] and [choices] should not both be set.
 *
 * @property name Discord option name.
 * @property description Literal Discord option description.
 * @property type Discord option type.
 * @property valueTypeName Qualified Kotlin handler parameter type.
 * @property required Whether Discord requires the option.
 * @property choices Allowed string values.
 * @property minimum Inclusive numeric lower bound.
 * @property maximum Inclusive numeric upper bound.
 * @property minimumLength Inclusive string minimum length.
 * @property maximumLength Inclusive string maximum length.
 * @property autocompleteProvider Provider used for autocomplete interactions.
 * @property converterProvider Converter used before handler invocation.
 */
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

/**
 * Runtime behavior applied before and during a handler invocation.
 *
 * A null cooldown, timeout, or loading response disables that feature.
 *
 * @property visibility Default visibility for plain-text responses.
 * @property contexts Discord contexts in which the command is installed.
 * @property cooldownSeconds Cooldown duration, or null when disabled.
 * @property cooldownScope Identity shared by cooldown entries.
 * @property timeoutSeconds Handler timeout, or null when disabled.
 * @property loadingResponse Delayed response content, or null when disabled.
 * @property loadingResponseDelayMillis Delay before sending [loadingResponse].
 */
data class ExecutionPolicy(
    val visibility: ReplyVisibility = ReplyVisibility.PUBLIC,
    val contexts: Set<InteractionContextType> = setOf(InteractionContextType.GUILD),
    val cooldownSeconds: Int? = null,
    val cooldownScope: CooldownScope = CooldownScope.USER,
    val timeoutSeconds: Int? = 30,
    val loadingResponse: String? = "Working…",
    val loadingResponseDelayMillis: Long = 2_000,
)

/**
 * A generated check annotation paired with its checker.
 *
 * @property annotation Annotation instance passed to the checker.
 * @property checkerType Checker resolved before command execution.
 */
data class GeneratedCheck(
    val annotation: Annotation,
    val checkerType: KClass<out AnnotationCheck<*>>,
)

/**
 * Generated metadata for one application command.
 *
 * [parentName] identifies the slash-command root when this descriptor represents a subcommand.
 *
 * @property name Command or subcommand name.
 * @property description Literal Discord command description.
 * @property type Application-command shape.
 * @property parentName Slash-command root name for a subcommand.
 * @property parentDescription Literal description of the slash-command root.
 * @property options Options accepted by the command.
 * @property execution Runtime policy for the command.
 * @property checks Checks evaluated before invoking the handler.
 */
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

/**
 * Generated metadata for a component handler.
 *
 * @property id Component custom ID.
 * @property type Component shape.
 * @property execution Runtime policy for the component.
 */
data class ComponentDescriptor(
    val id: String,
    val type: ComponentType,
    val execution: ExecutionPolicy = ExecutionPolicy(),
)

/** Typed access to the option values received for one command invocation. */
class CommandOptions internal constructor(private val values: Map<String, Any?>) {
    /** Returns the raw value for [name], or null when it was not supplied. */
    operator fun get(name: String): Any? = values[name]

    /** Returns whether an option named [name] was supplied. */
    fun contains(name: String): Boolean = values.containsKey(name)

    /**
     * Returns the required option [name] as [type].
     *
     * @throws MissingCommandOptionException if the option is absent.
     * @throws InvalidCommandOptionException if its value is not an instance of [type].
     */
    fun <T : Any> require(name: String, type: KClass<T>): T {
        val value = values[name] ?: throw MissingCommandOptionException(name)
        if (!type.isInstance(value)) throw InvalidCommandOptionException(name, type.qualifiedName.orEmpty(), value::class.qualifiedName.orEmpty())
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    /**
     * Returns option [name] as [type], or null when it was not supplied.
     *
     * @throws InvalidCommandOptionException if its value is not an instance of [type].
     */
    fun <T : Any> optional(name: String, type: KClass<T>): T? {
        val value = values[name] ?: return null
        if (!type.isInstance(value)) throw InvalidCommandOptionException(name, type.qualifiedName.orEmpty(), value::class.qualifiedName.orEmpty())
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    /** Creates [CommandOptions] snapshots. */
    companion object {
        /** Takes a snapshot of [values] for use as command options. */
        fun of(values: Map<String, Any?>): CommandOptions = CommandOptions(values.toMap())
    }
}

/**
 * Stable identity values associated with one Discord interaction.
 *
 * @property userId Invoking Discord user ID.
 * @property channelId Discord channel ID when available.
 * @property guildId Discord guild ID when available.
 * @property locale Discord locale tag.
 */
data class InteractionIdentity(
    val userId: String,
    val channelId: String? = null,
    val guildId: String? = null,
    val locale: String = "en",
)

/**
 * A plain-text interaction response.
 *
 * @property content Message content.
 * @property visibility Users allowed to see the response.
 */
data class InteractionResponse(val content: String, val visibility: ReplyVisibility = ReplyVisibility.PUBLIC)

/** Sends responses for a command or component invocation. */
interface ResponseController {
    /** Whether this interaction has already been acknowledged. */
    val hasResponded: Boolean

    /** Sends the first response or edits the framework loading response after acknowledgement. */
    suspend fun respond(response: InteractionResponse)

    /**
     * Sends a response configured through Kord's message builder.
     *
     * Controllers without a live Kord interaction may reject rich responses.
     */
    suspend fun respond(
        visibility: ReplyVisibility,
        builder: InteractionResponseCreateBuilder.() -> Unit,
    ): Unit = error("Rich Kord responses are unavailable from this response controller")
}

/**
 * Data and response operations available to a command handler.
 *
 * [kordInteraction], [user], and [member] are null when executing without a live Kord interaction.
 *
 * @property command Descriptor for the invoked command.
 * @property identity Identity associated with the interaction.
 * @property options Values supplied for command options.
 * @property kordInteraction Underlying Kord interaction when available.
 */
open class CommandContext(
    val command: CommandDescriptor,
    val identity: InteractionIdentity,
    val options: CommandOptions,
    private val responses: ResponseController,
    private val extensions: HandlerResolver? = null,
    val kordInteraction: ApplicationCommandInteraction? = null,
) {
    /** Whether the interaction has already been acknowledged. */
    val hasResponded: Boolean get() = responses.hasResponded

    /** The invoking Discord user, when a live interaction is available. */
    val user: User? get() = kordInteraction?.user

    /** The invoking guild member, when this is a live guild interaction. */
    val member: Member? get() = (kordInteraction as? GuildInteraction)?.user

    /** Sends a plain-text response using the command's configured visibility by default. */
    suspend fun respond(content: String, visibility: ReplyVisibility = command.execution.visibility) {
        responses.respond(InteractionResponse(content, visibility))
    }

    /** Sends a public response configured through Kord's message builder. */
    suspend fun respondPublic(builder: InteractionResponseCreateBuilder.() -> Unit) {
        responses.respond(ReplyVisibility.PUBLIC, builder)
    }

    /** Sends a private response configured through Kord's message builder. */
    suspend fun respondPrivate(builder: InteractionResponseCreateBuilder.() -> Unit) {
        responses.respond(ReplyVisibility.PRIVATE, builder)
    }
    /**
     * Converts the raw option [name] with [converterType] and verifies [expectedType].
     *
     * @throws MissingCommandOptionException if the option is absent.
     * @throws InvalidCommandOptionException if the converter returns a different type.
     */
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

    /** Converts option [name] when present, otherwise returns null. */
    suspend fun <T : Any> convertOptional(
        name: String,
        expectedType: KClass<T>,
        converterType: KClass<out OptionConverter<*>>,
    ): T? = if (options.contains(name)) convert(name, expectedType, converterType) else null
}

/**
 * Data and response operations available to a component handler.
 *
 * @property component Descriptor for the invoked component.
 * @property identity Identity associated with the interaction.
 * @property values Selected values for a select-menu interaction.
 */
class ComponentContext(
    val component: ComponentDescriptor,
    val identity: InteractionIdentity,
    val values: List<String>,
    private val responses: ResponseController,
) {
    /** Whether the interaction has already been acknowledged. */
    val hasResponded: Boolean get() = responses.hasResponded

    /** Sends a plain-text response using the component's configured visibility by default. */
    suspend fun respond(content: String, visibility: ReplyVisibility = component.execution.visibility) {
        responses.respond(InteractionResponse(content, visibility))
    }
}

/**
 * A command descriptor and generated invocation function.
 *
 * @property ownerType Type resolved before invocation.
 * @property descriptor Generated command metadata.
 * @property invoke Function that casts [ownerType] and calls the handler.
 */
data class GeneratedCommand(
    val ownerType: KClass<*>,
    val descriptor: CommandDescriptor,
    val invoke: suspend (owner: Any, context: CommandContext) -> Unit,
)

/**
 * A component descriptor and generated invocation function.
 *
 * @property ownerType Type resolved before invocation.
 * @property descriptor Generated component metadata.
 * @property invoke Function that casts [ownerType] and calls the handler.
 */
data class GeneratedComponent(
    val ownerType: KClass<*>,
    val descriptor: ComponentDescriptor,
    val invoke: suspend (owner: Any, context: ComponentContext) -> Unit,
)

/** A generated collection of commands and components discovered through [ServiceLoader]. */
interface CommandModule {
    /** Commands generated for this module. */
    val commands: List<GeneratedCommand>

    /** Components generated for this module. */
    val components: List<GeneratedComponent>
}

/** Discovers generated [CommandModule] services. */
object CommandModules {
    /** Loads every module visible to [classLoader]. */
    fun load(classLoader: ClassLoader = Thread.currentThread().contextClassLoader): List<CommandModule> =
        ServiceLoader.load(CommandModule::class.java, classLoader).toList()
}

/** Resolves handler owners and extension implementations by type. */
fun interface HandlerResolver {
    /** Returns an instance assignable to [type]. */
    fun resolve(type: KClass<*>): Any
}
