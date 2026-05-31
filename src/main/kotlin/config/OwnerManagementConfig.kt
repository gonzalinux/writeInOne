package com.gonzalinux.config

import com.gonzalinux.api.OwnerHandler
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse

@ManagementContextConfiguration(proxyBeanMethods = false)
class OwnerManagementConfig(
    private val ownerHandler: OwnerHandler,
    private val ownerExceptionFilter: OwnerExceptionFilter
) {

    @Bean
    fun ownerManagementRoutes(): RouterFunction<ServerResponse> = route()
        .GET("/owner", ownerHandler::dashboard)
        .POST("/owner/sites/{siteId}/verify", ownerHandler::verifySite)
        .build()
        .filter(ownerExceptionFilter)
}
