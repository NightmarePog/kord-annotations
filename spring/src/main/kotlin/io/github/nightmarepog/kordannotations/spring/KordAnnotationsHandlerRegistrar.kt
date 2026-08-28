package io.github.nightmarepog.kordannotations.spring

import io.github.nightmarepog.kordannotations.CommandModules
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.type.AnnotationMetadata

/** Registers generated handler and extension types as Spring beans when absent. */
class KordAnnotationsHandlerRegistrar : ImportBeanDefinitionRegistrar {
    /** Registers every generated owner, converter, autocomplete provider, and check. */
    override fun registerBeanDefinitions(importingClassMetadata: AnnotationMetadata, registry: BeanDefinitionRegistry) {
        CommandModules.load().flatMap { module ->
            module.commands.map { it.ownerType } +
                module.components.map { it.ownerType } +
                module.commands.flatMap { command -> command.descriptor.options.mapNotNull { it.autocompleteProvider } } +
                module.commands.flatMap { command -> command.descriptor.options.mapNotNull { it.converterProvider } } +
                module.commands.flatMap { command -> command.descriptor.checks.map { it.checkerType } }
        }.distinct().forEach { ownerType ->
            val ownerClass = ownerType.java
            val beanName = ownerClass.name
            val alreadyRegistered = registry.beanDefinitionNames.any { existingName ->
                registry.getBeanDefinition(existingName).beanClassName == ownerClass.name
            }
            if (!alreadyRegistered && !registry.containsBeanDefinition(beanName)) {
                registry.registerBeanDefinition(beanName, RootBeanDefinition(ownerClass))
            }
        }
    }
}
