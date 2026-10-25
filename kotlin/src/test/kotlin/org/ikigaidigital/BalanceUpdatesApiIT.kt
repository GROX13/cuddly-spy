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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `POST /time-deposits/balance-updates` end to end: real HTTP, real Postgres with the Liquibase
 * schema. The balances are read back with JDBC, outside the application's transaction, so a change
 * that was never flushed or committed fails here.
 *
 * Expected balances were derived independently in jshell from the original legacy calculator (the
 * if/else version before ADR 0009), one month per call (ADR 0008):
 * basic 1500.00/400 days -> 1501.25 -> 1502.50; student 2000.00/200 days -> 2005.00 -> 2010.01;
 * premium 6000.00/90 days -> 6025.00 -> 6050.10; basic/20, student/400 and premium/40 earn nothing.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class BalanceUpdatesApiIT {

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
    fun `should add one month of interest to every time deposit and return them all`() {
        insertSeedLikeDeposits()

        val response = postBalanceUpdates()

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json")
        assertThat(response.body()).isEqualTo(
            """[""" +
                """{"id":1,"planType":"basic","balance":1000.00,"days":20,"withdrawals":[]},""" +
                """{"id":2,"planType":"basic","balance":1501.25,"days":400,"withdrawals":[""" +
                """{"id":11,"amount":100.00,"date":"2026-03-15"},""" +
                """{"id":10,"amount":50.00,"date":"2026-06-01"}""" +
                """]},""" +
                """{"id":3,"planType":"student","balance":2005.00,"days":200,"withdrawals":[]},""" +
                """{"id":4,"planType":"student","balance":2500.00,"days":400,"withdrawals":[]},""" +
                """{"id":5,"planType":"premium","balance":5000.00,"days":40,"withdrawals":[]},""" +
                """{"id":6,"planType":"premium","balance":6025.00,"days":90,"withdrawals":[""" +
                """{"id":12,"amount":250.00,"date":"2026-08-20"}""" +
                """]}""" +
                """]"""
        )
        assertThat(storedBalances()).containsExactly(
            BigDecimal("1000.00"), BigDecimal("1501.25"), BigDecimal("2005.00"),
            BigDecimal("2500.00"), BigDecimal("5000.00"), BigDecimal("6025.00"),
        )
    }

    @Test
    fun `should compound when balances are updated twice in a row`() {
        insertSeedLikeDeposits()

        postBalanceUpdates()
        val second = postBalanceUpdates()

        assertThat(second.statusCode()).isEqualTo(200)
        assertThat(storedBalances()).containsExactly(*TWO_MONTHS)
        assertThat(second.body()).contains(
            """{"id":2,"planType":"basic","balance":1502.50,""",
            """{"id":3,"planType":"student","balance":2010.01,""",
            """{"id":6,"planType":"premium","balance":6050.10,""",
        )
        assertThat(second.body()).isEqualTo(getTimeDeposits().body())
    }

    /**
     * Two updates sent at the same moment must add two months, not one: the row lock taken by
     * `loadAllForUpdate` makes the second wait for the first to commit, then read its balances.
     * Without the lock both would read the same balances and one month would be lost. Repeated in
     * rounds, each from fresh rows, so a lost update can't slip through on lucky timing.
     */
    @Test
    fun `should add exactly two months when two updates run concurrently`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(CONCURRENT_ROUNDS) { round ->
                deleteRows()
                insertSeedLikeDeposits()
                val start = CountDownLatch(1)
                val calls = List(2) { pool.submit<HttpResponse<String>> { start.await(); postBalanceUpdates() } }

                start.countDown()
                val responses = calls.map { it.get(30, TimeUnit.SECONDS) }

                assertThat(responses.map { it.statusCode() }).describedAs("round $round").containsOnly(200)
                assertThat(storedBalances()).describedAs("round $round").containsExactly(*TWO_MONTHS)
                // Each response is re-read in its own transaction: one shows the first month, one both.
                assertThat(responses.map { basicBalanceIn(it.body()) }).describedAs("round $round")
                    .containsExactlyInAnyOrder("1501.25", "1502.50")
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * Basic at day 0 earns nothing. The Double the calculator gets, 90071992547409.90625, can't hold
     * the cents (ulp 1/64), yet the stored decimal must come back and stay exactly as it was (ADR 0015).
     */
    @Test
    fun `should keep cents a Double can't hold on a time deposit that earns nothing`() {
        insertDeposit(1, "basic", 0, "90071992547409.91")
        val expected = """[{"id":1,"planType":"basic","balance":90071992547409.91,"days":0,"withdrawals":[]}]"""

        assertThat(getTimeDeposits().body()).isEqualTo(expected)
        val response = postBalanceUpdates()

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).isEqualTo(expected)
        assertThat(storedBalances()).containsExactly(BigDecimal("90071992547409.91"))
    }

    /** NUMERIC(19,2)'s maximum, whose Double is 1e17: writing that back would overflow the column. */
    @Test
    fun `should leave the largest storable balance unchanged when it earns nothing`() {
        insertDeposit(1, "basic", 0, "99999999999999999.99")
        val expected = """[{"id":1,"planType":"basic","balance":99999999999999999.99,"days":0,"withdrawals":[]}]"""

        assertThat(getTimeDeposits().body()).isEqualTo(expected)
        val response = postBalanceUpdates()

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).isEqualTo(expected)
        assertThat(storedBalances()).containsExactly(BigDecimal("99999999999999999.99"))
    }

    /**
     * The documented limit (ADR 0015): a balance that earns past NUMERIC(19,2)'s maximum overflows the
     * column, and the whole batch fails and rolls back, the normal deposit next to it included.
     */
    @Test
    fun `should fail the whole update and roll it back when an earning balance would overflow the column`() {
        insertDeposit(1, "basic", 400, "1500.00")
        insertDeposit(2, "basic", 400, "99999999999999999.99")

        val response = postBalanceUpdates()

        assertThat(response.statusCode()).isEqualTo(500)
        assertThat(storedBalances()).containsExactly(BigDecimal("1500.00"), BigDecimal("99999999999999999.99"))
    }

    @Test
    fun `should return an empty array when there are no time deposits`() {
        val response = postBalanceUpdates()

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).isEqualTo("[]")
    }

    private fun basicBalanceIn(body: String): String? =
        Regex(""""id":2,"planType":"basic","balance":([0-9.]+),""").find(body)?.groupValues?.get(1)

    private fun getTimeDeposits(): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/time-deposits")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun postBalanceUpdates(): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/time-deposits/balance-updates"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun storedBalances(): List<BigDecimal> =
        jdbc.query("SELECT balance FROM time_deposits ORDER BY id") { rs, _ -> rs.getBigDecimal("balance") }

    /** The six rows of the local seed (003-seed-local.yaml) with fixed ids, plus its three withdrawals. */
    private fun insertSeedLikeDeposits() {
        insertDeposit(1, "basic", 20, "1000.00")
        insertDeposit(2, "basic", 400, "1500.00")
        insertDeposit(3, "student", 200, "2000.00")
        insertDeposit(4, "student", 400, "2500.00")
        insertDeposit(5, "premium", 40, "5000.00")
        insertDeposit(6, "premium", 90, "6000.00")
        insertWithdrawal(10, 2, "50.00", "2026-06-01")
        insertWithdrawal(11, 2, "100.00", "2026-03-15")
        insertWithdrawal(12, 6, "250.00", "2026-08-20")
    }

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

    private companion object {
        const val CONCURRENT_ROUNDS = 10

        /** The six seed-like rows after two monthly updates, by id. */
        val TWO_MONTHS = arrayOf(
            BigDecimal("1000.00"), BigDecimal("1502.50"), BigDecimal("2010.01"),
            BigDecimal("2500.00"), BigDecimal("5000.00"), BigDecimal("6050.10"),
        )
    }
}
