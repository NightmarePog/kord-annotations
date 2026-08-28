package io.github.nightmarepog.kordannotations

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Records successful uses and reports active cooldowns by key. */
interface CooldownStore {
    /** Returns the rounded-up remaining cooldown in seconds, or null when the key is ready. */
    fun remainingSeconds(key: String, durationSeconds: Int): Long?

    /** Records a successful use of [key] at the current time. */
    fun record(key: String)
}

/** A thread-safe, process-local [CooldownStore]. */
class InMemoryCooldownStore(private val clock: Clock = Clock.systemUTC()) : CooldownStore {
    private val uses = ConcurrentHashMap<String, Instant>()

    override fun remainingSeconds(key: String, durationSeconds: Int): Long? {
        val usedAt = uses[key] ?: return null
        val remainingMillis = durationSeconds * 1_000L - (clock.instant().toEpochMilli() - usedAt.toEpochMilli())
        return if (remainingMillis > 0) ceil(remainingMillis / 1_000.0).toLong() else null
    }

    override fun record(key: String) {
        uses[key] = clock.instant()
    }
}

/**
 * Executes generated commands with checks, cooldowns, loading responses, timeouts, and observers.
 *
 * [CommandFailure] instances are sent privately. Unexpected failures are observed and rethrown.
 */
class CommandExecutor(
    private val resolver: HandlerResolver,
    private val cooldowns: CooldownStore = InMemoryCooldownStore(),
    private val observers: List<CommandObserver> = emptyList(),
) {
    /** Executes [command] with [context]. */
    suspend fun execute(command: GeneratedCommand, context: CommandContext) {
        val startedAt = System.nanoTime()
        notify(CommandExecutionEvent.Started(command.descriptor))
        try {
            enforceChecks(command.descriptor, context)
            enforceCooldown(command.descriptor, context.identity)
            supervisorScope {
                val loading = startLoadingResponse(this, command.descriptor.execution, context)
                try {
                    val invocation: suspend () -> Unit = {
                        command.invoke(resolver.resolve(command.ownerType), context)
                    }
                    val timeout = command.descriptor.execution.timeoutSeconds
                    if (timeout == null) invocation() else withTimeout(timeout.seconds) { invocation() }
                    recordCooldown(command.descriptor, context.identity)
                } finally {
                    loading?.cancel()
                }
            }
            notify(CommandExecutionEvent.Succeeded(command.descriptor, elapsedMillis(startedAt)))
        } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
            val timeout = command.descriptor.execution.timeoutSeconds ?: 30
            val commandFailure = CommandTimedOutException(timeout)
            respondWithFailure(context, commandFailure)
            notify(CommandExecutionEvent.Failed(command.descriptor, elapsedMillis(startedAt), commandFailure))
        } catch (failure: CommandFailure) {
            respondWithFailure(context, failure)
            notify(CommandExecutionEvent.Failed(command.descriptor, elapsedMillis(startedAt), failure))
        } catch (failure: Throwable) {
            notify(CommandExecutionEvent.Failed(command.descriptor, elapsedMillis(startedAt), failure))
            throw failure
        }
    }

    private suspend fun enforceChecks(descriptor: CommandDescriptor, context: CommandContext) {
        descriptor.checks.forEach { generatedCheck ->
            @Suppress("UNCHECKED_CAST")
            val checker = resolver.resolve(generatedCheck.checkerType) as AnnotationCheck<Annotation>
            when (val result = checker.check(generatedCheck.annotation, context)) {
                CheckResult.Allowed -> Unit
                is CheckResult.Denied -> throw result.failure
            }
        }
    }

    private fun enforceCooldown(descriptor: CommandDescriptor, identity: InteractionIdentity) {
        val duration = descriptor.execution.cooldownSeconds ?: return
        cooldowns.remainingSeconds(cooldownKey(descriptor, identity), duration)?.let {
            throw CommandOnCooldownException(it)
        }
    }

    private fun recordCooldown(descriptor: CommandDescriptor, identity: InteractionIdentity) {
        if (descriptor.execution.cooldownSeconds != null) cooldowns.record(cooldownKey(descriptor, identity))
    }

    private fun cooldownKey(descriptor: CommandDescriptor, identity: InteractionIdentity): String {
        val scope = when (descriptor.execution.cooldownScope) {
            CooldownScope.USER -> identity.userId
            CooldownScope.CHANNEL -> identity.channelId ?: identity.userId
            CooldownScope.GUILD -> identity.guildId ?: identity.userId
            CooldownScope.GLOBAL -> "global"
        }
        return "${descriptor.parentName.orEmpty()}:${descriptor.name}:$scope"
    }

    private fun startLoadingResponse(scope: CoroutineScope, policy: ExecutionPolicy, context: CommandContext) =
        policy.loadingResponse?.let { response ->
            scope.launch {
                delay(policy.loadingResponseDelayMillis.milliseconds)
                if (!context.hasResponded) context.respond(response)
            }
        }

    private suspend fun respondWithFailure(context: CommandContext, failure: CommandFailure) {
        context.respond(failure.message, ReplyVisibility.PRIVATE)
    }

    private suspend fun notify(event: CommandExecutionEvent) {
        observers.forEach { observer -> runCatching { observer.observe(event) } }
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}

/** Executes generated components with cooldowns, loading responses, and timeouts. */
class ComponentExecutor(
    private val resolver: HandlerResolver,
    private val cooldowns: CooldownStore = InMemoryCooldownStore(),
) {
    /** Executes [component] with [context]. */
    suspend fun execute(component: GeneratedComponent, context: ComponentContext) {
        val policy = component.descriptor.execution
        try {
            enforceCooldown(component.descriptor, context.identity)
            supervisorScope {
                val loading = policy.loadingResponse?.let { response ->
                    launch {
                        delay(policy.loadingResponseDelayMillis.milliseconds)
                        if (!context.hasResponded) context.respond(response)
                    }
                }
                try {
                    val invocation: suspend () -> Unit = {
                        component.invoke(resolver.resolve(component.ownerType), context)
                    }
                    val timeout = policy.timeoutSeconds
                    if (timeout == null) invocation() else withTimeout(timeout.seconds) { invocation() }
                    recordCooldown(component.descriptor, context.identity)
                } finally {
                    loading?.cancel()
                }
            }
        } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
            val seconds = policy.timeoutSeconds ?: 30
            context.respond(CommandTimedOutException(seconds).message, ReplyVisibility.PRIVATE)
        } catch (failure: CommandFailure) {
            context.respond(failure.message, ReplyVisibility.PRIVATE)
        }
    }

    private fun enforceCooldown(descriptor: ComponentDescriptor, identity: InteractionIdentity) {
        val duration = descriptor.execution.cooldownSeconds ?: return
        cooldowns.remainingSeconds(cooldownKey(descriptor, identity), duration)?.let {
            throw CommandOnCooldownException(it)
        }
    }

    private fun recordCooldown(descriptor: ComponentDescriptor, identity: InteractionIdentity) {
        if (descriptor.execution.cooldownSeconds != null) cooldowns.record(cooldownKey(descriptor, identity))
    }

    private fun cooldownKey(descriptor: ComponentDescriptor, identity: InteractionIdentity): String {
        val scope = when (descriptor.execution.cooldownScope) {
            CooldownScope.USER -> identity.userId
            CooldownScope.CHANNEL -> identity.channelId ?: identity.userId
            CooldownScope.GUILD -> identity.guildId ?: identity.userId
            CooldownScope.GLOBAL -> "global"
        }
        return "component:${descriptor.id}:$scope"
    }
}
