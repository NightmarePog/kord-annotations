package io.github.nightmarepog.kordannotations.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class KordAnnotationsExtension @Inject constructor(objects: ObjectFactory) {
    val generatedPackage: Property<String> = objects.property(String::class.java)
        .convention("io.github.nightmarepog.kordannotations.generated")
    val moduleName: Property<String> = objects.property(String::class.java).convention("GeneratedKordAnnotationsModule")
}
