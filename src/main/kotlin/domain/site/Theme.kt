package com.gonzalinux.domain.site

enum class Theme(val value: String) {
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromValue(v: String) = entries.find { it.value == v }
    }
}
