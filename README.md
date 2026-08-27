# Kord Annotations

Annotation-driven Discord application commands and components for [Kord](https://github.com/kordlib/kord), generated at compile time with KSP.

```kotlin
class GeneralCommands {
    @Command("ping")
    @Description("Replies with pong")
    suspend fun ping(context: CommandContext) {
        context.respond("Pong!")
    }
}
```

There is no reflective package scan and no handwritten command registry. KSP validates each handler and generates a `CommandModule` plus its service entry.

## Modules

- `kord-annotations-core` — annotations, runtime, Kord adapter, localization, policies, and component state
- `kord-annotations-processor` — KSP validation and invocation generation
- `kord-annotations-spring` — Spring Boot handler discovery and auto-configuration
- `kord-annotations-gradle-plugin` — one-plugin setup and typed translation-key generation
- `kord-annotations-help` — optional help catalog
- `kord-annotations-testkit` — in-memory response controller and command harness

The current development version is `0.1.0-SNAPSHOT`, group `io.github.nightmarepog`, and targets Java 21, Kotlin 2.4.10, KSP 2.3.10, and Kord 0.18.1.

## Setup

Until the artifacts are published, include this checkout as a plugin build and use its build Maven repository:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("../kord-annotations")
    repositories { gradlePluginPortal(); mavenCentral() }
}

dependencyResolutionManagement {
    repositories {
        maven("../kord-annotations/build/repository")
        mavenCentral()
    }
}
```

Publish development artifacts once:

```shell
./gradlew publishAllPublicationsToBuildRepositoryRepository
```

Apply the consumer plugin:

```kotlin
plugins {
    id("io.github.nightmarepog.kord-annotations")
}

kordAnnotations {
    moduleName.set("MyBotCommands")
    generatedPackage.set("com.example.bot.generated")
}
```

The plugin applies Kotlin/JVM and KSP and adds the core and processor dependencies.

## Commands and inferred options

The first parameter is `CommandContext`. Remaining parameters become Discord options; nullable parameters and parameters with Kotlin defaults are optional.

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

Supported inferred types are strings, integers, numbers, booleans, users/members, resolved channels, roles, mentionable entities, and attachments. `@Range`, `@Length`, `@Choices`, and `@Autocomplete` configure their Discord option metadata.

A class-level command creates a slash-command root and its annotated methods become subcommands:

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

## Typed domain options

Use a converter when the handler should receive a domain type instead of a Discord primitive:

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

## Checks, policies, and failures

Custom annotations can carry reusable checks without coupling the framework to an application's permission model:

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@CheckedBy(AdminOnlyCheck::class)
annotation class AdminOnly

class AdminOnlyCheck : AnnotationCheck<AdminOnly> {
    override suspend fun check(annotation: AdminOnly, context: CommandContext): CheckResult =
        if (isAdmin(context.identity.userId)) CheckResult.Allowed
        else CheckResult.Denied(CommandFailure("errors.adminOnly"))
}
```

Handlers may also use `@PrivateResponse`, `@BotDM`, `@AvailableIn`, `@Cooldown`, `@Timeout`, `@NoTimeout`, `@LoadingResponse`, `@NoLoadingResponse`, and `@Observed`. The default timeout is 30 seconds; the default localized loading response appears after two seconds. A handler response edits that loading response instead of sending an invalid second initial response.

## Components

Buttons, select menus, and modals use the same generated owner resolution:

```kotlin
class TicketComponents {
    @Button("ticket.close")
    @PrivateResponse
    suspend fun close(context: ComponentContext) {
        context.respond("Ticket closed")
    }
}
```

`InMemoryComponentStateStore` creates cryptographically random state tokens. State is caller-bound, expires after 15 minutes by default, and is reusable unless `reusable = false` is requested.

## Translations

Put YAML files in `src/main/resources/translations`. The fallback file is `en.yml` by default:

```yaml
commands:
  ping:
    description: "Replies with pong"
```

The Gradle plugin generates compile-time constants:

```kotlin
@Command("ping")
@Description(Translations.CommandsPingDescription)
suspend fun ping(context: CommandContext) = context.respond("Pong!")
```

Translation values support ICU message syntax, including plurals. Missing keys in non-fallback locale files fail generation. Runtime lookup uses the interaction locale, language fallback, then English.

## Runtime

Without Spring, provide handler and extension instances explicitly, install listeners before login, and optionally sync global commands:

```kotlin
val modules = CommandModules.load()
val runtime = KordAnnotations(
    modules = modules,
    handlerResolver = InstanceHandlerResolver(
        GeneralCommands(),
        DurationConverter(),
        AdminOnlyCheck(),
    ),
    translations = DefaultTranslations.load(),
)

runtime.install(kord)
runtime.syncGlobalCommands(kord) // authoritative: stale global commands are removed
kord.login()
```

Spring Boot registers generated handler, converter, autocomplete-provider, and check owner types as beans. When a `Kord` bean exists, it installs listeners and authoritatively syncs global commands by default:

```kotlin
dependencies {
    implementation("io.github.nightmarepog:kord-annotations-spring:0.1.0-SNAPSHOT")
}
```

Disable startup sync with `kord-annotations.sync-global-commands=false`.

## Testing

`CommandTestHarness` executes the same generated invocation and policy pipeline with an in-memory response controller:

```kotlin
val command = CommandModules.load().single().commands.single { it.descriptor.name == "ping" }
val result = CommandTestHarness(InstanceHandlerResolver(GeneralCommands())).execute(command)
assertEquals("Pong!", result.responses.single().content)
```
