package com.gonzalinux.config

import com.gonzalinux.common.ApiException
import com.gonzalinux.common.UnauthorizedException
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import java.net.URI

private val logger = KotlinLogging.logger {}

@Component
class AdminExceptionFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> =
        Mono.defer { next.handle(request) }
            .onErrorResume(UnauthorizedException::class.java) {
                ServerResponse.seeOther(URI.create("/admin/login")).build()
            }
            .onErrorResume(ApiException::class.java) { ex ->
                ServerResponse.status(ex.status).contentType(MediaType.TEXT_HTML)
                    .render("error", mapOf(
                        "status" to ex.status.value(),
                        "reason" to ex.status.reasonPhrase,
                        "message" to ex.details
                    ))
            }
            .onErrorResume { ex ->
                logger.error(ex) { "Unexpected error on admin route ${request.path()}" }
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_HTML)
                    .render("error", mapOf(
                        "status" to 500,
                        "reason" to "Internal Server Error",
                        "message" to null
                    ))
            }
}
