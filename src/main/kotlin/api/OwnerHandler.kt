package com.gonzalinux.api

import com.gonzalinux.domain.owner.OwnerService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class OwnerHandler(private val ownerService: OwnerService) {

    fun dashboard(request: ServerRequest): Mono<ServerResponse> =
        ownerService.getStats()
            .flatMap { stats ->
                ServerResponse.ok().render(
                    "owner/dashboard", mapOf(
                        "userCount" to stats.userCount,
                        "siteCount" to stats.siteCount,
                        "users" to stats.recentUsers,
                        "sites" to stats.recentSites,
                        "topPosts" to stats.topPosts
                    )
                )
            }

    fun verifySite(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLongOrNull()
            ?: return ServerResponse.badRequest().build()

        return ownerService.verifySite(siteId)
            .then(ServerResponse.seeOther(java.net.URI.create("/owner")).build())
    }
}
