package com.gonzalinux.domain.site

import reactor.core.publisher.Mono

interface VerifyClient {
    fun verify(domain: String, prefix: String): Mono<Boolean>
    fun getToken(domain: String): String?
}
