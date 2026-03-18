package com.gonzalinux.api

import com.gonzalinux.config.AdminExceptionFilter
import com.gonzalinux.config.BlogExceptionFilter
import com.gonzalinux.config.HostFilter
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
    private val tagHandler: TagHandler,
    private val blogsHandler: BlogsHandler,
    private val adminHandler: AdminHandler,
    private val jwtAuthFilter: JwtAuthFilter,
    private val hostFilter: HostFilter,
    private val blogExceptionFilter: BlogExceptionFilter,
    private val adminExceptionFilter: AdminExceptionFilter
) {

    @Bean
    fun routes(): RouterFunction<ServerResponse> =
        publicRoutes()
            .and(adminPublicRoutes())
            .and(adminProtectedRoutes())
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
                .path("/{siteId}/tags") { tags ->
                    tags
                        .GET("/", tagHandler::list)
                        .DELETE("/{tagId}", tagHandler::delete)
                }

        }

        .build()
        .filter(jwtAuthFilter)

    private fun adminPublicRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/admin/login", adminHandler::loginPage)
        .POST("/admin/login", adminHandler::loginSubmit)
        .GET("/admin/register", adminHandler::registerPage)
        .POST("/admin/register", adminHandler::registerSubmit)
        .GET("/admin/logout", adminHandler::logout)
        .build()
        .filter(adminExceptionFilter)

    private fun adminProtectedRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/admin", adminHandler::dashboard)
        .GET("/admin/sites/new", adminHandler::newSitePage)
        .POST("/admin/sites", adminHandler::createSite)
        .GET("/admin/sites/{siteId}/posts", adminHandler::postListPage)
        .GET("/admin/sites/{siteId}/posts/new", adminHandler::newPostPage)
        .POST("/admin/sites/{siteId}/posts", adminHandler::createPost)
        .build()
        .filter(jwtAuthFilter)
        .filter(adminExceptionFilter)

    private fun blogUiRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/", blogsHandler::index)
        .GET("/{lang}", blogsHandler::postList)
        .GET("/{lang}/{slug}", blogsHandler::post)
        .build()
        .filter(hostFilter)
        .filter(blogExceptionFilter)

    private fun blogApiRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/{lang}/posts", blogsHandler::postListJson)
        .build()
        .filter(hostFilter)
}
