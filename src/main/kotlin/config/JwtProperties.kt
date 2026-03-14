package com.gonzalinux.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpiryMinutes: Long,
    val refreshTokenExpiryDays: Long
)
