package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var db: DatabaseClient

    private var accessTokenCookie: String = ""
    private var siteId: Long = 0L
    private lateinit var testEmail: String

    @BeforeEach
    fun setup() {
        val ts = System.currentTimeMillis()
        testEmail = "admintest-$ts@integrationtest.com"
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        val result = webTestClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to testEmail, "displayName" to "Admin Tester", "password" to "password123"))
            .exchange()
            .expectBody(Map::class.java)
            .returnResult()
        accessTokenCookie = result.responseCookies.getFirst(ACCESS_TOKEN_COOKIE)?.value ?: ""

        siteId = createSite("Admin Test Site", "admintest-$ts.example.com")
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM users WHERE email LIKE '%@integrationtest.com'").then().block()
    }

    // ── AdminHostFilter ──────────────────────────────────────────────────────

    @Test
    fun `admin pages are accessible on localhost`() {
        // Host header will be localhost:{port}, which strips to "localhost"
        webTestClient.get().uri("/admin/login")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
    }

    @Test
    fun `admin pages return 404 when accessed from a foreign domain`() {
        webTestClient.get().uri("/admin/login")
            .header("X-Site-Host", "someotherblog.com")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `admin index returns 404 when accessed from a foreign domain`() {
        webTestClient.get().uri("/admin")
            .header("X-Site-Host", "someotherblog.com")
            .exchange()
            .expectStatus().isNotFound
    }

    // ── Preview route ────────────────────────────────────────────────────────

    @Test
    fun `preview redirects to login when unauthenticated`() {
        val postId = createPost("Preview Test", "Some content")
        val slug = getSlug(postId)

        webTestClient.get().uri("/admin/preview/$siteId/en/$slug")
            .exchange()
            .expectStatus().isSeeOther
            .expectHeader().location("/admin/login")
    }

    @Test
    fun `preview returns HTML for authenticated owner with published post`() {
        val postId = createPost("Preview Test", "Some content")
        publishPost(postId)
        val slug = getSlug(postId)

        webTestClient.get().uri("/admin/preview/$siteId/en/$slug")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
    }

    @Test
    fun `preview returns HTML for authenticated owner with draft post`() {
        val postId = createPost("Draft Preview", "Draft content")
        val slug = getSlug(postId)

        webTestClient.get().uri("/admin/preview/$siteId/en/$slug")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
    }

    @Test
    fun `preview returns 404 when accessed from a foreign domain`() {
        val postId = createPost("Preview Test", "Some content")
        publishPost(postId)
        val slug = getSlug(postId)

        webTestClient.get().uri("/admin/preview/$siteId/en/$slug")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .header("X-Site-Host", "someotherblog.com")
            .exchange()
            .expectStatus().isNotFound
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun createSite(name: String, domain: String): Long {
        val body = webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name, "domain" to domain))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        return (body["id"] as Number).toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun createPost(title: String, body: String): Long {
        val response = webTestClient.post().uri("/sites/$siteId/posts/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("translations" to mapOf("en" to mapOf("title" to title, "body" to body))))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        return ((response["post"] as Map<*, *>)["id"] as Number).toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getSlug(postId: Long): String {
        val response = webTestClient.get().uri("/sites/$siteId/posts/$postId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        val translations = response["translations"] as List<Map<*, *>>
        return translations.first()["slug"] as String
    }

    private fun publishPost(postId: Long) {
        webTestClient.post().uri("/sites/$siteId/posts/$postId/publish")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
    }
}
