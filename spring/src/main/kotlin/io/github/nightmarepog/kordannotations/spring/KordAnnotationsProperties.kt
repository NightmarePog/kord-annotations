package io.github.nightmarepog.kordannotations.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/** Spring Boot settings under the `kord-annotations` configuration prefix. */
@ConfigurationProperties("kord-annotations")
class KordAnnotationsProperties {
    /** Whether Spring startup installs Kord listeners and may synchronize commands. */
    var enabled: Boolean = true

    /** Replace all global application commands during startup. */
    var syncGlobalCommands: Boolean = true

    /** Maximum attempts when synchronizing global commands. */
    var maximumSyncAttempts: Int = 3
}
