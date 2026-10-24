package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class SwaggerIT {

    @LocalServerPort
    private var port: Int = 0

    private val http = HttpClient.newHttpClient()

    private fun get(path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

    @Test
    fun `should serve the OpenAPI document`() {
        val response = get("/v3/api-docs")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying {
            assertThat(it).startsWith("application/json")
        }
        assertThat(response.body()).contains("\"openapi\"")
    }

    @Test
    fun `should serve Swagger UI`() {
        val response = get("/swagger-ui/index.html")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("swagger-ui")
    }
}
