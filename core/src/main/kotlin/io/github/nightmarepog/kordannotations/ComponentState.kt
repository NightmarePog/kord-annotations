package io.github.nightmarepog.kordannotations

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

public sealed interface ComponentStateResult<out T : Any> {
    public data class Found<T : Any>(public val value: T) : ComponentStateResult<T>
    public data object Missing : ComponentStateResult<Nothing>
    public data object Expired : ComponentStateResult<Nothing>
    public data object WrongUser : ComponentStateResult<Nothing>
}

public interface ComponentStateStore {
    public fun <T : Any> create(
        value: T,
        ownerUserId: String,
        lifetime: Duration = Duration.ofMinutes(15),
        reusable: Boolean = true,
    ): String

    public fun <T : Any> consume(token: String, userId: String, type: Class<T>): ComponentStateResult<T>
}

public class InMemoryComponentStateStore(
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) : ComponentStateStore {
    private data class Entry(val value: Any, val ownerUserId: String, val expiresAt: Instant, val reusable: Boolean)
    private val entries = ConcurrentHashMap<String, Entry>()

    override fun <T : Any> create(value: T, ownerUserId: String, lifetime: Duration, reusable: Boolean): String {
        val tokenBytes = ByteArray(18).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        entries[token] = Entry(value, ownerUserId, clock.instant().plus(lifetime), reusable)
        return token
    }

    override fun <T : Any> consume(token: String, userId: String, type: Class<T>): ComponentStateResult<T> {
        val entry = entries[token] ?: return ComponentStateResult.Missing
        if (entry.expiresAt <= clock.instant()) {
            entries.remove(token, entry)
            return ComponentStateResult.Expired
        }
        if (entry.ownerUserId != userId) return ComponentStateResult.WrongUser
        if (!type.isInstance(entry.value)) return ComponentStateResult.Missing
        if (!entry.reusable) entries.remove(token, entry)
        @Suppress("UNCHECKED_CAST")
        return ComponentStateResult.Found(entry.value as T)
    }
}
