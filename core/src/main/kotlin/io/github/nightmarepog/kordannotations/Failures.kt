package io.github.nightmarepog.kordannotations

open class CommandFailure(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class MissingCommandOptionException(optionName: String) :
    CommandFailure("The required option $optionName is missing.")

class InvalidCommandOptionException(optionName: String, expected: String, actual: String) :
    CommandFailure("The option $optionName has the wrong type: expected $expected, but received $actual.")

class CommandOnCooldownException(retryAfterSeconds: Long) :
    CommandFailure("Try again in ${formatSeconds(retryAfterSeconds)}.")

class CommandTimedOutException(seconds: Int) :
    CommandFailure("The command timed out after ${formatSeconds(seconds.toLong())}.")

private fun formatSeconds(seconds: Long): String = "$seconds ${if (seconds == 1L) "second" else "seconds"}"
