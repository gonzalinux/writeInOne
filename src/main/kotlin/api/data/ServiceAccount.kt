package com.gonzalinux.api.data

import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime

data class CreateServiceAccountRequest(
    @field:NotBlank val name: String
)

data class ServiceAccountResponse(
    val id: Long,
    val name: String,
    val createdAt: OffsetDateTime
)

/** Token is only ever returned once, at creation time. */
data class ServiceAccountCreatedResponse(
    val id: Long,
    val name: String,
    val token: String,
    val createdAt: OffsetDateTime
)

data class ServiceAccountTokenResponse(val token: String)
