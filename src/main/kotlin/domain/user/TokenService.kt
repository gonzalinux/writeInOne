package com.gonzalinux.domain.user

import com.gonzalinux.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.*

@Service
class TokenService(jwtProperties: JwtProperties) {

    private val signingKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    private val accessTokenExpiryMs = jwtProperties.accessTokenExpiryMinutes * 60 * 1000
    private val refreshTokenExpiryDays = jwtProperties.refreshTokenExpiryDays

    fun generateAccessToken(user: User): AccessToken {
        val now = Date()

        val token = Jwts.builder()
            .subject(user.id.toString())
            .issuedAt(now)
            .expiration(Date(now.time + accessTokenExpiryMs))
            .signWith(signingKey)
            .compact()

        return AccessToken(token)
    }

    fun generateRefreshToken(): RefreshToken {
        val value = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val expiresAt = LocalDateTime.now().plusDays(refreshTokenExpiryDays)
        return RefreshToken(value, expiresAt)
    }

    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray())
        return HexFormat.of().formatHex(hashBytes)
    }

    fun getUserIdFromToken(token: String): Long {
        val claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload

        return claims.subject.toLong()
    }
}
