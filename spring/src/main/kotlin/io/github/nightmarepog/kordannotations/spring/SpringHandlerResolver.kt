package io.github.nightmarepog.kordannotations.spring

import io.github.nightmarepog.kordannotations.HandlerResolver
import org.springframework.context.ApplicationContext
import kotlin.reflect.KClass

class SpringHandlerResolver(private val applicationContext: ApplicationContext) : HandlerResolver {
    override fun resolve(type: KClass<*>): Any = applicationContext.getBean(type.java)
}
