package io.github.nightmarepog.kordannotations

fun interface OptionConverter<T : Any> {
    suspend fun convert(value: Any, context: CommandContext): T
}

data class AutocompleteChoice(val name: String, val value: String)

fun interface AutocompleteProvider {
    suspend fun complete(input: String, context: CommandContext): List<AutocompleteChoice>
}

sealed interface CheckResult {
    data object Allowed : CheckResult
    data class Denied(val failure: CommandFailure) : CheckResult
}

fun interface AnnotationCheck<A : Annotation> {
    suspend fun check(annotation: A, context: CommandContext): CheckResult
}

sealed interface CommandExecutionEvent {
    val descriptor: CommandDescriptor
    data class Started(override val descriptor: CommandDescriptor) : CommandExecutionEvent
    data class Succeeded(override val descriptor: CommandDescriptor, val durationMillis: Long) : CommandExecutionEvent
    data class Failed(
        override val descriptor: CommandDescriptor,
        val durationMillis: Long,
        val cause: Throwable,
    ) : CommandExecutionEvent
}

fun interface CommandObserver {
    suspend fun observe(event: CommandExecutionEvent)
}
