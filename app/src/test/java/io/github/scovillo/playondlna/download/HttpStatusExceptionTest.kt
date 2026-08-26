package io.github.scovillo.playondlna.download

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class HttpStatusExceptionTest {
    @Test
    fun `preserves unauthorized status`() {
        assertHttpStatusException(401)
    }

    @Test
    fun `preserves forbidden status`() {
        assertHttpStatusException(403)
    }

    private fun assertHttpStatusException(statusCode: Int) {
        try {
            requireSuccessfulHttpStatus(statusCode, "Authentication required", "https://video.example/api")
            fail("Expected HttpStatusException")
        } catch (exception: HttpStatusException) {
            assertEquals(statusCode, exception.statusCode)
        }
    }
}
