package com.gonzalinux.common

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.util.context.Context

fun ServerRequest.isSecureContext(): Boolean {
    val host = uri().host
    return host != "localhost" && host != "127.0.0.1" && !host.startsWith("192.168.") && !host.startsWith("10.")
}

inline fun <reified T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?
): DatabaseClient.GenericExecuteSpec =
    if (value != null) bind(name, value) else bindNull(name, T::class.java)


