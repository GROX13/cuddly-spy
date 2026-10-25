package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * springdoc serves the generated OpenAPI document and Swagger UI. The operation set is pinned, so a
 * third endpoint fails the build ("exactly two API endpoints", AGENTS.md).
 */
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
    fun `should expose exactly the agreed operations`() {
        val paths = JsonMapper.builder().build().readTree(get("/v3/api-docs").body()).path("paths")

        val operations = paths.properties().flatMap { (path, item) ->
            item.propertyNames().filter { it in HTTP_METHODS }.map { "${it.uppercase()} $path" }
        }

        assertThat(operations).containsExactlyInAnyOrder("GET /time-deposits")
    }

    @Test
    fun `should document the time deposit list with its summary and response schema`() {
        val api = JsonMapper.builder().build().readTree(get("/v3/api-docs").body())
        val list = api.path("paths").path("/time-deposits").path("get")
        val schema = list.path("responses").path("200").path("content").path("application/json").path("schema")

        assertThat(list.path("summary").asString()).isEqualTo("List all time deposits")
        assertThat(schema.path("type").asString()).isEqualTo("array")
        assertThat(schema.path("items").path("\$ref").asString()).isEqualTo("#/components/schemas/TimeDepositResponse")
        assertThat(api.path("components").path("schemas").path("TimeDepositResponse").path("properties").propertyNames())
            .containsExactly("id", "planType", "balance", "days", "withdrawals")
    }

    @Test
    fun `should serve Swagger UI`() {
        val response = get("/swagger-ui/index.html")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("swagger-ui")
    }

    private companion object {
        /** The OpenAPI path-item keys that are operations; others (`parameters`, `summary`…) are not. */
        val HTTP_METHODS = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")
    }
}
