package io.github.nightmarepog.kordannotations.help

import io.github.nightmarepog.kordannotations.ApplicationCommandType
import io.github.nightmarepog.kordannotations.CommandModule

/** Builds user-facing help entries from generated chat-input commands. */
class HelpCatalog(modules: Iterable<CommandModule>) {
    /** One slash-command invocation and its Discord description. */
    data class Entry(
        /** Slash-command invocation beginning with `/`. */
        val invocation: String,
        /** Literal Discord command description. */
        val description: String,
    )

    /** Chat-input commands sorted by invocation. */
    val entries: List<Entry> = modules.flatMap { it.commands }
        .filter { it.descriptor.type == ApplicationCommandType.CHAT_INPUT }
        .map { command ->
            val invocation = listOfNotNull(command.descriptor.parentName, command.descriptor.name).joinToString(" ", prefix = "/")
            Entry(invocation, command.descriptor.description)
        }
        .sortedBy(Entry::invocation)

    /** Renders one Markdown-formatted command per line. */
    fun render(): String = entries.joinToString("\n") { "`${it.invocation}` - ${it.description}" }
}
