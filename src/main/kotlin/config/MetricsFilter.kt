package com.gonzalinux.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class MetricsFilter(private val registry: MeterRegistry) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val start = System.currentTimeMillis()
        val req = exchange.request
        return Mono.deferContextual { _ ->
            val path = exchange.request.path.value()
            if (!path.startsWith("/metrics"))
                logger.info { "${req.method} ${req.uri.path}" }
            chain.filter(exchange).doFinally {
                if (path.startsWith("/metrics")) return@doFinally

                val status = exchange.response.statusCode?.value() ?: 0
                val method = exchange.request.method.name()
                val elapsed = System.currentTimeMillis() - start

                logger.info { "returned $status in ${elapsed}ms" }

                Counter.builder("http.requests")
                    .tag("status", status.toString())
                    .tag("path", path)
                    .tag("method", method)
                    .register(registry)
                    .increment()

                DistributionSummary.builder("http_request_duration_milliseconds")
                    .tag("path", path)
                    .tag("method", method)
                    .serviceLevelObjectives(5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0)
                    .register(registry)
                    .record(elapsed.toDouble())
            }
        }
    }
}
