package com.gonzalinux.domain.site

enum class Roles(val modifyUsers: Boolean, val publish: Boolean, val write: Boolean) {
    ADMIN(true, true, true),
    EDITOR(false, true, true),
    WRITER(false, false, true);

    companion object {
        fun from(string: String?): Roles? {
            if (string == null) {
                return null
            }
            return Roles.entries.firstOrNull { it.name == string.uppercase() }
        }
    }
}