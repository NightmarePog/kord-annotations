package io.github.nightmarepog.kordannotations

/** Converts a raw Discord option value into a handler parameter value. */
fun interface OptionConverter<T : Any> {
    /** Returns the value passed to the command handler. */
    suspend fun convert(value: Any, context: CommandContext): T
}

/**
 * A choice returned for an autocomplete request.
 *
 * @property name Text displayed to the user.
 * @property value Value submitted to the command.
 */
data class AutocompleteChoice(val name: String, val value: String)

/** Supplies at most 25 choices for an autocomplete request. */
fun interface AutocompleteProvider {
    /** Returns choices matching the user's current [input]. */
    suspend fun complete(input: String, context: CommandContext): List<AutocompleteChoice>
}

/** The outcome of an [AnnotationCheck]. */
sealed interface CheckResult {
    /** Allows the handler to run. */
    data object Allowed : CheckResult

    /**
     * Stops execution and responds privately.
     *
     * @property failure Failure sent to the invoking user.
     */
    data class Denied(val failure: CommandFailure) : CheckResult
}

/** Enforces an annotation attached through [CheckedBy]. */
fun interface AnnotationCheck<A : Annotation> {
    /** Returns whether the command may run for [context]. */
    suspend fun check(annotation: A, context: CommandContext): CheckResult
}

/** An observable stage in command execution. */
sealed interface CommandExecutionEvent {
    /** Describes the command associated with this event. */
    val descriptor: CommandDescriptor

    /** Emitted immediately before checks and cooldown enforcement. */
    data class Started(override val descriptor: CommandDescriptor) : CommandExecutionEvent

    /**
     * Emitted after the handler completes successfully.
     *
     * @property durationMillis Total execution time in milliseconds.
     */
    data class Succeeded(override val descriptor: CommandDescriptor, val durationMillis: Long) : CommandExecutionEvent

    /**
     * Emitted when execution is denied, times out, or throws.
     *
     * @property durationMillis Total execution time in milliseconds.
     * @property cause Failure that ended execution.
     */
    data class Failed(
        override val descriptor: CommandDescriptor,
        val durationMillis: Long,
        val cause: Throwable,
    ) : CommandExecutionEvent
}

/** Receives command execution events without affecting command execution if observation fails. */
fun interface CommandObserver {
    /** Observes [event]. Exceptions thrown here are ignored by the executor. */
    suspend fun observe(event: CommandExecutionEvent)
}
