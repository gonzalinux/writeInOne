package com.gonzalinux.api

import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.SchedulePostRequest
import com.gonzalinux.api.data.UpdatePostRequest
import com.gonzalinux.common.RequestContextHolder.getUserId
import com.gonzalinux.common.pathVariableLong
import com.gonzalinux.domain.post.PostService
import com.gonzalinux.utils.Utils
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono
import kotlin.jvm.optionals.getOrNull

@Component
class PostHandler(private val service: PostService) {

    fun create(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("siteId")
        return Mono.deferContextual { ctx ->
            request.bodyToMono<CreatePostRequest>()
                .flatMap { service.create(siteId, ctx.getUserId()!!, it) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun get(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("siteId")
        val postId = request.pathVariableLong("postId")
        return Mono.deferContextual { ctx ->
            service.get(postId, siteId, ctx.getUserId()!!)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun list(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("siteId")
        val page = Utils.queryToInt(request.queryParam("page").getOrNull(), default = 0, min= 0)
        val size   = Utils.queryToInt(request.queryParam("size").getOrNull(), default = 10, min= 1)
        val status = request.queryParam("status").orElse(null)?.takeIf { it.isNotBlank() }
        val tag    = request.queryParam("tag").orElse(null)?.takeIf { it.isNotBlank() }
        val search = request.queryParam("search").orElse(null)?.takeIf { it.isNotBlank() }
        return Mono.deferContextual { ctx ->
            service.list(siteId, ctx.getUserId()!!, page, size, status, tag, search)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun update(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("siteId")
        val postId = request.pathVariableLong("postId")
        return Mono.deferContextual { ctx ->
            request.bodyToMono<UpdatePostRequest>()
                .flatMap { service.update(postId, siteId, ctx.getUserId()!!, it) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun delete(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("siteId")
        val postId = request.pathVariableLong("postId")
        return Mono.deferContextual { ctx ->
            service.delete(postId, siteId, ctx.getUserId()!!)
                .then(ServerResponse.ok().build())
        }
    }

    fun publish(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("siteId")
        val postId = request.pathVariableLong("postId")
        return Mono.deferContextual { ctx ->
            service.publish(postId, siteId, ctx.getUserId()!!)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun unpublish(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("siteId")
        val postId = request.pathVariableLong("postId")
        return Mono.deferContextual { ctx ->
            service.unpublish(postId, siteId, ctx.getUserId()!!)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun schedule(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariableLong("siteId")
        val postId = request.pathVariableLong("postId")
        return Mono.deferContextual { ctx ->
            request.bodyToMono<SchedulePostRequest>()
                .flatMap { service.schedule(postId, siteId, ctx.getUserId()!!, it.scheduledAt) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }
}
