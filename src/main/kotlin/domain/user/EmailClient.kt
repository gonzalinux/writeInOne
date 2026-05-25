package com.gonzalinux.domain.user

import reactor.core.publisher.Mono

interface EmailClient {
    fun sendVerificationEmail(to: String, displayName: String, code: String): Mono<Void>
    fun sendPasswordResetEmail(to: String, code: String): Mono<Void>
}
