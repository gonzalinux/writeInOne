package com.gonzalinux.common

import org.springframework.boot.autoconfigure.web.WebProperties
import org.springframework.boot.webflux.autoconfigure.error.AbstractErrorWebExceptionHandler
import org.springframework.boot.webflux.error.ErrorAttributes
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

data class ErrorResponse(val error: String, val details: String?)

@Component
@Order(-2)
class GlobalExceptionHandler(
    errorAttributes: ErrorAttributes,
    webProperties: WebProperties,
    applicationContext: ApplicationContext,
    codecConfigurer: ServerCodecConfigurer
) : AbstractErrorWebExceptionHandler(errorAttributes, webProperties.resources, applicationContext) {

    init {
        setMessageWriters(codecConfigurer.writers)
        setMessageReaders(codecConfigurer.readers)
    }

    override fun getRoutingFunction(errorAttributes: ErrorAttributes): RouterFunction<ServerResponse> =
        RouterFunctions.route(RequestPredicates.all()) { request -> handleError(request) }

    private fun handleError(request: ServerRequest): Mono<ServerResponse> {
        return when (val error = getError(request)) {
            is ApiException -> ServerResponse.status(error.status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ErrorResponse(error.error, error.details))

            is ResponseStatusException -> ServerResponse.status(error.statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ErrorResponse(error.reason ?: error.statusCode.toString(), null))

            else -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ErrorResponse("INTERNAL_SERVER_ERROR", null))
        }
    }
}
