package io.github.nightmarepog.kordannotations.spring

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("kord-annotations")
public class KordAnnotationsProperties {
    /** Install Kord listeners and synchronize commands during Spring startup. */
    public var enabled: Boolean = true
    /** Authoritatively replace global application commands during startup. */
    public var syncGlobalCommands: Boolean = true
    public var maximumSyncAttempts: Int = 3
}
