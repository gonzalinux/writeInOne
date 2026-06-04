package com.gonzalinux.domain.post

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.time.LocalDate

@Service
class PostEventService(
    private val postEventRepo: PostEventRepository,
    private val postRepo: PostRepository
) {

    fun recordView(postId: Long, ip: String, userAgent: String): Mono<Void> {
        val hash = fingerprint(ip, userAgent, postId)
        return postEventRepo.fingerprintExistsInWindow(hash, 24)
            .flatMap { exists ->
                if (exists) Mono.empty()
                else postEventRepo.record(postId, "view", 1, hash)
                    .then(postRepo.incrementViewCount(postId))
            }
    }

    private fun fingerprint(ip: String, userAgent: String, postId: Long): String {
        val raw = "$ip|$userAgent|$postId|${LocalDate.now()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
