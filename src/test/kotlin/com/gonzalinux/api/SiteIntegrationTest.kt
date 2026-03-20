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
    private lateinit var testEmail: String
    private var ts: Long = 0L

    @BeforeEach
    fun setup() {
        ts = System.currentTimeMillis()
        testEmail = "sitetest-$ts@integrationtest.com"
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        val result = webTestClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf(
                "email" to testEmail,
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
        db.sql("DELETE FROM sites WHERE user_id IN (SELECT id FROM users WHERE email = :email)")
            .bind("email", testEmail).then().block()
        db.sql("DELETE FROM users WHERE email = :email")
            .bind("email", testEmail).then().block()
    }

    @Test
    fun `create site returns 200 with valid request`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Test Blog", "domain" to "testblog-$ts.example.com"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Test Blog")
            .jsonPath("$.domain").isEqualTo("testblog-$ts.example.com")
            .jsonPath("$.id").isNumber
    }

    @Test
    fun `create site returns 409 when domain is already taken`() {
        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Blog 1", "domain" to "duplicate-$ts.example.com"))
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Blog 2", "domain" to "duplicate-$ts.example.com"))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `create site returns 401 when not authenticated`() {
        webTestClient.post().uri("/sites/")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Test Blog", "domain" to "unauth-$ts.example.com"))
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
            .bodyValue(mapOf("name" to "My Blog", "domain" to "myblog-$ts.example.com"))
            .exchange()
            .expectStatus().isOk

        webTestClient.get().uri("/sites/")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].domain").isEqualTo("myblog-$ts.example.com")
    }

    @Test
    fun `get site returns 200 when found`() {
        val siteId = createSite("Get Blog", "getblog-$ts.example.com")

        webTestClient.get().uri("/sites/$siteId")
            .cookie(ACCESS_TOKEN_COOKIE, accessTokenCookie)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(siteId)
            .jsonPath("$.domain").isEqualTo("getblog-$ts.example.com")
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
        val siteId = createSite("Old Name", "updateblog-$ts.example.com")

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
        val siteId = createSite("Delete Me", "deleteblog-$ts.example.com")

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
