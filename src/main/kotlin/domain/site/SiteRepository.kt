package com.gonzalinux.domain.site

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class SiteRepository(val client: DatabaseClient) {
}