package com.gonzalinux.api

import com.gonzalinux.docs.DocsService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import java.net.URI

@Component
class DocsHandler(private val docsService: DocsService) {

    fun index(request: ServerRequest): Mono<ServerResponse> {
        val firstSlug = docsService.firstSlug()
            ?: return ServerResponse.notFound().build()
        return ServerResponse.seeOther(URI.create("/docs/$firstSlug")).build()
    }

    fun page(request: ServerRequest): Mono<ServerResponse> {
        val slug = request.path().removePrefix("/docs/").trim('/')
        val page = docsService.find(slug)
            ?: return ServerResponse.notFound().build()

        return ServerResponse.ok().render(
            "docs",
            mapOf(
                "groups" to docsService.groups,
                "activeSlug" to slug,
                "page" to page
            )
        )
    }
}
