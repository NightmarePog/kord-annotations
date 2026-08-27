package io.github.nightmarepog.kordannotations

public fun interface OptionConverter<T : Any> {
    public suspend fun convert(value: Any, context: CommandContext): T
}

public data class AutocompleteChoice(public val name: String, public val value: String)

public fun interface AutocompleteProvider {
    public suspend fun complete(input: String, context: CommandContext): List<AutocompleteChoice>
}

public sealed interface CheckResult {
    public data object Allowed : CheckResult
    public data class Denied(public val failure: CommandFailure) : CheckResult
}

public fun interface AnnotationCheck<A : Annotation> {
    public suspend fun check(annotation: A, context: CommandContext): CheckResult
}

public sealed interface CommandExecutionEvent {
    public val descriptor: CommandDescriptor
    public data class Started(override val descriptor: CommandDescriptor) : CommandExecutionEvent
    public data class Succeeded(override val descriptor: CommandDescriptor, public val durationMillis: Long) : CommandExecutionEvent
    public data class Failed(
        override val descriptor: CommandDescriptor,
        public val durationMillis: Long,
        public val cause: Throwable,
    ) : CommandExecutionEvent
}

public fun interface CommandObserver {
    public suspend fun observe(event: CommandExecutionEvent)
}
