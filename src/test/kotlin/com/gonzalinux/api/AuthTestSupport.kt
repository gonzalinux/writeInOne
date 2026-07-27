package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.client.TestEmailClient
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Registration alone no longer authenticates a user: `/auth/register` returns no cookies and
 * `/auth/login` rejects unverified accounts. Integration tests that need an authenticated caller
 * go through the full register → verify-email → login flow, reading the one-time code straight
 * out of [TestEmailClient] instead of a real inbox.
 *
 * Returns the value of the `access_token` cookie issued by the login.
 */
fun WebTestClient.registerVerifiedUser(
    emailClient: TestEmailClient,
    email: String,
    displayName: String = "Test User",
    password: String = "password123"
): String {
    post().uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(mapOf("email" to email, "displayName" to displayName, "password" to password))
        .exchange()
        .expectStatus().isOk

    val code = emailClient.getVerificationCode(email)
        ?: error("No verification code captured for $email")

    post().uri("/auth/verify-email")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(mapOf("email" to email, "code" to code))
        .exchange()
        .expectStatus().isOk

    val login = post().uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(mapOf("email" to email, "password" to password))
        .exchange()
        .expectStatus().isOk
        .expectBody(Map::class.java)
        .returnResult()

    return login.responseCookies.getFirst(ACCESS_TOKEN_COOKIE)?.value
        ?: error("No access token cookie returned for $email")
}
