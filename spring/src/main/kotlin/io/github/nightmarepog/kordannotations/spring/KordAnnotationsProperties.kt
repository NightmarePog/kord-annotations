package io.github.nightmarepog.kordannotations.spring

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("kord-annotations")
class KordAnnotationsProperties {
    /** Install Kord listeners and synchronize commands during Spring startup. */
    var enabled: Boolean = true
    /** Replace all global application commands during startup. */
    var syncGlobalCommands: Boolean = true
    var maximumSyncAttempts: Int = 3
}
