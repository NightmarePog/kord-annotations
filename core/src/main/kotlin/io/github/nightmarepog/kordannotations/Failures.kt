package io.github.nightmarepog.kordannotations

/**
 * A failure safe to show to the invoking user.
 *
 * Executors send the message privately and stop the current handler invocation.
 *
 * @property message Text safe to send to the invoking user.
 */
open class CommandFailure(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Indicates that a required command option was absent. */
class MissingCommandOptionException(optionName: String) :
    CommandFailure("The required option $optionName is missing.")

/** Indicates that a command option could not be read as its declared type. */
class InvalidCommandOptionException(optionName: String, expected: String, actual: String) :
    CommandFailure("The option $optionName has the wrong type: expected $expected, but received $actual.")

/** Indicates that a command or component is still on cooldown. */
class CommandOnCooldownException(retryAfterSeconds: Long) :
    CommandFailure("Try again in ${formatSeconds(retryAfterSeconds)}.")

/** Indicates that a handler exceeded its configured timeout. */
class CommandTimedOutException(seconds: Int) :
    CommandFailure("The command timed out after ${formatSeconds(seconds.toLong())}.")

private fun formatSeconds(seconds: Long): String = "$seconds ${if (seconds == 1L) "second" else "seconds"}"
