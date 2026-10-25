package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate

/**
 * `GET /time-deposits` end to end: real HTTP, real Postgres with the Liquibase schema. The body is
 * compared as raw text, so number and date formats are pinned literally (`1500.00`, not `1500.0`).
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class TimeDepositsApiIT {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val http = HttpClient.newHttpClient()

    // Before and after: each test starts from empty tables, whichever ITs share this context.
    @BeforeEach
    @AfterEach
    fun deleteRows() {
        jdbc.update("DELETE FROM withdrawals")
        jdbc.update("DELETE FROM time_deposits")
    }

    @Test
    fun `should list every time deposit by id with its withdrawals by date`() {
        // Out of order on purpose: deposit 2 before 1, and withdrawal ids opposite to their dates.
        insertDeposit(2, "basic", 400, "1500.00")
        insertDeposit(1, "premium", 90, "6000.00")
        insertWithdrawal(10, 2, "50.00", "2026-06-01")
        insertWithdrawal(11, 2, "100.00", "2026-03-15")

        val response = getTimeDeposits()

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json")
        assertThat(response.body()).isEqualTo(
            """[""" +
                """{"id":1,"planType":"premium","balance":6000.00,"days":90,"withdrawals":[]},""" +
                """{"id":2,"planType":"basic","balance":1500.00,"days":400,"withdrawals":[""" +
                """{"id":11,"amount":100.00,"date":"2026-03-15"},""" +
                """{"id":10,"amount":50.00,"date":"2026-06-01"}""" +
                """]}""" +
                """]"""
        )
    }

    @Test
    fun `should list no time deposits as an empty array`() {
        val response = getTimeDeposits()

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).isEqualTo("[]")
    }

    private fun getTimeDeposits(): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/time-deposits")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun insertDeposit(id: Int, planType: String, days: Int, balance: String) {
        jdbc.update(
            "INSERT INTO time_deposits (id, plan_type, days, balance) VALUES (?, ?, ?, ?)",
            id, planType, days, BigDecimal(balance),
        )
    }

    private fun insertWithdrawal(id: Int, timeDepositId: Int, amount: String, date: String) {
        jdbc.update(
            "INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES (?, ?, ?, ?)",
            id, timeDepositId, BigDecimal(amount), LocalDate.parse(date),
        )
    }
}
