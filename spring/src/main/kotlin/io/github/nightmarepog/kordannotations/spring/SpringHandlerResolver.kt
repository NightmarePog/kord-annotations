package io.github.nightmarepog.kordannotations.spring

import io.github.nightmarepog.kordannotations.HandlerResolver
import org.springframework.context.ApplicationContext
import kotlin.reflect.KClass

/** Resolves handlers and extensions from a Spring [ApplicationContext]. */
class SpringHandlerResolver(private val applicationContext: ApplicationContext) : HandlerResolver {
    /** Returns the Spring bean assignable to [type]. */
    override fun resolve(type: KClass<*>): Any = applicationContext.getBean(type.java)
}
