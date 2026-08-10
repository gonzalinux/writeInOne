package com.gonzalinux.domain.site

import com.gonzalinux.common.ForbiddenException
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

enum class Roles(val admin: Boolean, val publish: Boolean, val write: Boolean) {
    ADMIN(true, true, true),
    EDITOR(false, true, true),
    WRITER(false, false, true);

    companion object {
        fun from(string: String?): Roles? {
            if (string == null) {
                return null
            }
            return Roles.entries.firstOrNull { it.name == string.uppercase() }
        }
    }
}

fun Mono<Site>.requireWrite(): Mono<Site> =
    flatMap {
        if (it.role?.write == true)
            it.toMono()
        else
            Mono.error { ForbiddenException("Current user has no write permissions.") }
    }

fun Mono<Site>.requirePublish(): Mono<Site> =
    flatMap {
        if (it.role?.publish == true)
            it.toMono()
        else
            Mono.error { ForbiddenException("Current user has no publish permissions.") }
    }

fun Mono<Site>.requireAdmin(): Mono<Site> =
    flatMap {
        if (it.role?.admin == true)
            it.toMono()
        else
            Mono.error { ForbiddenException("Current user has no admin permissions.") }
    }
