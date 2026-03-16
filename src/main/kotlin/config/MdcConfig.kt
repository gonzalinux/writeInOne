package com.gonzalinux.config

import com.gonzalinux.common.RequestContext
import com.gonzalinux.common.RequestContextHolder
import io.micrometer.context.ContextRegistry
import io.micrometer.context.ThreadLocalAccessor
import jakarta.annotation.PostConstruct
import org.slf4j.MDC
import org.springframework.context.annotation.Configuration

@Configuration
class MdcConfig {

    @PostConstruct
    fun registerMdcAccessor() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(RequestContextMdcAccessor())
    }
}

class RequestContextMdcAccessor : ThreadLocalAccessor<RequestContext> {

    override fun key(): Any = RequestContextHolder.CONTEXT_KEY

    override fun getValue(): RequestContext? = null

    override fun setValue(value: RequestContext) {
        MDC.put("requestId", value.requestId)
        MDC.put("userId", value.userId.toString())
    }

    override fun reset() {
        MDC.remove("requestId")
        MDC.remove("userId")
    }
}
