package io.github.nightmarepog.kordannotations.processor

import com.google.devtools.ksp.validate
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import java.io.OutputStream

internal class KordAnnotationsProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val annotationNames = listOf(COMMAND, USER_COMMAND, MESSAGE_COMMAND, BUTTON, SELECT_MENU, MODAL)
        val functions = annotationNames.asSequence()
            .flatMap { resolver.getSymbolsWithAnnotation(it) }
            .filterIsInstance<KSFunctionDeclaration>()
            .distinctBy { it.qualifiedName?.asString() + it.parameters.joinToString { parameter -> parameter.type.toString() } }
            .toList()
        if (functions.any { !it.validate() }) return functions.filterNot { it.validate() }

        generated = true
        if (functions.isEmpty()) return emptyList()
        val sources = functions.mapNotNull { it.containingFile }.distinct().toTypedArray()
        val moduleName = options["kordAnnotations.moduleName"]?.identifier() ?: "GeneratedKordAnnotationsModule"
        val packageName = options["kordAnnotations.generatedPackage"] ?: "io.github.nightmarepog.kordannotations.generated"
        val commands = functions.mapNotNull(::commandSource)
        val components = functions.mapNotNull(::componentSource)
        if (commands.isEmpty() && components.isEmpty()) return emptyList()

        codeGenerator.createNewFile(Dependencies(true, *sources), packageName, moduleName).bufferedWriter().use { writer ->
            writer.appendLine("package $packageName")
            writer.appendLine()
            writer.appendLine("import io.github.nightmarepog.kordannotations.*")
            writer.appendLine()
            writer.appendLine("public class $moduleName : CommandModule {")
            writer.appendLine("    override val commands: List<GeneratedCommand> = listOf(")
            commands.forEach { writer.appendLine(it.prependIndent("        ") + ",") }
            writer.appendLine("    )")
            writer.appendLine("    override val components: List<GeneratedComponent> = listOf(")
            components.forEach { writer.appendLine(it.prependIndent("        ") + ",") }
            writer.appendLine("    )")
            writer.appendLine("}")
        }
        codeGenerator.createNewFileByPath(
            Dependencies(true, *sources),
            "META-INF/services/io.github.nightmarepog.kordannotations.CommandModule",
            "",
        ).writeUtf8("$packageName.$moduleName\n")
        return emptyList()
    }

    private fun commandSource(function: KSFunctionDeclaration): String? {
        val commandAnnotation = function.annotation(COMMAND)
        val userAnnotation = function.annotation(USER_COMMAND)
        val messageAnnotation = function.annotation(MESSAGE_COMMAND)
        if (commandAnnotation == null && userAnnotation == null && messageAnnotation == null) return null
        val owner = function.parentDeclaration as? KSClassDeclaration ?: return error(function, "Command handlers must be member functions")
        if (Modifier.PRIVATE in function.modifiers || Modifier.PROTECTED in function.modifiers) {
            return error(function, "Command handlers must be visible to generated code")
        }
        val description = function.annotation(DESCRIPTION)?.stringValue()
        if (commandAnnotation != null && description == null) return error(function, "@Command requires @Description")
        val parameters = function.parameters
        val context = parameters.firstOrNull()
        if (context?.qualifiedType() != COMMAND_CONTEXT) return error(function, "The first command parameter must be CommandContext")
        val options = parameters.drop(1).mapNotNull { optionSource(it, function) }
        if (options.size != parameters.size - 1) return null

        val ownerName = owner.qualifiedName?.asString() ?: return error(function, "Command owner must have a qualified name")
        val parentName = owner.annotation(COMMAND)?.stringValue()
        val parentDescription = owner.annotation(DESCRIPTION)?.stringValue()
        val annotation = commandAnnotation ?: userAnnotation ?: messageAnnotation!!
        val commandName = annotation.stringValue() ?: return error(function, "Command name cannot be empty")
        val type = when {
            userAnnotation != null -> "ApplicationCommandType.USER"
            messageAnnotation != null -> "ApplicationCommandType.MESSAGE"
            else -> "ApplicationCommandType.CHAT_INPUT"
        }
        val invocation = invocationSource(ownerName, function, context, parameters.drop(1))
        val optionsSource = options.joinToString(",\n").ifEmpty { "" }
        val checksSource = checkSources(owner, function).joinToString(",\n")
        return """
            GeneratedCommand(
                ownerType = $ownerName::class,
                descriptor = CommandDescriptor(
                    name = ${commandName.literal()},
                    description = ${(description ?: commandName).literal()},
                    type = $type,
                    parentName = ${parentName?.literal() ?: "null"},
                    parentDescription = ${parentDescription?.literal() ?: "null"},
                    options = listOf(
            ${optionsSource.prependIndent("            ")}
                    ),
                    execution = ${executionPolicySource(owner, function)},
                    checks = listOf(
            ${checksSource.prependIndent("            ")}
                    ),
                ),
                invoke = { owner, context ->
            ${invocation.prependIndent("        ")}
                },
            )
        """.trimIndent()
    }

    private fun checkSources(vararg annotated: KSAnnotated): List<String> = annotated.flatMap { symbol ->
        symbol.annotations.mapNotNull { annotation ->
            val declaration = annotation.annotationType.resolve().declaration as? KSClassDeclaration ?: return@mapNotNull null
            val checkedBy = declaration.annotation(CHECKED_BY) ?: return@mapNotNull null
            val checkerType = checkedBy.typeValue("value") ?: return@mapNotNull null
            val annotationType = declaration.qualifiedName?.asString() ?: return@mapNotNull null
            val arguments = annotation.arguments.joinToString { argument ->
                val name = argument.name?.asString() ?: return@joinToString renderAnnotationValue(argument.value)
                "$name = ${renderAnnotationValue(argument.value)}"
            }
            "GeneratedCheck(annotation = $annotationType($arguments), checkerType = $checkerType::class)"
        }.toList()
    }

    private fun renderAnnotationValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> value.literal()
        is Char -> value.toString().literal() + ".single()"
        is Boolean, is Byte, is Short, is Int -> value.toString()
        is Long -> "${value}L"
        is Float -> "${value}f"
        is Double -> value.toString()
        is KSType -> "${value.declaration.qualifiedName?.asString()}::class"
        is List<*> -> value.joinToString(prefix = "[", postfix = "]") { renderAnnotationValue(it) }
        else -> value.toString()
    }

    private fun componentSource(function: KSFunctionDeclaration): String? {
        val annotations = listOf(BUTTON, SELECT_MENU, MODAL).mapNotNull { name -> function.annotation(name)?.let { name to it } }
        if (annotations.isEmpty()) return null
        val (name, annotation) = annotations.singleOrNull() ?: return error(function, "A component handler may have only one component annotation")
        val owner = function.parentDeclaration as? KSClassDeclaration ?: return error(function, "Component handlers must be member functions")
        val context = function.parameters.singleOrNull()
        if (context?.qualifiedType() != COMPONENT_CONTEXT) return error(function, "A component handler must have one ComponentContext parameter")
        val ownerName = owner.qualifiedName?.asString() ?: return error(function, "Component owner must have a qualified name")
        val functionName = function.simpleName.asString()
        val contextName = context.name?.asString() ?: "context"
        val componentType = when (name) {
            BUTTON -> "ComponentType.BUTTON"
            SELECT_MENU -> "ComponentType.SELECT_MENU"
            else -> "ComponentType.MODAL"
        }
        return """
            GeneratedComponent(
                ownerType = $ownerName::class,
                descriptor = ComponentDescriptor(
                    id = ${annotation.stringValue().orEmpty().literal()},
                    type = $componentType,
                    execution = ${executionPolicySource(owner, function)},
                ),
                invoke = { owner, context -> (owner as $ownerName).$functionName($contextName = context) },
            )
        """.trimIndent()
    }

    private fun optionSource(parameter: KSValueParameter, function: KSFunctionDeclaration): String? {
        val typeName = parameter.qualifiedType()
        val converter = parameter.annotation(CONVERT_WITH)
        val optionType = converter?.enumValue("from")?.let { "OptionType.$it" } ?: when (typeName) {
            "kotlin.String" -> "OptionType.STRING"
            "kotlin.Int", "kotlin.Long" -> "OptionType.INTEGER"
            "kotlin.Double", "kotlin.Float" -> "OptionType.NUMBER"
            "kotlin.Boolean" -> "OptionType.BOOLEAN"
            "dev.kord.core.entity.User", "dev.kord.core.entity.Member" -> "OptionType.USER"
            "dev.kord.core.entity.Role" -> "OptionType.ROLE"
            "dev.kord.core.entity.channel.ResolvedChannel" -> "OptionType.CHANNEL"
            "dev.kord.core.entity.Attachment" -> "OptionType.ATTACHMENT"
            "dev.kord.core.entity.Entity" -> "OptionType.MENTIONABLE"
            else -> return error(parameter, "Unsupported command option type $typeName in ${function.simpleName.asString()}")
        }
        val parameterName = parameter.name?.asString() ?: return error(parameter, "Command option needs a name")
        val optionName = parameter.annotation(OPTION)?.stringValue() ?: parameterName
        val description = parameter.annotation(DESCRIPTION)?.stringValue()
            ?: return error(parameter, "Command option $parameterName requires @Description")
        val required = parameter.type.resolve().nullability != Nullability.NULLABLE && !parameter.hasDefault
        val choices = parameter.annotation(CHOICES)?.arguments?.firstOrNull()?.value as? List<*>
        val choicesSource = choices.orEmpty().joinToString { it.toString().literal() }
        val range = parameter.annotation(RANGE)
        val length = parameter.annotation(LENGTH)
        val autocomplete = parameter.annotation(AUTOCOMPLETE)?.typeValue("provider")
        val converterProvider = converter?.typeValue("provider")
        if (autocomplete != null && optionType != "OptionType.STRING") {
            return error(parameter, "AutocompleteProvider currently supports String options only")
        }
        return """
            OptionDescriptor(
                name = ${optionName.literal()},
                description = ${description.literal()},
                type = $optionType,
                valueTypeName = ${typeName.literal()},
                required = $required,
                choices = listOf($choicesSource),
                minimum = ${range?.longValue("minimum")?.takeUnless { it == Long.MIN_VALUE }?.let { "${it}L" } ?: "null"},
                maximum = ${range?.longValue("maximum")?.takeUnless { it == Long.MAX_VALUE }?.let { "${it}L" } ?: "null"},
                minimumLength = ${length?.longValue("minimum")?.toInt()?.takeUnless { it == 0 } ?: "null"},
                maximumLength = ${length?.longValue("maximum")?.toInt()?.takeUnless { it == Int.MAX_VALUE } ?: "null"},
                autocompleteProvider = ${autocomplete?.let { "$it::class" } ?: "null"},
                converterProvider = ${converterProvider?.let { "$it::class" } ?: "null"},
            )
        """.trimIndent()
    }

    private fun invocationSource(
        ownerName: String,
        function: KSFunctionDeclaration,
        context: KSValueParameter,
        parameters: List<KSValueParameter>,
    ): String {
        val owner = "(owner as $ownerName)"
        val functionName = function.simpleName.asString()
        val fixed = parameters.filterNot { it.hasDefault }
        val defaults = parameters.filter { it.hasDefault }
        val combinations = (0 until (1 shl defaults.size)).reversed().toList()
        fun argument(parameter: KSValueParameter): String {
            val name = parameter.name!!.asString()
            val optionName = parameter.annotation(OPTION)?.stringValue() ?: name
            val type = parameter.qualifiedType()
            val converter = parameter.annotation(CONVERT_WITH)?.typeValue("provider")
            val nullable = parameter.type.resolve().nullability == Nullability.NULLABLE
            if (converter != null) {
                val method = if (nullable) "convertOptional" else "convert"
                return "$name = context.$method(${optionName.literal()}, $type::class, $converter::class)"
            }
            val accessor = if (nullable) "optional" else "require"
            return "$name = context.options.$accessor(${optionName.literal()}, $type::class)"
        }
        fun call(includedDefaults: List<KSValueParameter>): String {
            val arguments = listOf("${context.name?.asString() ?: "context"} = context") + (fixed + includedDefaults).map(::argument)
            return "$owner.$functionName(${arguments.joinToString()})"
        }
        if (defaults.isEmpty()) return call(emptyList())
        return buildString {
            combinations.forEachIndexed { index, mask ->
                val included = defaults.filterIndexed { bit, _ -> mask and (1 shl bit) != 0 }
                val condition = included.joinToString(" && ") {
                    val optionName = it.annotation(OPTION)?.stringValue() ?: it.name!!.asString()
                    "context.options.contains(${optionName.literal()})"
                }
                when {
                    index == combinations.lastIndex -> append("else ${call(emptyList())}")
                    condition.isEmpty() -> append("else ${call(emptyList())}")
                    index == 0 -> append("if ($condition) ${call(included)} ")
                    else -> append("else if ($condition) ${call(included)} ")
                }
            }
        }
    }

    private fun executionPolicySource(owner: KSClassDeclaration, function: KSFunctionDeclaration): String {
        fun has(name: String) = function.annotation(name) != null || owner.annotation(name) != null
        fun nearest(name: String) = function.annotation(name) ?: owner.annotation(name)
        val visibility = if (has(PRIVATE_RESPONSE)) "ReplyVisibility.PRIVATE" else "ReplyVisibility.PUBLIC"
        val timeout = when {
            has(NO_TIMEOUT) -> "null"
            nearest(TIMEOUT) != null -> nearest(TIMEOUT)!!.longValue("seconds").toString()
            else -> "30"
        }
        val loading = when {
            has(NO_LOADING_RESPONSE) -> "null"
            nearest(LOADING_RESPONSE) != null -> nearest(LOADING_RESPONSE)!!.stringValue().orEmpty().literal()
            else -> "\"kordAnnotations.loading\""
        }
        val cooldown = nearest(COOLDOWN)
        val cooldownSeconds = cooldown?.longValue("seconds")?.toString() ?: "null"
        val cooldownScope = cooldown?.enumValue("per")?.let { "CooldownScope.$it" } ?: "CooldownScope.USER"
        val contexts = nearest(AVAILABLE_IN)?.arguments?.firstOrNull()?.value as? List<*>
        val contextSource = when {
            contexts != null && contexts.isNotEmpty() -> contexts.joinToString { "InteractionContextType.${it.toString().substringAfterLast('.')}" }
            has(BOT_DM) -> "InteractionContextType.GUILD, InteractionContextType.BOT_DM"
            else -> "InteractionContextType.GUILD"
        }
        return "ExecutionPolicy(visibility = $visibility, contexts = setOf($contextSource), cooldownSeconds = $cooldownSeconds, cooldownScope = $cooldownScope, timeoutSeconds = $timeout, loadingResponseKey = $loading, observed = ${has(OBSERVED)})"
    }

    private fun KSAnnotated.annotation(name: String): KSAnnotation? = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == name
    }

    private fun KSValueParameter.qualifiedType(): String = type.resolve().declaration.qualifiedName?.asString().orEmpty()
    private fun KSAnnotation.stringValue(): String? = arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String
    private fun KSAnnotation.longValue(name: String): Long = (arguments.first { it.name?.asString() == name }.value as Number).toLong()
    private fun KSAnnotation.enumValue(name: String): String? = arguments.firstOrNull { it.name?.asString() == name }?.value?.toString()?.substringAfterLast('.')
    private fun KSAnnotation.typeValue(name: String): String? =
        (arguments.firstOrNull { it.name?.asString() == name }?.value as? KSType)?.declaration?.qualifiedName?.asString()
    private fun String.literal(): String = buildString {
        append('"')
        this@literal.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '$' -> append("\\$")
                else -> append(character)
            }
        }
        append('"')
    }
    private fun String.identifier(): String = replace(Regex("[^A-Za-z0-9_]"), "_").let { if (it.firstOrNull()?.isDigit() == true) "_$it" else it }
    private fun <T> error(symbol: KSAnnotated, message: String): T? { logger.error(message, symbol); return null }
    private fun OutputStream.writeUtf8(value: String) = bufferedWriter().use { it.write(value) }

    private companion object {
        const val PREFIX = "io.github.nightmarepog.kordannotations."
        const val COMMAND = PREFIX + "Command"
        const val USER_COMMAND = PREFIX + "UserCommand"
        const val MESSAGE_COMMAND = PREFIX + "MessageCommand"
        const val DESCRIPTION = PREFIX + "Description"
        const val OPTION = PREFIX + "Option"
        const val CHOICES = PREFIX + "Choices"
        const val RANGE = PREFIX + "Range"
        const val LENGTH = PREFIX + "Length"
        const val AUTOCOMPLETE = PREFIX + "Autocomplete"
        const val CONVERT_WITH = PREFIX + "ConvertWith"
        const val BUTTON = PREFIX + "Button"
        const val SELECT_MENU = PREFIX + "SelectMenu"
        const val MODAL = PREFIX + "Modal"
        const val PRIVATE_RESPONSE = PREFIX + "PrivateResponse"
        const val BOT_DM = PREFIX + "BotDM"
        const val AVAILABLE_IN = PREFIX + "AvailableIn"
        const val COOLDOWN = PREFIX + "Cooldown"
        const val TIMEOUT = PREFIX + "Timeout"
        const val NO_TIMEOUT = PREFIX + "NoTimeout"
        const val LOADING_RESPONSE = PREFIX + "LoadingResponse"
        const val NO_LOADING_RESPONSE = PREFIX + "NoLoadingResponse"
        const val OBSERVED = PREFIX + "Observed"
        const val COMMAND_CONTEXT = PREFIX + "CommandContext"
        const val COMPONENT_CONTEXT = PREFIX + "ComponentContext"
        const val CHECKED_BY = PREFIX + "CheckedBy"
    }
}
