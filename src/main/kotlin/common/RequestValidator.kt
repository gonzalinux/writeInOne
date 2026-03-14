package com.gonzalinux.common

import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

class ValidationException(details: String) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    error = "VALIDATION_ERROR",
    details = details
)

@Component
class RequestValidator(private val validator: Validator) {

    fun <T> validate(request: T): T {
        val violations = validator.validate(request).toSet()
        if (violations.isNotEmpty()) {
            val message = violations.joinToString(", ") { it.message }
            throw ValidationException(message)
        }
        return request
    }
}
