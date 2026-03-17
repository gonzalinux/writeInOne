package com.gonzalinux.domain.user

import com.gonzalinux.config.JwtProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TokenServiceTest {

    private val properties = JwtProperties(
        secret = "test-secret-that-is-long-enough-for-hmac-sha",
        accessTokenExpiryMinutes = 15,
        refreshTokenExpiryDays = 30
    )
    private val tokenService = TokenService(properties)

    private val user = User(
        id = 1L,
        email = "test@test.com",
        passwordHash = "hash",
        displayName = "Test User",
        createdAt = OffsetDateTime.now(ZoneOffset.UTC),
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
    )

    @Test
    fun `generateAccessToken produces valid JWT with correct userId`() {
        val token = tokenService.generateAccessToken(user.id)

        assertFalse(token.value.isBlank())
        assertEquals(user.id, tokenService.getUserIdFromToken(token.value))
    }

    @Test
    fun `getUserIdFromToken fails on tampered token`() {
        val token = tokenService.generateAccessToken(user.id)
        val tampered = token.value + "tampered"

        assertThrows(Exception::class.java) {
            tokenService.getUserIdFromToken(tampered)
        }
    }

    @Test
    fun `generateRefreshToken returns unique values`() {
        val token1 = tokenService.generateRefreshToken()
        val token2 = tokenService.generateRefreshToken()

        assertNotEquals(token1.value, token2.value)
    }

    @Test
    fun `generateRefreshToken expiry is 30 days from now`() {
        val token = tokenService.generateRefreshToken()
        val expectedExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30)

        assertTrue(token.expiresAt.isAfter(expectedExpiry.minusSeconds(5)))
        assertTrue(token.expiresAt.isBefore(expectedExpiry.plusSeconds(5)))
    }

    @Test
    fun `hashToken produces consistent results`() {
        val hash1 = tokenService.hashToken("sometoken")
        val hash2 = tokenService.hashToken("sometoken")

        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashToken produces different results for different inputs`() {
        val hash1 = tokenService.hashToken("token1")
        val hash2 = tokenService.hashToken("token2")

        assertNotEquals(hash1, hash2)
    }
}
