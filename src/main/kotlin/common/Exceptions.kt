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

class SlugAlreadyExistsException(slug: String) : ApiException(
    status = HttpStatus.CONFLICT,
    error = "SLUG_ALREADY_EXISTS",
    details = "A post with slug '$slug' already exists for this site and language"
)

class BadRequestException(details: String) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    error = "BAD_REQUEST",
    details = details
)

