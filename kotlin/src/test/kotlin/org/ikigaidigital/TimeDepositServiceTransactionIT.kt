package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import org.ikigaidigital.timedeposit.application.TimeDepositService
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

/**
 * `@Transactional` on [TimeDepositService] over real Postgres (ADR 0007). No test transaction wraps
 * these tests, so commit and rollback are the service's own; balances are read back with JDBC.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class TimeDepositServiceTransactionIT {

    /** Delegates to the real repository, but fails right after the balances are saved and flushed. */
    class FailingAfterSaveRepository(private val delegate: TimeDepositRepository) : TimeDepositRepository by delegate {
        override fun loadAllWithWithdrawals(): List<TimeDepositStatement> {
            delegate.loadAllWithWithdrawals() // Hibernate flushes the UPDATEs before this query
            error("failed after saving")
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FailingService {
        /** Not an autowire candidate, so the controller still gets the one real service. */
        @Bean(autowireCandidate = false)
        fun failingTimeDepositService(repository: TimeDepositRepository, calculator: TimeDepositCalculator) =
            TimeDepositService(FailingAfterSaveRepository(repository), calculator)
    }

    @Autowired
    private lateinit var service: TimeDepositService

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    @AfterEach
    fun deleteRows() {
        jdbc.update("DELETE FROM withdrawals")
        jdbc.update("DELETE FROM time_deposits")
    }

    @Test
    fun `should run the service through a transactional proxy`() {
        assertThat(AopUtils.isAopProxy(service)).isTrue()
    }

    @Test
    fun `should roll back every saved balance when the update fails`() {
        insertDeposit(1, "basic", 31, "1000.00")
        insertDeposit(2, "student", 31, "2000.00")
        val failing = context.getBean("failingTimeDepositService", TimeDepositService::class.java)

        assertThatIllegalStateException().isThrownBy { failing.updateAllBalances() }.withMessage("failed after saving")

        assertThat(storedBalances()).containsExactly(BigDecimal("1000.00"), BigDecimal("2000.00"))
    }

    private fun storedBalances(): List<BigDecimal> =
        jdbc.query("SELECT balance FROM time_deposits ORDER BY id") { rs, _ -> rs.getBigDecimal("balance") }

    private fun insertDeposit(id: Int, planType: String, days: Int, balance: String) {
        jdbc.update(
            "INSERT INTO time_deposits (id, plan_type, days, balance) VALUES (?, ?, ?, ?)",
            id, planType, days, BigDecimal(balance),
        )
    }
}
