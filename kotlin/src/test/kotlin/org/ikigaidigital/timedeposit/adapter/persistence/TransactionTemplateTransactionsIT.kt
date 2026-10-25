package org.ikigaidigital.timedeposit.adapter.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.ikigaidigital.TestcontainersConfiguration
import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import org.ikigaidigital.timedeposit.application.Transactions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

/**
 * The transaction boundary over real Postgres, with the port's real methods inside it. No test
 * transaction wraps these tests, so commit and rollback are the adapter's own; balances are read back
 * with JDBC afterwards. Same context setup as the API ITs, so Spring reuses that context.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class TransactionTemplateTransactionsIT {

    @Autowired
    private lateinit var transactions: Transactions

    @Autowired
    private lateinit var repository: TimeDepositRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    @AfterEach
    fun deleteRows() {
        jdbc.update("DELETE FROM withdrawals")
        jdbc.update("DELETE FROM time_deposits")
    }

    @Test
    fun `should commit the balances saved inside the block and return its result`() {
        insertDeposit(1, "1000.00")
        insertDeposit(2, "2000.00")

        val result = transactions.inTransaction {
            val deposits = repository.loadAllForUpdate()
            deposits.forEach { it.balance += 1.0 }
            repository.saveBalances(deposits)
            "done"
        }

        // The repository's methods joined the outer read-write transaction, so the dirty balances flushed.
        assertThat(result).isEqualTo("done")
        assertThat(storedBalances()).containsExactly(BigDecimal("1001.00"), BigDecimal("2001.00"))
    }

    @Test
    fun `should roll back every saved balance when the block throws`() {
        insertDeposit(1, "1000.00")
        insertDeposit(2, "2000.00")

        assertThatIllegalStateException().isThrownBy {
            transactions.inTransaction {
                val deposits = repository.loadAllForUpdate()
                deposits.forEach { it.balance += 1.0 }
                repository.saveBalances(deposits)
                // Hibernate auto-flushes before this query, so the UPDATEs reach Postgres before the throw.
                repository.loadAllWithWithdrawals()
                error("failed after saving")
            }
        }.withMessage("failed after saving")

        assertThat(storedBalances()).containsExactly(BigDecimal("1000.00"), BigDecimal("2000.00"))
    }

    @Test
    fun `should return null from a block that returns null`() {
        assertThat(transactions.inTransaction<String?> { null }).isNull()
    }

    private fun storedBalances(): List<BigDecimal> =
        jdbc.query("SELECT balance FROM time_deposits ORDER BY id") { rs, _ -> rs.getBigDecimal("balance") }

    private fun insertDeposit(id: Int, balance: String) {
        jdbc.update(
            "INSERT INTO time_deposits (id, plan_type, days, balance) VALUES (?, 'basic', 31, ?)",
            id, BigDecimal(balance),
        )
    }
}
