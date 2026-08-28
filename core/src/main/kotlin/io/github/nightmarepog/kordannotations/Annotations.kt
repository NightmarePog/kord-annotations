package io.github.nightmarepog.kordannotations

import kotlin.reflect.KClass

/**
 * Declares a chat-input command.
 *
 * On a class, [value] is the root command name and annotated member functions become subcommands.
 * On a function, [value] is the command or subcommand name.
 *
 * @property value Discord command name.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Command(val value: String)

/**
 * Declares a user context-menu command.
 *
 * @property value Discord command name.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class UserCommand(val value: String)

/**
 * Declares a message context-menu command.
 *
 * @property value Discord command name.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class MessageCommand(val value: String)

/**
 * Supplies literal Discord description text for a command, command group, or option.
 *
 * The value is not treated as a translation key.
 *
 * @property value Literal description sent to Discord.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Description(val value: String)

/**
 * Overrides the Discord option name inferred from the annotated parameter.
 *
 * @property value Discord option name.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Option(val value: String)

/**
 * Restricts a string option to the supplied values.
 *
 * @property value Allowed option values.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Choices(vararg val value: String)

/**
 * Restricts an integer or number option to an inclusive range.
 *
 * @property minimum Inclusive lower bound.
 * @property maximum Inclusive upper bound.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Range(val minimum: Long = Long.MIN_VALUE, val maximum: Long = Long.MAX_VALUE)

/**
 * Restricts the length of a string option to an inclusive range.
 *
 * @property minimum Inclusive minimum length.
 * @property maximum Inclusive maximum length.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Length(val minimum: Int = 0, val maximum: Int = Int.MAX_VALUE)

/**
 * Resolves completion choices for the annotated option.
 *
 * @property provider Provider resolved for autocomplete interactions.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Autocomplete(val provider: KClass<out AutocompleteProvider>)

/**
 * Converts a Discord option of type [from] into the annotated parameter type using [provider].
 *
 * @property provider Converter resolved for command execution.
 * @property from Discord option type accepted by the converter.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class ConvertWith(
    val provider: KClass<out OptionConverter<*>>,
    val from: OptionType,
)

/**
 * Declares a button handler.
 *
 * @property value Component custom ID.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Button(val value: String)

/**
 * Declares a select-menu handler.
 *
 * @property value Component custom ID.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class SelectMenu(val value: String)

/**
 * Declares a modal-submit handler.
 *
 * @property value Component custom ID.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Modal(val value: String)

/** Makes plain-text responses private to the invoking user. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class PrivateResponse

/** Allows the command to be installed and used in bot direct messages. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class BotDM

/**
 * Limits a command to supplied Discord interaction contexts.
 *
 * @property value Allowed contexts.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class AvailableIn(vararg val value: InteractionContextType)

/**
 * Prevents another invocation in the same scope for a duration.
 *
 * @property seconds Cooldown duration in seconds.
 * @property per Identity scope shared by cooldown entries.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Cooldown(val seconds: Int, val per: CooldownScope = CooldownScope.USER)

/**
 * Cancels a handler that exceeds a duration.
 *
 * @property seconds Maximum execution time in seconds.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Timeout(val seconds: Int)

/** Disables the default handler timeout. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class NoTimeout

/**
 * Sends a loading response if the handler has not responded within the configured delay.
 *
 * @property value Loading response content.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class LoadingResponse(val value: String = "Working…")

/** Disables the automatic loading response. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class NoLoadingResponse

/**
 * Associates a reusable annotation with an [AnnotationCheck].
 *
 * @property value Check resolved before invoking an annotated handler.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckedBy(val value: KClass<out AnnotationCheck<*>>)
