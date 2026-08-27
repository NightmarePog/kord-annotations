package io.github.nightmarepog.kordannotations.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

public abstract class KordAnnotationsExtension @Inject constructor(objects: ObjectFactory) {
    public val translationDirectory: DirectoryProperty = objects.directoryProperty()
    public val fallbackLocale: Property<String> = objects.property(String::class.java).convention("en")
    public val generatedPackage: Property<String> = objects.property(String::class.java)
        .convention("io.github.nightmarepog.kordannotations.generated")
    public val translationObjectName: Property<String> = objects.property(String::class.java).convention("Translations")
    public val moduleName: Property<String> = objects.property(String::class.java).convention("GeneratedKordAnnotationsModule")
}
