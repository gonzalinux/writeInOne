package com.gonzalinux.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@RestController
@RequestMapping("/")
class BlogsHandler {

    @GetMapping("/blogs/{id}")
    fun handleGet(request: ServerRequest, @PathVariable id: String): Mono<ServerResponse> {

        return ServerResponse.ok().bodyValue("{\"id\":\"$id\"}")

    }

}