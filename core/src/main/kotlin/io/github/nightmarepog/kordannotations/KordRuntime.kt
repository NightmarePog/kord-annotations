package io.github.nightmarepog.kordannotations

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.behavior.interaction.response.MessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ActionInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.core.entity.interaction.RootCommand
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.core.event.interaction.ApplicationCommandInteractionCreateEvent
import dev.kord.core.event.interaction.AutoCompleteInteractionCreateEvent
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.core.event.interaction.SelectMenuInteractionCreateEvent
import dev.kord.core.on
import dev.kord.core.entity.interaction.InteractionCommand
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.attachment
import dev.kord.rest.builder.interaction.boolean
import dev.kord.rest.builder.interaction.channel
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.mentionable
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.role
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlin.math.pow
import kotlin.reflect.KClass

public class InstanceHandlerResolver(instances: Iterable<Any>) : HandlerResolver {
    private val instances = instances.associateBy { it::class }

    public constructor(vararg instances: Any) : this(instances.asIterable())

    override fun resolve(type: KClass<*>): Any = instances[type]
        ?: error("No handler instance was registered for ${type.qualifiedName}")
}

public class KordAnnotations(
    modules: Iterable<CommandModule>,
    handlerResolver: HandlerResolver,
    private val translations: TranslationProvider = EmptyTranslations,
    cooldownStore: CooldownStore = InMemoryCooldownStore(),
    observers: List<CommandObserver> = emptyList(),
) {
    private val commands = modules.flatMap { it.commands }
    private val components = modules.flatMap { it.components }
    private val resolver = handlerResolver
    private val executor = CommandExecutor(handlerResolver, cooldownStore, observers)
    private val componentExecutor = ComponentExecutor(handlerResolver, cooldownStore)

    init {
        require(commands.distinctBy { it.descriptor.parentName to it.descriptor.name }.size == commands.size) {
            "Generated command names must be unique"
        }
        require(components.distinctBy { it.descriptor.id }.size == components.size) {
            "Generated component IDs must be unique"
        }
    }

    /** Installs listeners. Call this before [Kord.login]. */
    public fun install(kord: Kord): List<Job> = listOf(
        kord.on<ApplicationCommandInteractionCreateEvent> {
            val kordInteraction = interaction
            val generated = findCommand(kordInteraction) ?: return@on
            val options = if (kordInteraction is ChatInputCommandInteraction) commandOptions(kordInteraction.command, generated.descriptor) else CommandOptions.of(emptyMap())
            val context = CommandContext(
                generated.descriptor,
                identity(kordInteraction, kordInteraction.invokedCommandGuildId?.toString()),
                options,
                KordResponseController(kordInteraction),
                translations,
                resolver,
                kordInteraction,
            )
            executor.execute(generated, context)
        },
        kord.on<ButtonInteractionCreateEvent> {
            executeComponent(interaction.componentId, ComponentType.BUTTON, emptyList(), interaction)
        },
        kord.on<SelectMenuInteractionCreateEvent> {
            executeComponent(interaction.componentId, ComponentType.SELECT_MENU, interaction.values, interaction)
        },
        kord.on<ModalSubmitInteractionCreateEvent> {
            executeComponent(interaction.modalId, ComponentType.MODAL, emptyList(), interaction)
        },
        kord.on<AutoCompleteInteractionCreateEvent> {
            val generated = findChatCommand(interaction.command) ?: return@on
            val focused = interaction.command.options.entries.firstOrNull { it.value.focused } ?: return@on
            val option = generated.descriptor.options.firstOrNull { it.name == focused.key } ?: return@on
            val providerType = option.autocompleteProvider ?: return@on
            val context = CommandContext(
                generated.descriptor,
                identity(interaction),
                commandOptions(interaction.command, generated.descriptor),
                UnavailableResponseController,
                translations,
                resolver,
            )
            val provider = resolver.resolve(providerType) as AutocompleteProvider
            val choices = provider.complete(focused.value.value.toString(), context).take(25)
            interaction.suggestString { choices.forEach { choice(it.name, it.value) } }
        },
    )

    /** Replaces the complete global application-command set, which also removes stale commands. */
    public suspend fun syncGlobalCommands(kord: Kord, maximumAttempts: Int = 3) {
        require(maximumAttempts > 0)
        var lastFailure: Throwable? = null
        repeat(maximumAttempts) { attempt ->
            try {
                kord.createGlobalApplicationCommands {
                    this@KordAnnotations.commands.filter { it.descriptor.type == ApplicationCommandType.USER }.forEach {
                        user(it.descriptor.name) { dmPermission = InteractionContextType.BOT_DM in it.descriptor.execution.contexts }
                    }
                    this@KordAnnotations.commands.filter { it.descriptor.type == ApplicationCommandType.MESSAGE }.forEach {
                        message(it.descriptor.name) { dmPermission = InteractionContextType.BOT_DM in it.descriptor.execution.contexts }
                    }

                    val chatCommands = this@KordAnnotations.commands.filter { it.descriptor.type == ApplicationCommandType.CHAT_INPUT }
                    chatCommands.filter { it.descriptor.parentName == null }.forEach { command ->
                        input(command.descriptor.name, command.descriptor.description) {
                            dmPermission = InteractionContextType.BOT_DM in command.descriptor.execution.contexts
                            command.descriptor.options.forEach(::addOption)
                        }
                    }
                    chatCommands.filter { it.descriptor.parentName != null }
                        .groupBy { it.descriptor.parentName!! }
                        .forEach { (rootName, children) ->
                            val description = children.first().descriptor.parentDescription ?: "Commands for $rootName"
                            input(rootName, description) {
                                dmPermission = children.any { InteractionContextType.BOT_DM in it.descriptor.execution.contexts }
                                children.forEach { child ->
                                    subCommand(child.descriptor.name, child.descriptor.description) {
                                        child.descriptor.options.forEach(::addOption)
                                    }
                                }
                            }
                        }
                }.toList()
                return
            } catch (failure: Throwable) {
                lastFailure = failure
                if (attempt + 1 < maximumAttempts) delay((2.0.pow(attempt) * 1_000).toLong())
            }
        }
        throw checkNotNull(lastFailure)
    }

    private fun findCommand(interaction: dev.kord.core.entity.interaction.ApplicationCommandInteraction): GeneratedCommand? {
        val type = when (interaction) {
            is ChatInputCommandInteraction -> ApplicationCommandType.CHAT_INPUT
            is dev.kord.core.entity.interaction.UserCommandInteraction -> ApplicationCommandType.USER
            is dev.kord.core.entity.interaction.MessageCommandInteraction -> ApplicationCommandType.MESSAGE
        }
        if (interaction !is ChatInputCommandInteraction) {
            return commands.firstOrNull { it.descriptor.type == type && it.descriptor.name == interaction.invokedCommandName }
        }
        return findChatCommand(interaction.command)
    }

    private fun findChatCommand(command: InteractionCommand): GeneratedCommand? {
        val rootName = command.rootName
        val leafName = when (command) {
            is RootCommand -> rootName
            is SubCommand -> command.name
            is GroupCommand -> command.name
        }
        return commands.firstOrNull {
            it.descriptor.type == ApplicationCommandType.CHAT_INPUT && it.descriptor.name == leafName &&
                (it.descriptor.parentName == null || it.descriptor.parentName == rootName)
        }
    }

    private fun commandOptions(command: InteractionCommand, descriptor: CommandDescriptor): CommandOptions {
        return CommandOptions.of(descriptor.options.mapNotNull { option ->
            val raw: Any? = when (option.type) {
                OptionType.STRING -> command.strings[option.name]
                OptionType.INTEGER -> command.integers[option.name]?.let { if (option.valueTypeName == "kotlin.Int") it.toInt() else it }
                OptionType.NUMBER -> command.numbers[option.name]?.let { if (option.valueTypeName == "kotlin.Float") it.toFloat() else it }
                OptionType.BOOLEAN -> command.booleans[option.name]
                OptionType.USER -> if (option.valueTypeName.endsWith("Member")) command.members[option.name] else command.users[option.name]
                OptionType.CHANNEL -> command.channels[option.name]
                OptionType.ROLE -> command.roles[option.name]
                OptionType.MENTIONABLE -> command.mentionables[option.name]
                OptionType.ATTACHMENT -> command.attachments[option.name]
            }
            raw?.let { option.name to it }
        }.toMap())
    }

    private suspend fun executeComponent(id: String, type: ComponentType, values: List<String>, interaction: ActionInteraction) {
        val generated = components.firstOrNull {
            it.descriptor.type == type && (id == it.descriptor.id || id.startsWith("${it.descriptor.id}:"))
        } ?: return
        val context = ComponentContext(
            generated.descriptor,
            identity(interaction),
            values,
            KordResponseController(interaction),
            translations,
        )
        componentExecutor.execute(generated, context)
    }

    private fun identity(interaction: dev.kord.core.entity.interaction.Interaction, guildId: String? = null) = InteractionIdentity(
        userId = interaction.user.id.toString(),
        channelId = interaction.channelId.toString(),
        guildId = guildId,
        locale = interaction.locale.toString(),
    )

    private class KordResponseController(private val interaction: ActionInteraction) : ResponseController {
        private var response: MessageInteractionResponseBehavior? = null
        override val hasResponded: Boolean get() = response != null

        override suspend fun respond(response: InteractionResponse) {
            val existing = this.response
            if (existing != null) {
                existing.edit { content = response.content }
                return
            }
            this.response = when (response.visibility) {
                ReplyVisibility.PUBLIC -> interaction.respondPublic { content = response.content }
                ReplyVisibility.PRIVATE -> interaction.respondEphemeral { content = response.content }
            }
        }

        override suspend fun respond(
            visibility: ReplyVisibility,
            builder: dev.kord.rest.builder.message.create.InteractionResponseCreateBuilder.() -> Unit,
        ) {
            check(this.response == null) { "A rich response cannot replace an existing loading response" }
            this.response = when (visibility) {
                ReplyVisibility.PUBLIC -> interaction.respondPublic(builder)
                ReplyVisibility.PRIVATE -> interaction.respondEphemeral(builder)
            }
        }
    }

    private object UnavailableResponseController : ResponseController {
        override val hasResponded: Boolean = false
        override suspend fun respond(response: InteractionResponse): Unit =
            error("Autocomplete providers cannot send interaction responses")
    }
}

