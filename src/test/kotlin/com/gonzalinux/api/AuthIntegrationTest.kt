package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.api.AuthHandler.Companion.REFRESH_TOKEN_COOKIE
import com.gonzalinux.client.TestEmailClient
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
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var db: DatabaseClient

    @Autowired
    lateinit var emailClient: TestEmailClient

    @BeforeEach
    fun setup() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @AfterEach
    fun cleanup() {
        db.sql("DELETE FROM users WHERE email LIKE '%@integrationtest.com'").then().block()
        emailClient.clear()
    }

    private fun register(email: String, displayName: String = "Test User", password: String = "password123") =
        webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf("email" to email, "displayName" to displayName, "password" to password))
            .exchange()

    private fun verifyEmail(email: String) {
        val code = emailClient.getVerificationCode(email) ?: error("No verification code captured for $email")
        webTestClient.post().uri("/auth/verify-email")
            .bodyValue(mapOf("email" to email, "code" to code))
            .exchange()
            .expectStatus().isOk
    }

    private fun registerAndVerify(email: String, displayName: String = "Test User", password: String = "password123") {
        register(email, displayName, password)
        verifyEmail(email)
    }

    // --- register ---

    @Test
    fun `register returns 200 and sets cookies`() {
        register("user@integrationtest.com")
            .expectStatus().isOk
            .expectCookie().exists(ACCESS_TOKEN_COOKIE)
            .expectCookie().exists(REFRESH_TOKEN_COOKIE)
            .expectCookie().httpOnly(ACCESS_TOKEN_COOKIE, true)
            .expectCookie().httpOnly(REFRESH_TOKEN_COOKIE, true)
    }

    @Test
    fun `register returns 409 when verified email is already taken`() {
        registerAndVerify("duplicate@integrationtest.com")
        register("duplicate@integrationtest.com")
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `register with unverified email overwrites and returns fresh tokens`() {
        register("overwrite@integrationtest.com").expectStatus().isOk
        register("overwrite@integrationtest.com").expectStatus().isOk

        val count = db.sql("SELECT COUNT(*) FROM users WHERE email = 'overwrite@integrationtest.com'")
            .fetch().first().map { it["count"] as Long }.block()!!
        assert(count == 1L) { "Expected 1 user record, got $count" }
    }

    @Test
    fun `register returns 400 with invalid email`() {
        webTestClient.post().uri("/auth/register")
            .bodyValue(mapOf("email" to "not-an-email", "displayName" to "Test User", "password" to "password123"))
            .exchange()
            .expectStatus().isBadRequest
    }

    // --- email verification ---

    @Test
    fun `verify-email returns 200 with correct code`() {
        register("verify@integrationtest.com")
        val code = emailClient.getVerificationCode("verify@integrationtest.com")!!

        webTestClient.post().uri("/auth/verify-email")
            .bodyValue(mapOf("email" to "verify@integrationtest.com", "code" to code))
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `verify-email returns 400 with wrong code`() {
        register("verify-bad@integrationtest.com")

        webTestClient.post().uri("/auth/verify-email")
            .bodyValue(mapOf("email" to "verify-bad@integrationtest.com", "code" to "000000"))
            .exchange()
            .expectStatus().isBadRequest
    }

    // --- login ---

    @Test
    fun `login returns 401 for unverified user`() {
        register("unverified@integrationtest.com")

        webTestClient.post().uri("/auth/login")
            .bodyValue(mapOf("email" to "unverified@integrationtest.com", "password" to "password123"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `login returns 200 after email is verified`() {
        registerAndVerify("login@integrationtest.com")

        webTestClient.post().uri("/auth/login")
            .bodyValue(mapOf("email" to "login@integrationtest.com", "password" to "password123"))
            .exchange()
            .expectStatus().isOk
            .expectCookie().exists(ACCESS_TOKEN_COOKIE)
    }

    @Test
    fun `login returns 401 with wrong password`() {
        registerAndVerify("wrongpass@integrationtest.com")

        webTestClient.post().uri("/auth/login")
            .bodyValue(mapOf("email" to "wrongpass@integrationtest.com", "password" to "wrongpassword"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `login returns 401 with unknown email`() {
        webTestClient.post().uri("/auth/login")
            .bodyValue(mapOf("email" to "ghost@integrationtest.com", "password" to "password123"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    // --- forgot password / reset password ---

    @Test
    fun `forgot-password returns 200 for unknown email without leaking existence`() {
        webTestClient.post().uri("/auth/forgot-password")
            .bodyValue(mapOf("email" to "ghost@integrationtest.com"))
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `full password reset flow works`() {
        registerAndVerify("resetme@integrationtest.com")

        webTestClient.post().uri("/auth/forgot-password")
            .bodyValue(mapOf("email" to "resetme@integrationtest.com"))
            .exchange()
            .expectStatus().isOk

        val code = emailClient.getResetCode("resetme@integrationtest.com")!!

        webTestClient.post().uri("/auth/reset-password")
            .bodyValue(mapOf("email" to "resetme@integrationtest.com", "code" to code, "password" to "newpassword123"))
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/auth/login")
            .bodyValue(mapOf("email" to "resetme@integrationtest.com", "password" to "newpassword123"))
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `reset-password returns 400 with wrong code`() {
        registerAndVerify("resetbad@integrationtest.com")

        webTestClient.post().uri("/auth/forgot-password")
            .bodyValue(mapOf("email" to "resetbad@integrationtest.com"))
            .exchange()

        webTestClient.post().uri("/auth/reset-password")
            .bodyValue(mapOf("email" to "resetbad@integrationtest.com", "code" to "000000", "password" to "newpassword123"))
            .exchange()
            .expectStatus().isBadRequest
    }

    // --- logout / refresh ---

    @Test
    fun `logout returns 200 and clears cookies`() {
        val cookies = register("logout@integrationtest.com")
            .returnResult(String::class.java).responseCookies
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
        val cookies = register("refresh@integrationtest.com")
            .returnResult(String::class.java).responseCookies
        val refreshToken = cookies.getFirst(REFRESH_TOKEN_COOKIE)?.value ?: ""

        webTestClient.post().uri("/auth/refresh")
            .cookie(REFRESH_TOKEN_COOKIE, refreshToken)
            .exchange()
            .expectStatus().isOk
            .expectCookie().exists(ACCESS_TOKEN_COOKIE)
            .expectCookie().exists(REFRESH_TOKEN_COOKIE)
    }

    @Test
    fun `refresh returns 401 with invalid token`() {
        webTestClient.post().uri("/auth/refresh")
            .cookie(REFRESH_TOKEN_COOKIE, "not-a-valid-token")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
