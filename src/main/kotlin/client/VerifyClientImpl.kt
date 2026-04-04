package com.gonzalinux.client

import com.gonzalinux.domain.site.VerifyClient
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
class VerifyClientImpl(private val webClient: WebClient) : VerifyClient {

    private val verifications = ConcurrentHashMap<String, String>()

    override fun verify(domain: String, prefix: String): Mono<Boolean> {
        val token = UUID.randomUUID().toString()
        verifications[domain] = token
        val prefix = prefix.removePrefix("/").removeSuffix("/")
        return webClient
            .get()
            .uri("http://$domain/$prefix/_verify")
            .retrieve()
            .bodyToMono<String>()
            .map { it == verifications[domain] }
            .defaultIfEmpty(false)
            .doOnSuccess { verified -> if (verified==true) verifications.remove(domain) }
    }

    override fun getToken(domain: String): String? = verifications[domain]
}
