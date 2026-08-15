package com.gonzalinux.domain.user

import com.gonzalinux.common.ServiceAccountNotFoundException
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class ServiceAccountService(private val repo: ServiceAccountRepository, private val tokenService: TokenService) {

    /** The raw [CreatedServiceAccount.token] is only ever available here — only its hash is persisted. */
    fun create(ownerId: Long, name: String): Mono<CreatedServiceAccount> {
        val token = newToken()
        return repo.create(ownerId, name, tokenService.hashToken(token))
            .map { CreatedServiceAccount(it, token) }
            .doOnNext { logger.info { "Service account created [ownerId=$ownerId, id=${it.account.id}]" } }
    }

    fun list(ownerId: Long): Flux<User> = repo.findAllByOwnerId(ownerId)

    fun revoke(id: Long, ownerId: Long): Mono<Void> =
        repo.deleteByIdAndOwnerId(id, ownerId)
            .flatMap<Void> { rows ->
                if (rows == 0L) Mono.error(ServiceAccountNotFoundException(id))
                else Mono.empty()
            }
            .doOnSuccess { logger.info { "Service account revoked [ownerId=$ownerId, id=$id]" } }

    /** Old token stops working the instant this returns — same row, new hash. */
    fun rotate(id: Long, ownerId: Long): Mono<String> {
        val token = newToken()
        return repo.updateTokenHash(id, ownerId, tokenService.hashToken(token))
            .flatMap { rows ->
                if (rows == 0L) Mono.error(ServiceAccountNotFoundException(id))
                else Mono.just(token)
            }
    }

    private fun newToken(): String =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
}

data class CreatedServiceAccount(val account: User, val token: String)
