package io.github.nightmarepog.kordannotations

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** The result of retrieving component state by token. */
sealed interface ComponentStateResult<out T : Any> {
    /**
     * Contains stored state.
     *
     * @property value Retrieved state value.
     */
    data class Found<T : Any>(val value: T) : ComponentStateResult<T>

    /** No state exists for the token or the stored value has a different type. */
    data object Missing : ComponentStateResult<Nothing>

    /** The token existed but its lifetime elapsed. */
    data object Expired : ComponentStateResult<Nothing>

    /** The token belongs to a different Discord user. */
    data object WrongUser : ComponentStateResult<Nothing>
}

/** Stores state referenced by compact component custom-ID tokens. */
interface ComponentStateStore {
    /**
     * Stores [value] for [ownerUserId] and returns a token suitable for a component custom ID.
     *
     * When [reusable] is false, the first successful [consume] removes the value.
     */
    fun <T : Any> create(
        value: T,
        ownerUserId: String,
        lifetime: Duration = Duration.ofMinutes(15),
        reusable: Boolean = true,
    ): String

    /** Retrieves state when [token], [userId], and [type] match the stored entry. */
    fun <T : Any> consume(token: String, userId: String, type: Class<T>): ComponentStateResult<T>
}

/** A thread-safe in-memory [ComponentStateStore] with 144-bit random tokens. */
class InMemoryComponentStateStore(
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
