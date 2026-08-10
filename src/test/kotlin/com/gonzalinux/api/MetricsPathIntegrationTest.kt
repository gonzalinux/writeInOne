package com.gonzalinux.api

import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Guards the fix for the scanner incident: probes for `/.env`, `/wp-admin`, … must not each mint
 * their own `path` time series.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MetricsPathIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var registry: MeterRegistry

    lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setup() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    private fun pathTags(): Set<String> =
        registry.find("http_request_duration_milliseconds").summaries()
            .mapNotNull { it.id.getTag("path") }
            .toSet()

    /** The metric is recorded in `doFinally`, which may land just after the client sees the response. */
    private fun awaitPathTag(tag: String): Set<String> {
        val deadline = System.currentTimeMillis() + 2000
        var tags = pathTags()
        while (tag !in tags && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
            tags = pathTags()
        }
        return tags
    }

    @Test
    fun `scanned paths collapse into a single unknown series`() {
        val before = pathTags()

        listOf("/.env", "/wp-admin/setup-config.php", "/.git/config", "/phpmyadmin/", "/backup.sql")
            .forEach { webTestClient.get().uri(it).exchange().expectStatus().isNotFound }

        val added = awaitPathTag("unknown") - before
        assertEquals(setOf("unknown"), added)
    }

    @Test
    fun `matched routes are tagged with their route pattern`() {
        webTestClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"email":"nobody@integrationtest.com","password":"wrong"}""")
            .exchange()

        val tags = awaitPathTag("/auth/login")
        assertTrue("/auth/login" in tags, "Got: $tags")
    }

    @Test
    fun `nested routes report the full pattern, never the concrete ids`() {
        // Unauthenticated: rejected by JwtAuthFilter, but the route pattern is still what matched.
        webTestClient.get().uri("/sites/999/posts/888").exchange()

        val tags = awaitPathTag("/sites/{siteId}/posts/{postId}")
        assertTrue("/sites/{siteId}/posts/{postId}" in tags, "Got: $tags")
        assertTrue(tags.none { it.contains("999") }, "Concrete id leaked into the path tag: $tags")
    }

    @Test
    fun `served static resources keep their real path`() {
        webTestClient.get().uri("/css/blog.css").exchange().expectStatus().isOk

        val tags = awaitPathTag("/css/blog.css")
        assertTrue("/css/blog.css" in tags, "Got: $tags")
    }
}
