# Kord Annotations

Kord Annotations generates Discord command metadata and dispatch code from Kotlin handlers. It uses KSP at compile time, then loads generated modules through `ServiceLoader` at runtime.

Requires Java 21.

## Setup

The plugin is available from the Gradle Plugin Portal. Its libraries are available from Maven Central. No GitHub credentials are required.

```kotlin
plugins {
    id("io.github.nightmarepog.kord-annotations") version "0.2.0"
}
```

The plugin applies Kotlin/JVM and KSP, then adds matching versions of the core library and processor. Projects that replace Gradle's default repositories need `gradlePluginPortal()` in plugin management and `mavenCentral()` in dependency resolution.

Generated names can be changed if they conflict with existing code:

```kotlin
kordAnnotations {
    generatedPackage.set("com.example.bot.generated")
    moduleName.set("BotCommandModule")
}
```

## First command

```kotlin
class GeneralCommands {
    @Command("ping")
    @Description("Replies with pong")
    suspend fun ping(context: CommandContext) = context.respond("Pong!")
}
```

Create the runtime with every handler and extension instance, install its listeners, then log in:

```kotlin
val commands = KordAnnotations(
    modules = CommandModules.load(),
    handlerResolver = InstanceHandlerResolver(GeneralCommands()),
)

commands.install(kord)
commands.syncGlobalCommands(kord)
kord.login()
```

`syncGlobalCommands` replaces the complete global command set. Commands not present in the generated modules are removed.

## Handler rules

The first command parameter must be `CommandContext`. Remaining parameters become Discord options. Nullable parameters and parameters with Kotlin defaults are optional.

```kotlin
class ModerationCommands {
    @Command("warn")
    @Description("Warns a server member")
    @PrivateResponse
    @Cooldown(seconds = 10)
    suspend fun warn(
        context: CommandContext,
        @Description("Member to warn") member: Member,
        @Description("Reason shown to the member") reason: String = "No reason supplied",
    ) = context.respond("Warned ${member.displayName}: $reason")
}
```

`@Description` contains literal Discord text. It is not a translation key.

Supported option values include strings, integers, numbers, booleans, users, members, channels, roles, mentionables, and attachments. `@Option` changes the generated option name. `@Choices`, `@Range`, and `@Length` add Discord constraints.

Annotating a class creates a slash-command root. Annotated methods become subcommands:

```kotlin
@Command("settings")
@Description("Changes bot settings")
class SettingsCommands {
    @Command("language")
    @Description("Changes the language")
    suspend fun language(
        context: CommandContext,
        @Description("Locale tag") locale: String,
    ) = context.respond("Language changed to $locale")
}
```

`@UserCommand` and `@MessageCommand` create context-menu commands.

## Execution policies

- `@PrivateResponse` makes plain-text responses private.
- `@AvailableIn` selects Discord interaction contexts.
- `@BotDM` enables bot direct messages.
- `@Cooldown` limits repeated use by user, channel, guild, or globally.
- `@Timeout` changes the default 30-second timeout.
- `@NoTimeout` disables the timeout.
- `@LoadingResponse` changes the response sent when a handler takes longer than two seconds.
- `@NoLoadingResponse` disables that response.

Policy annotations on a handler override the same policy on its class.

## Checks

Attach a checker to a reusable annotation:

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@CheckedBy(AdminOnlyCheck::class)
annotation class AdminOnly

class AdminOnlyCheck : AnnotationCheck<AdminOnly> {
    override suspend fun check(annotation: AdminOnly, context: CommandContext): CheckResult =
        if (isAdmin(context.identity.userId)) CheckResult.Allowed
        else CheckResult.Denied(CommandFailure("Only administrators can use this command."))
}
```

Register the checker with the handler resolver. A denied check sends its message privately and does not invoke the handler.

## Autocomplete and conversion

An autocomplete provider returns up to 25 choices:

```kotlin
class LocaleAutocomplete : AutocompleteProvider {
    override suspend fun complete(input: String, context: CommandContext) =
        supportedLocales
            .filter { it.startsWith(input, ignoreCase = true) }
            .map { AutocompleteChoice(it, it) }
}
```

Apply it with `@Autocomplete(LocaleAutocomplete::class)` and register the provider with the handler resolver.

`@ConvertWith` maps a Discord option into a domain type:

```kotlin
data class DurationSeconds(val value: Long)

class DurationConverter : OptionConverter<DurationSeconds> {
    override suspend fun convert(value: Any, context: CommandContext) =
        DurationSeconds(value as Long)
}

class UtilityCommands {
    @Command("delay")
    @Description("Sets a delay")
    suspend fun delay(
        context: CommandContext,
        @Description("Delay in seconds")
        @ConvertWith(DurationConverter::class, OptionType.INTEGER)
        duration: DurationSeconds,
    ) = context.respond("Delay: ${duration.value}s")
}
```

## Components

Buttons, select menus, and modal submissions use generated handlers too:

```kotlin
class TicketComponents {
    @Button("ticket close")
    @PrivateResponse
    suspend fun close(context: ComponentContext) = context.respond("Ticket closed")
}
```

A component handler receives one `ComponentContext`. Select-menu values are available through `context.values`.

`InMemoryComponentStateStore` creates tokens tied to one Discord user. Tokens last 15 minutes by default. Pass `reusable = false` when state should be consumed once.

## Spring Boot

```kotlin
dependencies {
    implementation("io.github.nightmarepog:kord-annotations-spring:0.2.0")
}
```

With a `Kord` bean present, Spring discovers generated handlers, installs listeners, and synchronizes global commands during startup.

```properties
kord-annotations.enabled=true
kord-annotations.sync-global-commands=true
kord-annotations.maximum-sync-attempts=3
```

Set `sync-global-commands` to `false` when command synchronization is handled elsewhere.

## Testing

```kotlin
dependencies {
    testImplementation("io.github.nightmarepog:kord-annotations-testkit:0.2.0")
}
```

```kotlin
val command = CommandModules.load()
    .flatMap { it.commands }
    .single { it.descriptor.name == "ping" }

val result = CommandTestHarness(InstanceHandlerResolver(GeneralCommands()))
    .execute(command)

assertEquals("Pong!", result.responses.single().content)
```

The testkit runs the same checks, cooldowns, loading behavior, timeout handling, and generated invocation path without connecting to Discord.

## Modules

| Artifact | Contents |
| --- | --- |
| `kord-annotations-core` | Annotations, descriptors, runtime, policies, and component state |
| `kord-annotations-processor` | KSP validation and code generation |
| `kord-annotations-gradle-plugin` | Kotlin/JVM, KSP, and dependency setup |
| `kord-annotations-spring` | Spring Boot discovery and auto-configuration |
| `kord-annotations-help` | Help entries generated from command descriptors |
| `kord-annotations-testkit` | In-memory command execution and response recording |

## License

[Mozilla Public License 2.0](LICENSE)
