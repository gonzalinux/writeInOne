package com.gonzalinux.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import org.springframework.web.util.pattern.PathPattern
import reactor.core.publisher.Mono
import reactor.core.publisher.SignalType

private val logger = KotlinLogging.logger {}

/** Tag value used for requests that never reached a route, so scanners cannot invent new series. */
const val UNKNOWN_PATH = "unknown"

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class MetricsFilter(private val registry: MeterRegistry) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val start = System.currentTimeMillis()
        val req = exchange.request
        return Mono.deferContextual { _ ->
            val rawPath = exchange.request.path.value()
            if (!rawPath.startsWith("/metrics"))
                logger.info { "${req.method} ${req.uri.path}" }
            chain.filter(exchange).doFinally { signal ->
                if (rawPath.startsWith("/metrics")) return@doFinally

                val status = exchange.response.statusCode?.value() ?: 0
                val method = exchange.request.method.name()
                val elapsed = System.currentTimeMillis() - start
                val path = metricPath(exchange, signal, status)

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

    /**
     * Resolves the `path` tag to something with bounded cardinality.
     *
     * The raw request path must never be used as a tag: a scanner walking `/.env`, `/wp-admin`, …
     * would mint one time series per probe and flood Prometheus. Instead we report the *route
     * pattern* the request matched (`/sites/{siteId}/posts/{postId}`), which is a fixed set.
     *
     * Static resources all match the catch-all resource mapping, so their pattern says nothing
     * useful: for those we keep the raw path, but only when the resource was really served — a
     * miss errors out and is reported as [UNKNOWN_PATH] like any other unrouted request.
     */
    private fun metricPath(exchange: ServerWebExchange, signal: SignalType, status: Int): String {
        exchange.getAttribute<PathPattern>(RouterFunctions.MATCHING_PATTERN_ATTRIBUTE)
            ?.let { return it.patternString }

        val served = signal == SignalType.ON_COMPLETE && status in 200..399
        val mapped = exchange.getAttribute<Any>(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) != null
        return if (served && mapped) exchange.request.path.value() else UNKNOWN_PATH
    }
}
