package io.github.nightmarepog.kordannotations.testkit

import io.github.nightmarepog.kordannotations.Command
import io.github.nightmarepog.kordannotations.CommandContext
import io.github.nightmarepog.kordannotations.CommandModules
import io.github.nightmarepog.kordannotations.Description
import io.github.nightmarepog.kordannotations.ConvertWith
import io.github.nightmarepog.kordannotations.OptionConverter
import io.github.nightmarepog.kordannotations.OptionType
import io.github.nightmarepog.kordannotations.AnnotationCheck
import io.github.nightmarepog.kordannotations.CheckedBy
import io.github.nightmarepog.kordannotations.CheckResult
import io.github.nightmarepog.kordannotations.InstanceHandlerResolver
import io.github.nightmarepog.kordannotations.NoLoadingResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingCommands {
    @Command("greet")
    @Description("Greets somebody")
    @NoLoadingResponse
    suspend fun greet(
        context: CommandContext,
        @Description("Who to greet") name: String,
        @Description("Ending punctuation") punctuation: String = "!",
    ) {
        context.respond("Hello, $name$punctuation")
    }

    @Command("wait")
    @Description("Waits for a duration")
    @NoLoadingResponse
    @AllowedForTests
    suspend fun wait(
        context: CommandContext,
        @Description("Seconds to wait")
        @ConvertWith(SecondsConverter::class, OptionType.INTEGER)
        duration: Seconds,
    ) {
        context.respond("Waiting ${duration.value} seconds")
    }
}

data class Seconds(val value: Long)

class SecondsConverter : OptionConverter<Seconds> {
    override suspend fun convert(value: Any, context: CommandContext): Seconds = Seconds(value as Long)
}

@Target(AnnotationTarget.FUNCTION)
@CheckedBy(AllowForTestsCheck::class)
annotation class AllowedForTests

class AllowForTestsCheck : AnnotationCheck<AllowedForTests> {
    override suspend fun check(annotation: AllowedForTests, context: CommandContext): CheckResult = CheckResult.Allowed
}

class GeneratedModuleTest {
    @Test
    fun `generated invocation preserves Kotlin defaults`() = runTest {
        val module = CommandModules.load().single()
        val command = module.commands.single { it.descriptor.name == "greet" }
        val harness = CommandTestHarness(InstanceHandlerResolver(GreetingCommands(), SecondsConverter(), AllowForTestsCheck()))

        val result = harness.execute(command, mapOf("name" to "Ada"))

        assertEquals("Hello, Ada!", result.responses.single().content)
        assertEquals("Greets somebody", command.descriptor.description)
        assertEquals("Who to greet", command.descriptor.options.single { it.name == "name" }.description)
        assertEquals(false, command.descriptor.options.single { it.name == "punctuation" }.required)
    }

    @Test
    fun `generated invocation applies a typed converter`() = runTest {
        val command = CommandModules.load().single().commands.single { it.descriptor.name == "wait" }
        val harness = CommandTestHarness(InstanceHandlerResolver(GreetingCommands(), SecondsConverter(), AllowForTestsCheck()))

        val result = harness.execute(command, mapOf("duration" to 12L))

        assertEquals("Waiting 12 seconds", result.responses.single().content)
        assertEquals(OptionType.INTEGER, command.descriptor.options.single().type)
        assertEquals(AllowForTestsCheck::class, command.descriptor.checks.single().checkerType)
    }
}
