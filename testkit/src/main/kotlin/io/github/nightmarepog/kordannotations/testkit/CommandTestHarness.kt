package io.github.nightmarepog.kordannotations.testkit

import io.github.nightmarepog.kordannotations.CommandContext
import io.github.nightmarepog.kordannotations.CommandExecutor
import io.github.nightmarepog.kordannotations.CommandOptions
import io.github.nightmarepog.kordannotations.EmptyTranslations
import io.github.nightmarepog.kordannotations.GeneratedCommand
import io.github.nightmarepog.kordannotations.HandlerResolver
import io.github.nightmarepog.kordannotations.InteractionIdentity
import io.github.nightmarepog.kordannotations.InteractionResponse
import io.github.nightmarepog.kordannotations.ResponseController
import io.github.nightmarepog.kordannotations.TranslationProvider

public class RecordingResponseController : ResponseController {
    public val responses: MutableList<InteractionResponse> = mutableListOf()
    override val hasResponded: Boolean get() = responses.isNotEmpty()
    override suspend fun respond(response: InteractionResponse) { responses += response }
}

public data class CommandTestResult(
    public val responses: List<InteractionResponse>,
    public val controller: RecordingResponseController,
)

public class CommandTestHarness(
    private val resolver: HandlerResolver,
    private val translations: TranslationProvider = EmptyTranslations,
) {
    public suspend fun execute(
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
            translations,
            resolver,
        )
        CommandExecutor(resolver).execute(command, context)
        return CommandTestResult(responses.responses.toList(), responses)
    }
}
