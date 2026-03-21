package com.gonzalinux.api

import com.gonzalinux.api.data.CreateSiteRequest
import com.gonzalinux.api.data.UpdateSiteRequest
import com.gonzalinux.common.RequestContextHolder.getRequestContext
import com.gonzalinux.common.RequestValidator
import com.gonzalinux.common.pathVariableLong
import com.gonzalinux.domain.site.Site
import com.gonzalinux.domain.site.SiteService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono

@Component
class SiteHandler(private val service: SiteService, private val validator: RequestValidator) {

    fun create(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            request.bodyToMono<CreateSiteRequest>()
                .map { validator.validate(it) }
                .flatMap { service.create(ctx.getRequestContext()!!.userId, it) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }

    fun list(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            ServerResponse.ok().
            body<Site>(service.list(ctx.getRequestContext()!!.userId))
        }

    fun update(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariableLong("id")
        return Mono.deferContextual { ctx ->
            request.bodyToMono<UpdateSiteRequest>()
                .flatMap { service.update(id, ctx.getRequestContext()!!.userId, it) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun get(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariableLong("id")
        return Mono.deferContextual { ctx ->
            service.findById(id, ctx.getRequestContext()!!.userId)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun delete(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariableLong("id")
        return Mono.deferContextual { ctx ->
            service.delete(id, ctx.getRequestContext()!!.userId)
                .then(ServerResponse.ok().build())
        }
    }
}
