package com.gonzalinux.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class MetricsFilter(private val registry: MeterRegistry) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val start = System.currentTimeMillis()
        return chain.filter(exchange).doFinally {
            val path = exchange.getAttribute<Any>(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
                ?.toString() ?: exchange.request.path.value()

            if (path.startsWith("/actuator")) return@doFinally

            val status = exchange.response.statusCode?.value()?.toString() ?: "0"
            val method = exchange.request.method.name()
            val elapsed = (System.currentTimeMillis() - start).toDouble()

            Counter.builder("http.requests")
                .tag("status", status)
                .tag("path", path)
                .tag("method", method)
                .register(registry)
                .increment()

            DistributionSummary.builder("http_request_duration_milliseconds")
                .tag("path", path)
                .tag("method", method)
                .serviceLevelObjectives(5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0)
                .register(registry)
                .record(elapsed)
        }
    }
}
