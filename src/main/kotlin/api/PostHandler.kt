package com.gonzalinux.api

import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.SchedulePostRequest
import com.gonzalinux.api.data.UpdatePostRequest
import com.gonzalinux.common.RequestContextHolder.getRequestContext
import com.gonzalinux.domain.post.PostService
import com.gonzalinux.domain.post.PostWithTranslations
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
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
        return Mono.deferContextual { ctx ->
            ServerResponse.ok().body<PostWithTranslations>(service.list(siteId, ctx.getRequestContext()!!.userId))
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
