package io.github.nightmarepog.kordannotations.help

import io.github.nightmarepog.kordannotations.ApplicationCommandType
import io.github.nightmarepog.kordannotations.CommandModule

class HelpCatalog(modules: Iterable<CommandModule>) {
    data class Entry(val invocation: String, val description: String)

    val entries: List<Entry> = modules.flatMap { it.commands }
        .filter { it.descriptor.type == ApplicationCommandType.CHAT_INPUT }
        .map { command ->
            val invocation = listOfNotNull(command.descriptor.parentName, command.descriptor.name).joinToString(" ", prefix = "/")
            Entry(invocation, command.descriptor.description)
        }
        .sortedBy(Entry::invocation)

    fun render(): String = entries.joinToString("\n") { "`${it.invocation}` — ${it.description}" }
}
