# Kord Annotations

Kord Annotations is a compile-time command framework for [Kord](https://github.com/kordlib/kord). Annotate Kotlin handlers and KSP generates their Discord command metadata, registration, and dispatch code.

```kotlin
class GeneralCommands {
    @Command("ping")
    @Description("Replies with pong")
    suspend fun ping(context: CommandContext) {
        context.respond("Pong!")
    }
}
```

Generated modules are loaded through `ServiceLoader`. There is no reflective package scan or handwritten command registry.

Current release: [v0.1.0](https://github.com/NightmarePog/kord-annotations/releases/tag/v0.1.0). Requires Java 21.

## Install

Artifacts are published to GitHub Packages. GitHub requires authentication for public Maven packages, so create a classic personal access token with `read:packages` and add it to `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_PERSONAL_ACCESS_TOKEN
```

The following is configuration for the project using Kord Annotations, not this repository. Add the package repository to both plugin and dependency resolution:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://maven.pkg.github.com/nightmarepog/kord-annotations") {
            credentials {
                username = providers.gradleProperty("gpr.user").get()
                password = providers.gradleProperty("gpr.key").get()
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.github.com/nightmarepog/kord-annotations") {
            credentials {
                username = providers.gradleProperty("gpr.user").get()
                password = providers.gradleProperty("gpr.key").get()
            }
        }
        mavenCentral()
    }
}
```

Apply the plugin in the consuming project's `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.nightmarepog.kord-annotations") version "0.1.0"
}
```

The plugin applies Kotlin/JVM and KSP, then adds matching versions of `kord-annotations-core` and `kord-annotations-processor`. Its defaults are sufficient for most projects. Generated names can be changed when needed:

```kotlin
kordAnnotations {
    moduleName.set("MyBotCommands")
    generatedPackage.set("com.example.bot.generated")
}
```

## Commands

The first handler parameter must be `CommandContext`. Every remaining parameter becomes a Discord option. Nullable parameters and parameters with Kotlin defaults are optional.

```kotlin
class ModerationCommands {
    @Command("warn")
    @Description("Warns a server member")
    @PrivateResponse
    @Cooldown(seconds = 10, per = CooldownScope.USER)
    suspend fun warn(
        context: CommandContext,
        @Description("Member to warn") member: Member,
        @Description("Reason shown to the member") reason: String = "No reason supplied",
    ) {
        context.respond("Warned ${member.displayName}: $reason")
    }
}
```

`@Description` values are literal Discord text, not translation keys. Supported option types include strings, integers, numbers, booleans, users and members, channels, roles, mentionables, and attachments. Use `@Range`, `@Length`, `@Choices`, and `@Autocomplete` to refine generated option metadata.

Annotating a class creates a slash-command root. Its annotated methods become subcommands:

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

`@UserCommand` and `@MessageCommand` generate Discord context-menu commands.

### Policies and checks

`@PrivateResponse`, `@BotDM`, and `@AvailableIn` control response visibility and availability. `@Cooldown`, `@Timeout`, `@NoTimeout`, `@LoadingResponse`, and `@NoLoadingResponse` control execution.

Handlers time out after 30 seconds by default. If a handler has not responded within two seconds, the runtime sends `Working…` and edits that response when the handler completes.

Attach reusable checks through a custom annotation:

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

### Domain types

Use `@ConvertWith` when a handler should receive a domain type instead of a Discord primitive:

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

Buttons, select menus, and modals use the same generated dispatch path:

```kotlin
class TicketComponents {
    @Button("ticket close")
    @PrivateResponse
    suspend fun close(context: ComponentContext) {
        context.respond("Ticket closed")
    }
}
```

`InMemoryComponentStateStore` creates random tokens bound to one caller for 15 minutes by default. Set `reusable = false` for single-use state.

## Runtime

Without Spring, supply every handler, converter, check, and autocomplete provider to `InstanceHandlerResolver`. Install listeners before logging in:

```kotlin
val runtime = KordAnnotations(
    modules = CommandModules.load(),
    handlerResolver = InstanceHandlerResolver(
        GeneralCommands(),
        DurationConverter(),
        AdminOnlyCheck(),
    ),
)

runtime.install(kord)
runtime.syncGlobalCommands(kord) // replaces the complete global command set
kord.login()
```

## Spring Boot

Add the Spring integration when handlers should be discovered as beans:

```kotlin
dependencies {
    implementation("io.github.nightmarepog:kord-annotations-spring:0.1.0")
}
```

With a `Kord` bean present, the integration installs listeners and synchronizes global commands during startup. Disable command synchronization with `kord-annotations.sync-global-commands=false`, or disable the integration with `kord-annotations.enabled=false`.

## Testing

Add the testkit to run generated commands without connecting to Discord:

```kotlin
dependencies {
    testImplementation("io.github.nightmarepog:kord-annotations-testkit:0.1.0")
}
```

```kotlin
val command = CommandModules.load().single().commands.single { it.descriptor.name == "ping" }
val result = CommandTestHarness(InstanceHandlerResolver(GeneralCommands())).execute(command)
assertEquals("Pong!", result.responses.single().content)
```

## Modules

| Artifact | Purpose |
| --- | --- |
| `kord-annotations-core` | Annotations, runtime, policies, and component state |
| `kord-annotations-processor` | KSP validation and code generation |
| `kord-annotations-gradle-plugin` | Kotlin/JVM, KSP, core, and processor setup |
| `kord-annotations-spring` | Spring Boot discovery and auto-configuration |
| `kord-annotations-help` | Optional generated-command help catalog |
| `kord-annotations-testkit` | In-memory command execution and response recording |

## License

Kord Annotations is available under the [Mozilla Public License 2.0](LICENSE).
