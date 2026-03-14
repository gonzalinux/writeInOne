package com.gonzalinux.common

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    val error: String,
    val details: String? = null
) : RuntimeException(details ?: error)
