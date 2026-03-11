package com.gonzalinux.domain.post

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class PostRepository(val client: DatabaseClient) {
}
