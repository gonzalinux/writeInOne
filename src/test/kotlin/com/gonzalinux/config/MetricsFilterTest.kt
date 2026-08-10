package com.gonzalinux.config

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import org.springframework.web.util.pattern.PathPatternParser
import reactor.core.publisher.Mono

class MetricsFilterTest {

    private val registry = SimpleMeterRegistry()
    private val filter = MetricsFilter(registry)
    private val parser = PathPatternParser()

    private fun exchangeFor(path: String, status: HttpStatus? = HttpStatus.OK): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.get(path)).also { exchange ->
            status?.let { exchange.response.setStatusCode(it) }
        }

    private fun run(exchange: ServerWebExchange, result: Mono<Void> = Mono.empty()) {
        val chain = WebFilterChain { result }
        filter.filter(exchange, chain).onErrorComplete().block()
    }

    /** Path tags recorded on the duration histogram, in insertion order. */
    private fun recordedPaths(): List<String> =
        registry.find("http_request_duration_milliseconds").summaries().map { it.id.getTag("path")!! }

    @Test
    fun `matched route is tagged with its pattern, not the concrete path`() {
        val exchange = exchangeFor("/sites/42/posts/7")
        exchange.attributes[RouterFunctions.MATCHING_PATTERN_ATTRIBUTE] =
            parser.parse("/sites/{siteId}/posts/{postId}")

        run(exchange)

        assertEquals(listOf("/sites/{siteId}/posts/{postId}"), recordedPaths())
    }

    @Test
    fun `unrouted path is tagged unknown`() {
        run(exchangeFor("/.env", status = null), Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))

        assertEquals(listOf(UNKNOWN_PATH), recordedPaths())
    }

    @Test
    fun `a scan over many paths produces a single series`() {
        listOf("/.env", "/wp-admin", "/.git/config", "/phpmyadmin/index.php").forEach {
            run(exchangeFor(it, status = null), Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))
        }

        assertEquals(listOf(UNKNOWN_PATH), recordedPaths())
        assertEquals(4.0, registry.find("http_request_duration_milliseconds").summary()!!.count().toDouble())
    }

    @Test
    fun `served static resource keeps its raw path`() {
        val exchange = exchangeFor("/css/blog.css")
        exchange.attributes[HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE] = parser.parse("/**")

        run(exchange)

        assertEquals(listOf("/css/blog.css"), recordedPaths())
    }

    @Test
    fun `missing static resource is tagged unknown`() {
        val exchange = exchangeFor("/backup.sql", status = null)
        exchange.attributes[HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE] = parser.parse("/**")

        run(exchange, Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))

        assertEquals(listOf(UNKNOWN_PATH), recordedPaths())
    }

    @Test
    fun `metrics endpoint is not recorded`() {
        run(exchangeFor("/metrics"))

        assertEquals(emptyList<String>(), recordedPaths())
    }
}
