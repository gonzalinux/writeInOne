package com.gonzalinux.utils

object Utils {

    fun queryToInt(query: String?, default: Int = 0, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int {
        val number = if (query.isNullOrBlank()) {
            0
        } else {
            query.toIntOrNull() ?: 0
        }
        return number.coerceIn(min, max)
    }
}