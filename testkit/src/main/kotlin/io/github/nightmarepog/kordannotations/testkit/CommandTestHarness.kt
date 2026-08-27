package io.github.nightmarepog.kordannotations.testkit

import io.github.nightmarepog.kordannotations.CommandContext
import io.github.nightmarepog.kordannotations.CommandExecutor
import io.github.nightmarepog.kordannotations.CommandOptions
import io.github.nightmarepog.kordannotations.GeneratedCommand
import io.github.nightmarepog.kordannotations.HandlerResolver
import io.github.nightmarepog.kordannotations.InteractionIdentity
import io.github.nightmarepog.kordannotations.InteractionResponse
import io.github.nightmarepog.kordannotations.ResponseController

class RecordingResponseController : ResponseController {
    val responses: MutableList<InteractionResponse> = mutableListOf()
    override val hasResponded: Boolean get() = responses.isNotEmpty()
    override suspend fun respond(response: InteractionResponse) { responses += response }
}

data class CommandTestResult(
    val responses: List<InteractionResponse>,
    val controller: RecordingResponseController,
)

class CommandTestHarness(private val resolver: HandlerResolver) {
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
