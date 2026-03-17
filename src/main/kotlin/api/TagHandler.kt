package com.gonzalinux.api

import com.gonzalinux.common.RequestContextHolder.getRequestContext
import com.gonzalinux.domain.tag.Tag
import com.gonzalinux.domain.tag.TagService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono

@Component
class TagHandler(private val service: TagService) {

    fun list(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        return Mono.deferContextual { ctx ->
            ServerResponse.ok().body<Tag>(service.list(siteId, ctx.getRequestContext()!!.userId))
        }
    }

    fun delete(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        val tagId = request.pathVariable("tagId").toLong()
        return Mono.deferContextual { ctx ->
            service.delete(tagId, siteId, ctx.getRequestContext()!!.userId)
                .then(ServerResponse.ok().build())
        }
    }
}
