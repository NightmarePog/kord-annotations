package io.github.nightmarepog.kordannotations.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/** Configures Kord Annotations code generation. */
abstract class KordAnnotationsExtension @Inject constructor(objects: ObjectFactory) {
    /** Package for the generated command module. */
    val generatedPackage: Property<String> = objects.property(String::class.java)
        .convention("io.github.nightmarepog.kordannotations.generated")

    /** Simple class name of the generated command module. */
    val moduleName: Property<String> = objects.property(String::class.java).convention("GeneratedKordAnnotationsModule")
}
