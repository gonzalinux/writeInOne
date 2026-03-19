package com.gonzalinux.api

import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.SchedulePostRequest
import com.gonzalinux.api.data.UpdatePostRequest
import com.gonzalinux.common.RequestContextHolder.getRequestContext
import com.gonzalinux.domain.post.PostService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono

@Component
class PostHandler(private val service: PostService) {

    fun create(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        return Mono.deferContextual { ctx ->
            request.bodyToMono<CreatePostRequest>()
                .flatMap { service.create(siteId, ctx.getRequestContext()!!.userId, it) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun get(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        val postId = request.pathVariable("postId").toLong()
        return Mono.deferContextual { ctx ->
            service.get(postId, siteId, ctx.getRequestContext()!!.userId)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun list(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        val page   = request.queryParam("page").map { it.toIntOrNull() ?: 0 }.orElse(0).coerceAtLeast(0)
        val size   = request.queryParam("size").map { it.toIntOrNull() ?: 20 }.orElse(20).coerceIn(1, 100)
        val status = request.queryParam("status").orElse(null)?.takeIf { it.isNotBlank() }
        val tag    = request.queryParam("tag").orElse(null)?.takeIf { it.isNotBlank() }
        val search = request.queryParam("search").orElse(null)?.takeIf { it.isNotBlank() }
        return Mono.deferContextual { ctx ->
            service.list(siteId, ctx.getRequestContext()!!.userId, page, size, status, tag, search)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun update(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        val postId = request.pathVariable("postId").toLong()
        return Mono.deferContextual { ctx ->
            request.bodyToMono<UpdatePostRequest>()
                .flatMap { service.update(postId, siteId, ctx.getRequestContext()!!.userId, it) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun delete(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        val postId = request.pathVariable("postId").toLong()
        return Mono.deferContextual { ctx ->
            service.delete(postId, siteId, ctx.getRequestContext()!!.userId)
                .then(ServerResponse.ok().build())
        }
    }

    fun publish(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        val postId = request.pathVariable("postId").toLong()
        return Mono.deferContextual { ctx ->
            service.publish(postId, siteId, ctx.getRequestContext()!!.userId)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun unpublish(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        val postId = request.pathVariable("postId").toLong()
        return Mono.deferContextual { ctx ->
            service.unpublish(postId, siteId, ctx.getRequestContext()!!.userId)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }

    fun schedule(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        val postId = request.pathVariable("postId").toLong()
        return Mono.deferContextual { ctx ->
            request.bodyToMono<SchedulePostRequest>()
                .flatMap { service.schedule(postId, siteId, ctx.getRequestContext()!!.userId, it.scheduledAt) }
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }
}
