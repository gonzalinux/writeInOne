package com.gonzalinux.common

import org.springframework.r2dbc.core.DatabaseClient
import reactor.util.context.Context

inline fun <reified T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?
): DatabaseClient.GenericExecuteSpec =
    if (value != null) bind(name, value) else bindNull(name, T::class.java)


