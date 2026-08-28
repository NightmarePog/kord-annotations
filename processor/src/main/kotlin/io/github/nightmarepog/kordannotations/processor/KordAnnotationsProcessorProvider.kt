package io.github.nightmarepog.kordannotations.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/** Creates the KSP processor used by the Kord Annotations Gradle plugin. */
class KordAnnotationsProcessorProvider : SymbolProcessorProvider {
    /** Creates a processor from the current KSP [environment]. */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KordAnnotationsProcessor(environment.codeGenerator, environment.logger, environment.options)
}
