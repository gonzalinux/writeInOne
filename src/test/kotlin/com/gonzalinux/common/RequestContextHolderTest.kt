package com.gonzalinux.common

import com.gonzalinux.common.RequestContextHolder.extractRequestId
import com.gonzalinux.common.RequestContextHolder.generateRequestId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest

class RequestContextHolderTest {

    @Test
    fun `generateRequestId returns GK- prefixed 18-char hex string`() {
        val id = generateRequestId()

        assertTrue(id.matches("^GK[0-9a-fA-F]{18}$".toRegex()), "Got: $id")
    }

    @Test
    fun `generateRequestId returns unique values`() {
        val id1 = generateRequestId()
        val id2 = generateRequestId()

        assertNotEquals(id1, id2)
    }

    @Test
    fun `extractRequestId reuses valid x-request-id header`() {
        val existing = generateRequestId()
        val request = MockServerHttpRequest.get("/test")
            .header("x-request-id", existing)
            .build()

        assertEquals(existing, extractRequestId(request))
    }

    @Test
    fun `extractRequestId generates new id when header is missing`() {
        val request = MockServerHttpRequest.get("/test").build()

        val id = extractRequestId(request)

        assertTrue(id.matches("^GK[0-9a-fA-F]{18}$".toRegex()))
    }

    @Test
    fun `extractRequestId generates new id when header has wrong format`() {
        val request = MockServerHttpRequest.get("/test")
            .header("x-request-id", "not-valid-format")
            .build()

        val id = extractRequestId(request)

        assertTrue(id.matches("^GK[0-9a-fA-F]{18}$".toRegex()))
        assertNotEquals("not-valid-format", id)
    }
}
