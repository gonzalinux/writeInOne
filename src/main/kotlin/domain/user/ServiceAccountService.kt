package com.gonzalinux.domain.user

import com.gonzalinux.common.ServiceAccountNotFoundException
import com.gonzalinux.domain.site.Site
import com.gonzalinux.domain.site.SiteRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class ServiceAccountService(
    private val repo: ServiceAccountRepository,
    private val siteRepo: SiteRepository,
    private val tokenService: TokenService
) {

    /** The raw [CreatedServiceAccount.token] is only ever available here — only its hash is persisted. */
    fun create(ownerId: Long, name: String): Mono<CreatedServiceAccount> {
        val token = newToken()
        return repo.create(ownerId, name, tokenService.hashToken(token))
            .map { CreatedServiceAccount(it, token) }
            .doOnNext { logger.info { "Service account created [ownerId=$ownerId, id=${it.account.id}]" } }
    }

    fun list(ownerId: Long): Flux<User> = repo.findAllByOwnerId(ownerId)

    /**
     * [Site.role] here is the *service account's* role on each site, not the caller's — this reuses
     * `SiteRepository.findAllByUserId`, the same query a human's own site list is built from.
     */
    fun listSites(id: Long, ownerId: Long): Flux<Site> =
        repo.existsByIdAndOwnerId(id, ownerId)
            .flatMapMany { owned ->
                if (owned) siteRepo.findAllByUserId(id)
                else Flux.error(ServiceAccountNotFoundException(id))
            }

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
