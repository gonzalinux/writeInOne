package com.gonzalinux.config

import com.gonzalinux.common.ApiException
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
                renderErrorPage(ex.status, ex.details)
            }
            .onErrorResume { ex ->
                logger.error(ex) { "Unexpected error on blog route ${request.path()}" }
                renderErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, null)
            }

    private fun renderErrorPage(status: HttpStatus, message: String?): Mono<ServerResponse> =
        ServerResponse.status(status)
            .contentType(MediaType.TEXT_HTML)
            .render("error", mapOf(
                "status" to status.value(),
                "reason" to (status.reasonPhrase),
                "message" to message
            ))
}
