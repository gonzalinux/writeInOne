package com.gonzalinux.domain.user

import java.time.LocalDateTime
import java.time.OffsetDateTime

data class AccessToken(val value: String)

data class RefreshToken(val value: String, val expiresAt: LocalDateTime)

data class AuthTokens(val accessToken: AccessToken, val refreshToken: RefreshToken)

data class StoredRefreshToken(val userId: Long, val tokenHash: String, val expiresAt: LocalDateTime)
