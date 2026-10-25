package org.ikigaidigital

import io.swagger.v3.oas.annotations.Hidden
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Pins the generated OpenAPI document and the runtime application mappings independently, so hidden
 * operations cannot evade the "exactly two API endpoints" rule (AGENTS.md).
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class SwaggerIT {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var handlerMappings: List<RequestMappingHandlerMapping>

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
    fun `should register exactly the two agreed application operations`() {
        assertApplicationOperations()
    }

    @Test
    fun `should detect a third application mapping even when hidden from OpenAPI`() {
        val mapping = handlerMappings.first()
        val extra = RequestMappingInfo.paths("/review-hidden-probe").methods(RequestMethod.GET).build()
        mapping.registerMapping(extra, HiddenHandler(), HiddenHandler::class.java.getMethod("hidden"))
        try {
            assertThatThrownBy { assertApplicationOperations() }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("GET /review-hidden-probe")
        } finally {
            mapping.unregisterMapping(extra)
        }
    }

    private fun assertApplicationOperations() {
        // Infrastructure handlers (/error, Swagger) are framework-owned; every application package counts.
        val operations = handlerMappings.flatMap { mapping ->
            mapping.handlerMethods.entries
                .filter { (_, handler) -> handler.beanType.name.startsWith("org.ikigaidigital.") }
                .flatMap { (info, _) ->
                    info.patternValues.flatMap { path ->
                        val methods = info.methodsCondition.methods.map { it.name }.ifEmpty { listOf("*") }
                        methods.map { "$it $path" }
                    }
                }
        }
        assertThat(operations).containsExactlyInAnyOrder("GET /time-deposits", "POST /time-deposits/balance-updates")
    }

    class HiddenHandler {
        @Hidden
        fun hidden() = "fixture"
    }

    @Test
    fun `should expose exactly the agreed operations`() {
        val paths = JsonMapper.builder().build().readTree(get("/v3/api-docs").body()).path("paths")

        val operations = paths.properties().flatMap { (path, item) ->
            item.propertyNames().filter { it in HTTP_METHODS }.map { "${it.uppercase()} $path" }
        }

        assertThat(operations).containsExactlyInAnyOrder("GET /time-deposits", "POST /time-deposits/balance-updates")
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
    fun `should require every time deposit response field and describe cent precision`() {
        val schemas = JsonMapper.builder().build().readTree(get("/v3/api-docs").body())
            .path("components").path("schemas")
        val deposit = schemas.path("TimeDepositResponse")

        assertThat(deposit.path("required").toList().map { it.asString() })
            .containsExactlyInAnyOrder("id", "planType", "balance", "days", "withdrawals")
        val properties = deposit.path("properties")
        assertThat(properties.path("balance").path("type").asString()).isEqualTo("number")
        assertThat(properties.path("balance").path("multipleOf").asString()).isEqualTo("0.01")
        assertThat(properties.path("withdrawals").path("type").asString()).isEqualTo("array")
        assertThat(properties.path("withdrawals").path("items").path("\$ref").asString())
            .isEqualTo("#/components/schemas/WithdrawalResponse")
    }

    @Test
    fun `should require every withdrawal field and describe money and dates`() {
        val withdrawal = JsonMapper.builder().build().readTree(get("/v3/api-docs").body())
            .path("components").path("schemas").path("WithdrawalResponse")

        assertThat(withdrawal.path("required").toList().map { it.asString() })
            .containsExactlyInAnyOrder("id", "amount", "date")
        val properties = withdrawal.path("properties")
        assertThat(properties.propertyNames()).containsExactlyInAnyOrder("id", "amount", "date")
        assertThat(properties.path("amount").path("type").asString()).isEqualTo("number")
        assertThat(properties.path("amount").path("multipleOf").asString()).isEqualTo("0.01")
        assertThat(properties.path("date").path("type").asString()).isEqualTo("string")
        assertThat(properties.path("date").path("format").asString()).isEqualTo("date")
    }

    @Test
    fun `should document the balance update with its summary, no request body and the list response`() {
        val update = JsonMapper.builder().build().readTree(get("/v3/api-docs").body())
            .path("paths").path("/time-deposits/balance-updates").path("post")
        val schema = update.path("responses").path("200").path("content").path("application/json").path("schema")

        assertThat(update.path("summary").asString()).isEqualTo("Apply one month of interest to every time deposit")
        assertThat(update.has("requestBody")).isFalse
        assertThat(update.has("parameters")).isFalse
        assertThat(schema.path("type").asString()).isEqualTo("array")
        assertThat(schema.path("items").path("\$ref").asString()).isEqualTo("#/components/schemas/TimeDepositResponse")
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