private fun BaseInputChatBuilder.addOption(option: OptionDescriptor) {
    when (option.type) {
        OptionType.STRING -> string(option.name, option.description) {
            required = option.required
            minLength = option.minimumLength
            maxLength = option.maximumLength
            autocomplete = option.autocompleteProvider != null
            option.choices.forEach { choice(it, it) }
        }
        OptionType.INTEGER -> integer(option.name, option.description) {
            required = option.required
            minValue = option.minimum
            maxValue = option.maximum
            autocomplete = option.autocompleteProvider != null
        }
        OptionType.NUMBER -> number(option.name, option.description) {
            required = option.required
            minValue = option.minimum?.toDouble()
            maxValue = option.maximum?.toDouble()
            autocomplete = option.autocompleteProvider != null
        }
        OptionType.BOOLEAN -> boolean(option.name, option.description) { required = option.required }
        OptionType.USER -> user(option.name, option.description) { required = option.required }
        OptionType.CHANNEL -> channel(option.name, option.description) { required = option.required }
        OptionType.ROLE -> role(option.name, option.description) { required = option.required }
        OptionType.MENTIONABLE -> mentionable(option.name, option.description) { required = option.required }
        OptionType.ATTACHMENT -> attachment(option.name, option.description) { required = option.required }
    }
}
