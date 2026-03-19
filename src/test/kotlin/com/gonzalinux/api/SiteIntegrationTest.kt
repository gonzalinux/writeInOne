package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
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
        val cookies = webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf(
                "email" to "sitetest@integrationtest.com",
                "displayName" to "Test User",
                "password" to "password123"
            ))
            .exchange()
            .returnResult(String::class.java)
            .responseCookies
        accessTokenCookie = cookies.getFirst(ACCESS_TOKEN_COOKIE)?.value ?: ""
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM users WHERE email LIKE '%@integrationtest.com'").then().block()
    }

    @Test
    fun `create site returns 200 with valid request`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf("name" to "Test Blog", "domain" to "testblog.example.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Test Blog")
            .jsonPath("$.domain").isEqualTo("testblog.example.com")
            .jsonPath("$.id").isNumber
    }

    @Test
    fun `create site returns 400 when domain is invalid`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf("name" to "Test Blog", "domain" to "INVALID DOMAIN"))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `create site returns 400 when name is blank`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf("name" to "", "domain" to "valid.example.com"))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `create site returns 409 when domain is already taken`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf("name" to "Blog 1", "domain" to "duplicate.example.com"))
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf("name" to "Blog 2", "domain" to "duplicate.example.com"))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `create site returns 401 when not authenticated`() {
        webTestClient.post().uri("/sites/")
            .bodyValue(mapOf("name" to "Test Blog", "domain" to "unauth.example.com"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `list sites returns 200 and includes created site`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
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
    fun `get site returns 404 when not found`() {
        webTestClient.get().uri("/sites/999999")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `update site returns 200 with updated name`() {
        val siteId = createSite("Old Name", "updateblog.example.com")

        webTestClient.put().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
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

        webTestClient.get().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `delete site returns 404 when site not found`() {
        webTestClient.delete().uri("/sites/999999")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isNotFound
    }

    @Suppress("UNCHECKED_CAST")
    private fun createSite(name: String, domain: String): Long {
        val body = webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .bodyValue(mapOf("name" to name, "domain" to domain))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody!!
        return (body["id"] as Number).toLong()
    }
}
