package com.gonzalinux.domain.user

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class UserRepository (val client: DatabaseClient) {

}