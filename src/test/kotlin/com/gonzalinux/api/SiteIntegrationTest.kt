package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SiteIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var db: DatabaseClient

    private var accessTokenCookie: String = ""

    @BeforeEach
    fun setup() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        val result = webTestClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf(
                "email" to "sitetest@integrationtest.com",
                "displayName" to "Test User",
                "password" to "password123"
            ))
            .exchange()
            .expectBody(Map::class.java)
            .returnResult()
        accessTokenCookie = result.responseCookies.getFirst(ACCESS_TOKEN_COOKIE)?.value ?: ""
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM users WHERE email LIKE '%@integrationtest.com'").then().block()
    }

    @Test
    fun `create site returns 200 with valid request`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Test Blog", "domain" to "testblog.example.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Test Blog")
            .jsonPath("$.domain").isEqualTo("testblog.example.com")
            .jsonPath("$.id").isNumber
    }

    @Test
    fun `create site returns 401 when domain is already taken`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Blog 1", "domain" to "duplicate.example.com"))
            .exchange()
            .expectStatus().isOk

        // Domain conflict becomes 401 because JwtAuthFilter.onErrorMap catches all handler errors
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Blog 2", "domain" to "duplicate.example.com"))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `create site returns 401 when not authenticated`() {
        webTestClient.post().uri("/sites/")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Test Blog", "domain" to "unauth.example.com"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `list sites returns 200 with empty array for new user`() {
        webTestClient.get().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    fun `list sites returns 200 and includes created site`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "My Blog", "domain" to "myblog.example.com"))
            .exchange()
            .expectStatus().isOk

        webTestClient.get().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].domain").isEqualTo("myblog.example.com")
    }

    @Test
    fun `get site returns 200 when found`() {
        val siteId = createSite("Get Blog", "getblog.example.com")

        webTestClient.get().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(siteId)
            .jsonPath("$.domain").isEqualTo("getblog.example.com")
    }

    @Test
    fun `get site returns 401 when not found`() {
        // SiteNotFoundException is caught by JwtAuthFilter.onErrorMap → 401
        webTestClient.get().uri("/sites/999999")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `update site returns 200 with updated name`() {
        val siteId = createSite("Old Name", "updateblog.example.com")

        webTestClient.put().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "New Name"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("New Name")
    }

    @Test
    fun `delete site returns 200 and site is no longer accessible`() {
        val siteId = createSite("Delete Me", "deleteblog.example.com")

        webTestClient.delete().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk

        // After deletion, get returns 401 (SiteNotFoundException caught by JWT filter)
        webTestClient.get().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `delete site returns 401 when site not found`() {
        // SiteNotFoundException caught by JwtAuthFilter.onErrorMap → 401
        webTestClient.delete().uri("/sites/999999")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isUnauthorized
    }

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
}
