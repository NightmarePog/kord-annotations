package io.github.nightmarepog.kordannotations.testkit

import io.github.nightmarepog.kordannotations.CommandContext
import io.github.nightmarepog.kordannotations.CommandExecutor
import io.github.nightmarepog.kordannotations.CommandOptions
import io.github.nightmarepog.kordannotations.GeneratedCommand
import io.github.nightmarepog.kordannotations.HandlerResolver
import io.github.nightmarepog.kordannotations.InteractionIdentity
import io.github.nightmarepog.kordannotations.InteractionResponse
import io.github.nightmarepog.kordannotations.ResponseController

/** Records plain-text responses without connecting to Discord. */
class RecordingResponseController : ResponseController {
    /** Responses in the order they were sent. */
    val responses: MutableList<InteractionResponse> = mutableListOf()

    /** Whether at least one response has been recorded. */
    override val hasResponded: Boolean get() = responses.isNotEmpty()

    /** Appends [response] to [responses]. */
    override suspend fun respond(response: InteractionResponse) { responses += response }
}

/** The immutable responses and recording controller produced by a test execution. */
data class CommandTestResult(
    /** Snapshot of responses sent during execution. */
    val responses: List<InteractionResponse>,
    /** Controller used by the command context. */
    val controller: RecordingResponseController,
)

/** Executes generated commands against an in-memory response controller. */
class CommandTestHarness(private val resolver: HandlerResolver) {
    /** Executes [command] with the supplied raw [options] and [identity]. */
    suspend fun execute(
        command: GeneratedCommand,
        options: Map<String, Any?> = emptyMap(),
        identity: InteractionIdentity = InteractionIdentity(userId = "test-user"),
    ): CommandTestResult {
        val responses = RecordingResponseController()
        val context = CommandContext(
            command.descriptor,
            identity,
            CommandOptions.of(options),
            responses,
            resolver,
        )
        CommandExecutor(resolver).execute(command, context)
        return CommandTestResult(responses.responses.toList(), responses)
    }
}
