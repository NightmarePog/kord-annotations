package io.github.nightmarepog.kordannotations.help

import io.github.nightmarepog.kordannotations.ApplicationCommandType
import io.github.nightmarepog.kordannotations.CommandModule

public class HelpCatalog(modules: Iterable<CommandModule>) {
    public data class Entry(public val invocation: String, public val description: String)

    public val entries: List<Entry> = modules.flatMap { it.commands }
        .filter { it.descriptor.type == ApplicationCommandType.CHAT_INPUT }
        .map { command ->
            val invocation = listOfNotNull(command.descriptor.parentName, command.descriptor.name).joinToString(" ", prefix = "/")
            Entry(invocation, command.descriptor.description)
        }
        .sortedBy(Entry::invocation)

    public fun render(): String = entries.joinToString("\n") { "`${it.invocation}` — ${it.description}" }
}
