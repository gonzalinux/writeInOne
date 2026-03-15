package com.gonzalinux.api.data

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:Email val email: String,
    @field:Size(min = 8) val password: String
)

data class RegisterRequest(
    @field:Email val email: String,
    @field:NotBlank val displayName: String,
    @field:Size(min = 8) val password: String
)

data class AuthResponse(
    val message: String? = null,
)
