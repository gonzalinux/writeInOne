package com.gonzalinux.config

import com.gonzalinux.common.RequestContextHolder
import com.gonzalinux.common.SiteContextHolder
import com.gonzalinux.domain.site.Site
import io.micrometer.context.ContextRegistry
import io.micrometer.context.ThreadLocalAccessor
import jakarta.annotation.PostConstruct
import org.slf4j.MDC
import org.springframework.context.annotation.Configuration

@Configuration
class MdcConfig {

    @PostConstruct
    fun registerMdcAccessors() {
        ContextRegistry.getInstance()
            .registerThreadLocalAccessor(RequestIdMdcAccessor())
            .registerThreadLocalAccessor(UserIdMdcAccessor())
            .registerThreadLocalAccessor(SiteContextMdcAccessor())
    }
}

class RequestIdMdcAccessor : ThreadLocalAccessor<String> {

    override fun key(): Any = RequestContextHolder.REQUEST_ID_KEY

    override fun getValue(): String? = MDC.get("requestId")

    override fun setValue(value: String) {
        MDC.put("requestId", value)
    }

    override fun restore() {
        MDC.remove("requestId")
    }
}

class UserIdMdcAccessor : ThreadLocalAccessor<Long> {

    override fun key(): Any = RequestContextHolder.USER_ID_KEY

    override fun getValue(): Long? = MDC.get("userId")?.toLongOrNull()

    override fun setValue(value: Long) {
        MDC.put("userId", value.toString())
    }

    override fun restore() {
        MDC.remove("userId")
    }
}

class SiteContextMdcAccessor : ThreadLocalAccessor<Site> {

    override fun key(): Any = SiteContextHolder.CONTEXT_KEY

    override fun getValue(): Site? = null

    override fun setValue(value: Site) {
        MDC.put("site", value.domain)
    }

    override fun restore() {
        MDC.remove("site")
    }
}
