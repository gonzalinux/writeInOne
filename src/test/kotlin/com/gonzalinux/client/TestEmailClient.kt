package com.gonzalinux.client

import com.gonzalinux.domain.user.EmailClient
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

@Component
@Primary
@Profile("test")
class TestEmailClient : EmailClient {

    private val verificationCodes = ConcurrentHashMap<String, String>()
    private val resetCodes = ConcurrentHashMap<String, String>()

    override fun sendVerificationEmail(to: String, displayName: String, code: String): Mono<Void> {
        verificationCodes[to] = code
        return Mono.empty()
    }

    override fun sendPasswordResetEmail(to: String, code: String): Mono<Void> {
        resetCodes[to] = code
        return Mono.empty()
    }

    fun getVerificationCode(email: String): String? = verificationCodes[email]
    fun getResetCode(email: String): String? = resetCodes[email]
    fun clear() { verificationCodes.clear(); resetCodes.clear() }
}
