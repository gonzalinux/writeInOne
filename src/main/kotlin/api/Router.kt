package com.gonzalinux.api

import com.gonzalinux.config.JwtAuthFilter
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
    private val jwtAuthFilter: JwtAuthFilter
) {

    @Bean
    fun routes(): RouterFunction<ServerResponse> = publicRoutes().and(protectedRoutes())

    private fun publicRoutes(): RouterFunction<ServerResponse> = route()
        .POST("/auth/register", authHandler::register)
        .POST("/auth/login", authHandler::login)
        .POST("/auth/refresh", authHandler::refresh)
        .POST("/auth/logout", authHandler::logout)
        .build()

    private fun protectedRoutes(): RouterFunction<ServerResponse> = route()

        .path("/sites") { sites ->
            sites
                .POST("/sites", siteHandler::create)
                .GET("/", siteHandler::list)
                .PUT("/{id}", siteHandler::update)
                .DELETE("/{id}", siteHandler::delete)
                .path("/{siteId}/posts") { posts->
                    posts
                        .POST("/", postHandler::create)
                        .GET("/", postHandler::list)
                        .GET("/{postId}", postHandler::get)
                        .PUT("/{postId}", postHandler::update)
                        .DELETE("/{postId}", postHandler::delete)
                        .POST("/{postId}/publish", postHandler::publish)
                        .POST("/{postId}/schedule", postHandler::schedule)
                }

        }

        .build()
        .filter(jwtAuthFilter)
}
