package com.gonzalinux.api

import com.gonzalinux.config.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    private val jwtAuthFilter: JwtAuthFilter,
    private val jwtNotEnforcedFilter: JwtNotEnforcedFilter,
    private val hostFilter: HostFilter,
    private val adminHostFilter: AdminHostFilter,
    private val blogExceptionFilter: BlogExceptionFilter,
    private val adminExceptionFilter: AdminExceptionFilter
) {

    @Bean
    fun routes(): RouterFunction<ServerResponse> =
        publicRoutes()
            .and(adminPreviewRoute())
            .and(adminRoutes())
            .and(protectedRoutes())
            .and(blogUiRoutes())
            .and(blogApiRoutes())

    private fun publicRoutes(): RouterFunction<ServerResponse> = route()
        .POST("/auth/register", authHandler::register)
        .POST("/auth/login", authHandler::login)
        .POST("/auth/refresh", authHandler::refresh)
        .POST("/auth/logout", authHandler::logout)
        .build()

    private fun protectedRoutes(): RouterFunction<ServerResponse> = route()

        .path("/sites") { sites ->
            sites
                .POST("/", siteHandler::create)
                .GET("/", siteHandler::list)
                .GET("/{id}", siteHandler::get)
                .PATCH("/{id}", siteHandler::update)
                .DELETE("/{id}", siteHandler::delete)
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
        .filter(jwtAuthFilter)

    private fun adminPreviewRoute(): RouterFunction<ServerResponse> = route()
        .GET("/admin/preview/{siteId}/{lang}/{slug}", adminHandler::preview)
        .build()
        .filter(adminHostFilter)
        .filter(jwtAuthFilter)
        .filter(adminExceptionFilter)

    private fun adminRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/admin", adminHandler::serve)
        .GET("/admin/**", adminHandler::serve)
        .build()
        .filter(adminHostFilter)
        .filter(adminExceptionFilter)

    private fun blogUiRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/", blogsHandler::index)
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
        .build()
        .filter(hostFilter)
}
