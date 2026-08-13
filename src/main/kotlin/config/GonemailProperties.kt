package com.gonzalinux.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gonemail")
data class GonemailProperties(
    val baseUrl: String,
    val appName: String,
    val verificationTemplate: String = "email-verification",
    val passwordResetTemplate: String = "password-reset",
    val invitationTemplate: String = "site-invitation"
)
