package com.gonzalinux.api

import com.gonzalinux.config.AdminExceptionFilter
import com.gonzalinux.config.AdminHostFilter
import com.gonzalinux.config.BlogExceptionFilter
import com.gonzalinux.config.HostFilter
import com.gonzalinux.config.JwtAuthFilter
import com.gonzalinux.config.JwtNotEnforcedFilter
import com.gonzalinux.config.ServiceAccountAuthFilter
import com.gonzalinux.config.SubdomainProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RequestPredicates
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse

@Configuration
class Router(
    private val authHandler: AuthHandler,
    private val siteHandler: SiteHandler,
    private val postHandler: PostHandler,
    private val tagHandler: TagHandler,
    private val blogsHandler: BlogsHandler,
    private val adminHandler: AdminHandler,
    private val invitationHandler: InvitationHandler,
    private val serviceAccountHandler: ServiceAccountHandler,
    private val docsHandler: DocsHandler,
    private val mcpHandler: McpHandler,
    private val jwtAuthFilter: JwtAuthFilter,
    private val serviceAccountAuthFilter: ServiceAccountAuthFilter,
    private val jwtNotEnforcedFilter: JwtNotEnforcedFilter,
    private val hostFilter: HostFilter,
    private val adminHostFilter: AdminHostFilter,
    private val blogExceptionFilter: BlogExceptionFilter,
    private val adminExceptionFilter: AdminExceptionFilter,
    private val subdomainProperties: SubdomainProperties
) {

    @Bean
    fun routes(): RouterFunction<ServerResponse> =
        publicRoutes()
            .and(adminPreviewRoute())
            .and(adminRoutes())
            .and(docsRoutes())
            .and(protectedRoutes())
            .and(mcpRoutes())
            .and(blogUiRoutes())
            .and(blogApiRoutes())

    private fun publicRoutes(): RouterFunction<ServerResponse> = route()
        .POST("/auth/register", authHandler::register)
        .POST("/auth/login", authHandler::login)
        .POST("/auth/refresh", authHandler::refresh)
        .POST("/auth/logout", authHandler::logout)
        .POST("/auth/verify-email", authHandler::verifyEmail)
        .POST("/auth/resend-verification", authHandler::resendVerification)
        .POST("/auth/forgot-password", authHandler::forgotPassword)
        .POST("/auth/reset-password", authHandler::resetPassword)
        .build()

    private fun protectedRoutes(): RouterFunction<ServerResponse> = route()

        // Outside /sites on purpose: whoever follows an invite link has no membership yet, so a
        // site-scoped guard would reject exactly the person the link was made for.
        .POST("/invitations/accept", invitationHandler::accept)

        .path("/service-accounts") { serviceAccounts ->
            serviceAccounts
                .POST("/", serviceAccountHandler::create)
                .GET("/", serviceAccountHandler::list)
                .DELETE("/{id}", serviceAccountHandler::revoke)
                .POST("/{id}/rotate", serviceAccountHandler::rotate)
                .GET("/{id}/sites", serviceAccountHandler::sites)
        }

        .path("/sites") { sites ->
            sites
                .POST("/", siteHandler::create)
                .GET("/", siteHandler::list)
                // Must precede /{id}, which would otherwise swallow it.
                .GET("/subdomain", siteHandler::subdomain)
                .GET("/{id}", siteHandler::get)
                .PATCH("/{id}", siteHandler::update)
                .DELETE("/{id}", siteHandler::delete)
                .GET("/{id}/users", siteHandler::users)
                .DELETE("/{id}/users/{userId}", siteHandler::deleteUser)
                .PATCH("/{id}/users/{userId}", siteHandler::updateUserRole)
                .GET("/{id}/invitations", invitationHandler::list)
                .POST("/{id}/invitations", invitationHandler::create)
                .DELETE("/{id}/invitations/{invitationId}", invitationHandler::revoke)
                .POST("/{id}/invitations/service-account", invitationHandler::inviteServiceAccount)
                .path("/{siteId}/posts") { posts ->
                    posts
                        .POST("/", postHandler::create)
                        .GET("/", postHandler::list)
                        .GET("/{postId}", postHandler::get)
                        .PUT("/{postId}", postHandler::update)
                        .DELETE("/{postId}", postHandler::delete)
                        .POST("/{postId}/publish", postHandler::publish)
                        .POST("/{postId}/unpublish", postHandler::unpublish)
                        .POST("/{postId}/schedule", postHandler::schedule)
                }
                .path("/{siteId}/tags") { tags ->
                    tags
                        .GET("/", tagHandler::list)
                        .DELETE("/{tagId}", tagHandler::delete)
                }

        }

        .build()
        .filter(serviceAccountAuthFilter)

    // Stateless: no HostFilter (not site-domain-scoped — operates over whatever sites the
    // caller's site_members rows grant, same as protectedRoutes) and no Mcp-Session-Id ever
    // issued.
    private fun mcpRoutes(): RouterFunction<ServerResponse> = route()
        .POST("/mcp", mcpHandler::handle)
        .build()
        .filter(serviceAccountAuthFilter)

    private fun adminPreviewRoute(): RouterFunction<ServerResponse> = route()
        .GET("/admin/preview/{siteId}/{lang}/{slug}", adminHandler::preview)
        .build()
        .filter(adminHostFilter)
        .filter(jwtAuthFilter)
        .filter(adminExceptionFilter)

    private fun adminRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/sitemap.xml", RequestPredicates.headers { h ->
            val host = h.firstHeader("X-Site-Host") ?: h.firstHeader("Host") ?: ""
            subdomainProperties.isHomeDomain(host)
        }, blogsHandler::mainSitemap)
        .GET("/admin", adminHandler::serve)
        .GET("/admin/**", adminHandler::serve)
        .build()
        .filter(adminHostFilter)
        .filter(adminExceptionFilter)

    private fun docsRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/docs", docsHandler::index)
        .GET("/docs/**", docsHandler::page)
        .build()
        .filter(adminHostFilter)

    private fun blogUiRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/", blogsHandler::index)
        .GET("/css/custom.css", blogsHandler::customCss)
        .GET("/sitemap.xml", blogsHandler::sitemap)
        .GET("/rss.xml", blogsHandler::rssRoot)
        .GET("/{lang:es|en}", blogsHandler::postList)
        .GET("/{lang:es|en}/rss.xml", blogsHandler::rss)
        .GET("/articles/{slug}", blogsHandler::postRoot)
        .GET("/{lang:es|en}/articles/{slug}", blogsHandler::post)
        .GET("/_verify", blogsHandler::verify)
        .build()
        .filter(hostFilter)
        .filter(jwtNotEnforcedFilter)
        .filter(blogExceptionFilter)

    private fun blogApiRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/{lang:es|en}/posts", blogsHandler::postListJson)
        .POST("/{lang:es|en}/posts/{slug}/event", blogsHandler::recordEvent)
        .build()
        .filter(hostFilter)
}
