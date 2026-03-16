package com.gonzalinux.common

import org.springframework.http.HttpStatus


class UserAlreadyExistsException(email: String) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "USER_ALREADY_EXISTS",
    details = "A user with email $email already exists"
)

class UnauthorizedException() : ApiException(
    status = HttpStatus.UNAUTHORIZED,
    error = "UNAUTHORIZED",
    details = "You didn't provide valid credentials. Or your credentials were expired."
)

class SiteNotFoundException(id: Long) : ApiException(
    status = HttpStatus.NOT_FOUND,
    error = "SITE_NOT_FOUND",
    details = "Site with id $id not found"
)

class SiteDomainTakenException(domain: String) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "SITE_DOMAIN_TAKEN",
    details = "Domain $domain is already taken"
)

class PostNotFoundException(id: Long) : ApiException(
    status = HttpStatus.NOT_FOUND,
    error = "POST_NOT_FOUND",
    details = "Post with id $id not found"
)

