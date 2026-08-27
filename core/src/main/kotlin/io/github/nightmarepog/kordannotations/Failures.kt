package io.github.nightmarepog.kordannotations

public open class CommandFailure(
    public val translationKey: String,
    public val arguments: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(translationKey, cause)

public class MissingCommandOptionException(optionName: String) :
    CommandFailure("kordAnnotations.error.missingOption", mapOf("option" to optionName))

public class InvalidCommandOptionException(optionName: String, expected: String, actual: String) :
    CommandFailure(
        "kordAnnotations.error.invalidOption",
        mapOf("option" to optionName, "expected" to expected, "actual" to actual),
    )

public class CommandOnCooldownException(retryAfterSeconds: Long) :
    CommandFailure("kordAnnotations.error.cooldown", mapOf("seconds" to retryAfterSeconds))

public class CommandTimedOutException(seconds: Int) :
    CommandFailure("kordAnnotations.error.timeout", mapOf("seconds" to seconds))
