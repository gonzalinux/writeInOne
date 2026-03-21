package com.gonzalinux.common

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.util.context.Context

fun ServerRequest.pathVariableLong(name: String): Long =
    pathVariable(name).toLongOrNull() ?: throw BadRequestException("'$name' must be a number")

fun ServerRequest.isSecureContext(): Boolean {
    val forwardedProto = headers().firstHeader("X-Forwarded-Proto")
    if (forwardedProto != null) return forwardedProto.lowercase() == "https"
    val host = uri().host
    return host != "localhost" && host != "127.0.0.1" && !host.startsWith("192.168.") && !host.startsWith("10.")
}

inline fun <reified T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?
): DatabaseClient.GenericExecuteSpec =
    if (value != null) bind(name, value) else bindNull(name, T::class.java)


