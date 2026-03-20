package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.api.AuthHandler.Companion.REFRESH_TOKEN_COOKIE
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var db: DatabaseClient

    @BeforeEach
    fun setup() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM users WHERE email LIKE '%@integrationtest.com'").then().block()
    }

    @Test
    fun `register returns 200 and sets cookies`() {
        webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf(
                "email" to "user@integrationtest.com",
                "displayName" to "Test User",
                "password" to "password123"
            ))
            .exchange()
            .expectStatus().isOk
            .expectCookie().exists(ACCESS_TOKEN_COOKIE)
            .expectCookie().exists(REFRESH_TOKEN_COOKIE)
            .expectCookie().httpOnly(ACCESS_TOKEN_COOKIE, true)
            .expectCookie().httpOnly(REFRESH_TOKEN_COOKIE, true)
    }

    @Test
    fun `register returns 409 when email already taken`() {
        val body = mapOf(
            "email" to "duplicate@integrationtest.com",
            "displayName" to "Test User",
            "password" to "password123"
        )

        webTestClient.post().uri("/auth/register").bodyValue(body).exchange()
        webTestClient.post().uri("/auth/register").bodyValue(body).exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `register returns 400 with invalid email`() {
        webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf(
                "email" to "not-an-email",
                "displayName" to "Test User",
                "password" to "password123"
            ))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `register returns 400 with short password`() {
        webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf(
                "email" to "user@integrationtest.com",
                "displayName" to "Test User",
                "password" to "123"
            ))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `login returns 200 and sets cookies with valid credentials`() {
        webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf(
                "email" to "login@integrationtest.com",
                "displayName" to "Test User",
                "password" to "password123"
            ))
            .exchange()

        webTestClient.post().uri("/auth/login")
            .bodyValue(mapOf(
                "email" to "login@integrationtest.com",
                "password" to "password123"
            ))
            .exchange()
            .expectStatus().isOk
            .expectCookie().exists(ACCESS_TOKEN_COOKIE)
            .expectCookie().exists(REFRESH_TOKEN_COOKIE)
    }

    @Test
    fun `login returns 401 with wrong password`() {
        webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf(
                "email" to "wrongpass@integrationtest.com",
                "displayName" to "Test User",
                "password" to "password123"
            ))
            .exchange()

        webTestClient.post().uri("/auth/login")
            .bodyValue(mapOf(
                "email" to "wrongpass@integrationtest.com",
                "password" to "wrongpassword"
            ))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `login returns 401 with unknown email`() {
        webTestClient.post().uri("/auth/login")
            .bodyValue(mapOf(
                "email" to "ghost@integrationtest.com",
                "password" to "password123"
            ))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `logout returns 200 and clears cookies`() {
        val cookies = webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf(
                "email" to "logout@integrationtest.com",
                "displayName" to "Test User",
                "password" to "password123"
            ))
            .exchange()
            .returnResult(String::class.java)
            .responseCookies
        val refreshToken = cookies.getFirst(REFRESH_TOKEN_COOKIE)?.value ?: ""

        webTestClient.post().uri("/auth/logout")
            .cookie(REFRESH_TOKEN_COOKIE, refreshToken)
            .exchange()
            .expectStatus().isOk
            .expectCookie().maxAge(ACCESS_TOKEN_COOKIE, Duration.ZERO)
            .expectCookie().maxAge(REFRESH_TOKEN_COOKIE, Duration.ZERO)
    }

    @Test
    fun `refresh returns 200 and rotates tokens`() {
        val cookies = webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf(
                "email" to "refresh@integrationtest.com",
                "displayName" to "Test User",
                "password" to "password123"
            ))
            .exchange()
            .returnResult(String::class.java)
            .responseCookies
        val refreshToken = cookies.getFirst(REFRESH_TOKEN_COOKIE)?.value ?: ""

        webTestClient.post().uri("/auth/refresh")
            .cookie(REFRESH_TOKEN_COOKIE, refreshToken)
            .exchange()
            .expectStatus().isOk
            .expectCookie().exists(ACCESS_TOKEN_COOKIE)
            .expectCookie().exists(REFRESH_TOKEN_COOKIE)
    }

    @Test
    fun `refresh returns 401 when no token provided`() {
        webTestClient.post().uri("/auth/refresh")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `refresh returns 401 with invalid token`() {
        webTestClient.post().uri("/auth/refresh")
            .cookie(REFRESH_TOKEN_COOKIE, "this-is-not-a-valid-token")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
