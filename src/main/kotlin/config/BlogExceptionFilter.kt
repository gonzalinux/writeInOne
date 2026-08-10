package com.gonzalinux.config

import com.gonzalinux.api.forLang
import com.gonzalinux.common.ApiException
import com.gonzalinux.common.SiteContextHolder.getPrefix
import com.gonzalinux.common.SiteContextHolder.getSite
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
class BlogExceptionFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> =
        next.handle(request)
            .onErrorResume(ApiException::class.java) { ex ->
                logger.debug { "Blog UI error [${ex.status}]: ${ex.details}" }
                if (ex.status == HttpStatus.NOT_FOUND) renderBlogNotFound(request)
                else renderErrorPage(ex.status, ex.details)
            }
            .onErrorResume { ex ->
                logger.error(ex) { "Unexpected error on blog route ${request.path()}" }
                renderErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, null)
            }

    private fun renderBlogNotFound(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            val site = ctx.getSite()
            if (site == null) {
                renderErrorPage(HttpStatus.NOT_FOUND, null)
            } else {
                val prefix = ctx.getPrefix()
                val lang = request.pathVariables()["lang"] ?: site.languages.firstOrNull()?.value ?: "en"
                ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .render(
                        "blog-not-found", mapOf(
                            "site" to site,
                            "lang" to lang,
                            "prefix" to prefix,
                            "langConfig" to site.config.forLang(lang),
                        )
                    )
            }
        }

    private fun renderErrorPage(status: HttpStatus, message: String?): Mono<ServerResponse> =
        ServerResponse.status(status)
            .contentType(MediaType.TEXT_HTML)
            .render(
                "error", mapOf(
                    "status" to status.value(),
                    "reason" to status.reasonPhrase,
                    "message" to message
                )
            )
}
