# Kord Annotations

Kord Annotations uses KSP to generate registration and dispatch code for annotated [Kord](https://github.com/kordlib/kord) command and component handlers.

```kotlin
class GeneralCommands {
    @Command("ping")
    @Description("Replies with pong")
    suspend fun ping(context: CommandContext) {
        context.respond("Pong!")
    }
}
```

KSP validates each handler, then generates a `CommandModule` and service entry. At runtime, `ServiceLoader` finds the module; there is no reflective package scan or handwritten registry.

## Modules

- `kord-annotations-core` — annotations, runtime, Kord adapter, policies, and component state
- `kord-annotations-processor` — KSP validation and invocation generation
- `kord-annotations-spring` — Spring Boot handler discovery and auto-configuration
- `kord-annotations-gradle-plugin` — one-plugin Kotlin/JVM and KSP setup
- `kord-annotations-help` — optional help catalog
- `kord-annotations-testkit` — in-memory response controller and command harness

## Setup

Requires Java 21.

### GitHub Packages

Releases are published to this repository's private GitHub Packages registry. Add a GitHub username and a classic personal access token with `read:packages` and access to this repository to `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

Configure the authenticated repository for plugins and dependencies:

```kotlin
// settings.gradle.kts
pluginManagement {
    val githubPackagesUsername = providers.gradleProperty("gpr.user")
        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
    val githubPackagesToken = providers.gradleProperty("gpr.key")
        .orElse(providers.environmentVariable("GITHUB_TOKEN"))

    repositories {
        maven("https://maven.pkg.github.com/nightmarepog/kord-annotations") {
            credentials {
                username = githubPackagesUsername.orNull
                password = githubPackagesToken.orNull
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    val githubPackagesUsername = providers.gradleProperty("gpr.user")
        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
    val githubPackagesToken = providers.gradleProperty("gpr.key")
        .orElse(providers.environmentVariable("GITHUB_TOKEN"))

    repositories {
        maven("https://maven.pkg.github.com/nightmarepog/kord-annotations") {
            credentials {
                username = githubPackagesUsername.orNull
                password = githubPackagesToken.orNull
            }
        }
        mavenCentral()
    }
}
```

Apply the plugin using the release version:

```kotlin
plugins {
    id("io.github.nightmarepog.kord-annotations") version "0.1.0"
}

kordAnnotations {
    moduleName.set("MyBotCommands")
    generatedPackage.set("com.example.bot.generated")
}
```

The plugin applies Kotlin/JVM and KSP and adds matching versions of the core and processor dependencies.

A workflow in another repository cannot use its own `GITHUB_TOKEN` here by default. Grant that repository Actions access in the package settings, or give the workflow a classic personal access token through a secret.

### Local checkout

For local development, include this checkout as a plugin build and use its build Maven repository:

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

Apply the consumer plugin without a version:

```kotlin
plugins {
    id("io.github.nightmarepog.kord-annotations")
}

kordAnnotations {
    moduleName.set("MyBotCommands")
    generatedPackage.set("com.example.bot.generated")
}
```

## Releasing

Run the `Release` workflow from `main` and enter a `MAJOR.MINOR.PATCH` version without a `v` prefix:

```shell
gh workflow run release.yml --ref main -f version=0.1.0
```

The workflow builds and tests the selected `main` commit, publishes all modules and the Gradle plugin marker to GitHub Packages, then creates the matching annotated tag and GitHub Release. The release includes an archived Maven repository and SHA-256 checksum. The version input controls every artifact and the generated `vMAJOR.MINOR.PATCH` tag.

The job uses the `release` environment for deployment tracking. GitHub does not add approval rules when it creates that environment, so configure required reviewers or other protection rules in the repository environment settings if releases should require approval.

Do not create release tags manually. If release creation fails after the workflow pushes its tag, rerun the original workflow run; it will skip the existing package upload and repair the Release. GitHub Packages publication is not transactional, so if an upload fails partway through before the tag is created, delete that incomplete version from the affected packages before rerunning.

## Commands and inferred options

The first parameter is `CommandContext`. Remaining parameters become Discord options; nullable parameters and parameters with Kotlin defaults are optional.

`@Description` values are literal text sent to Discord, not translation keys.

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

`@CheckedBy` attaches a reusable check to a custom annotation:

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

`@PrivateResponse`, `@BotDM`, and `@AvailableIn` control where and how the runtime responds. `@Cooldown`, `@Timeout`, `@NoTimeout`, `@LoadingResponse`, and `@NoLoadingResponse` control execution timing. By default, a handler times out after 30 seconds. If it has not responded within two seconds, the runtime sends `Working…`; the eventual response edits that message.

## Components

`@Button`, `@SelectMenu`, and `@Modal` handlers use the same generated dispatch as commands:

```kotlin
class TicketComponents {
    @Button("ticket.close")
    @PrivateResponse
    suspend fun close(context: ComponentContext) {
        context.respond("Ticket closed")
    }
}
```

`InMemoryComponentStateStore` uses cryptographically random tokens bound to one caller for 15 minutes by default. Set `reusable = false` for single-use tokens.

## Runtime

Without Spring, pass handlers, converters, checks, and autocomplete providers to `InstanceHandlerResolver`. Install listeners before login; global command sync is optional:

```kotlin
val modules = CommandModules.load()
val runtime = KordAnnotations(
    modules = modules,
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

Spring Boot registers the handlers, converters, autocomplete providers, and checks referenced by generated modules as beans. With a `Kord` bean and the default settings, it installs listeners and replaces the global command set at startup:

```kotlin
dependencies {
    implementation("io.github.nightmarepog:kord-annotations-spring:0.1.0")
}
```

Disable startup sync with `kord-annotations.sync-global-commands=false`.

## Testing

`CommandTestHarness` runs generated commands through the normal execution path without connecting to Discord:

```kotlin
val command = CommandModules.load().single().commands.single { it.descriptor.name == "ping" }
val result = CommandTestHarness(InstanceHandlerResolver(GeneralCommands())).execute(command)
assertEquals("Pong!", result.responses.single().content)
```
