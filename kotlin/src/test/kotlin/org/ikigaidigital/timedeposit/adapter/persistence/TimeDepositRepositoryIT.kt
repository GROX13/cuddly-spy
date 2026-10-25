package org.ikigaidigital.timedeposit.adapter.persistence

import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.ikigaidigital.TestcontainersConfiguration
import org.ikigaidigital.timedeposit.application.TimeDepositSnapshot
import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.transaction.AfterTransaction
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDate
import javax.sql.DataSource

/**
 * The persistence adapter against real Postgres with the Liquibase schema. `@DataJpaTest` scans no
 * `@Repository` classes, so the adapter is imported, with the exception translation it gets in the
 * application. Each test runs in a transaction that is rolled back. Rows are inserted with JDBC on
 * that same transaction where exact ids matter (ordering), otherwise through the entities.
 */
@DataJpaTest(
    properties = [
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=" +
            "org.ikigaidigital.timedeposit.adapter.persistence.RecordingStatementInspector",
    ],
)
@Import(TestcontainersConfiguration::class, JpaTimeDepositAdapter::class)
// The slice leaves out the exception translation the full application has (ApplicationIT checks it there).
@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class TimeDepositRepositoryIT {

    @Autowired
    private lateinit var repository: TimeDepositRepository

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var dataSource: DataSource

    private val jdbc by lazy { JdbcTemplate(dataSource) }

    @BeforeEach
    fun resetRecordedStatements() {
        RecordingStatementInspector.statements.clear()
    }

    @Test
    fun `should save exact supplied balances without selecting loaded deposits again`() {
        insertDeposit(1, "basic", 31, "90071992547409.91")
        insertDeposit(2, "premium", 46, "10.80")
        repository.loadAllForUpdate()
        RecordingStatementInspector.statements.clear()

        repository.saveBalances(mapOf(1 to BigDecimal("90147052541199.43"), 2 to BigDecimal("10.85")))
        entityManager.flush()

        assertThat(RecordingStatementInspector.statements).hasSize(2).allSatisfy {
            assertThat(it).startsWith("update time_deposits set balance=")
        }
        entityManager.clear()
        assertThat(repository.loadAllWithWithdrawals().map { it.balance })
            .containsExactly(BigDecimal("90147052541199.43"), BigDecimal("10.85"))
    }

    @Test
    fun `should validate the entities against the Liquibase schema`() {
        // The real proof is that the context started at all (validate rejects any drift from Liquibase's
        // tables); this assertion only pins that validate, and not some other mode, is what ran.
        assertThat(entityManagerFactory.properties["hibernate.hbm2ddl.auto"]).isEqualTo("validate")
    }

    @Test
    fun `should serve the port from the mapping adapter behind an exception-translation proxy`() {
        // `@Repository` (ADR 0017): Spring wraps the one hand-written adapter in a proxy that
        // translates its exceptions into DataAccessExceptions.
        assertThat(AopUtils.isAopProxy(repository)).isTrue
        assertThat(AopUtils.getTargetClass(repository)).isEqualTo(JpaTimeDepositAdapter::class.java)
    }

    @Test
    fun `should load every time deposit by id with its withdrawals by date then id`() {
        // Inserted out of order on purpose: deposits 3, 1, 2; deposit 1's withdrawals so that id
        // order and date order disagree, plus two on the same date to need the id tie-break.
        insertDeposit(3, "premium", 46, "5000.00")
        insertDeposit(1, "basic", 31, "1000.83")
        insertDeposit(2, "student", 400, "2500.00")
        insertWithdrawal(10, 1, "50.00", "2026-06-01")
        insertWithdrawal(21, 1, "75.00", "2026-03-15")
        insertWithdrawal(20, 1, "100.00", "2026-03-15")
        insertWithdrawal(30, 3, "250.00", "2026-08-20")

        assertThat(repository.loadAllWithWithdrawals()).containsExactly(
            TimeDepositStatement(
                1, "basic", BigDecimal("1000.83"), 31,
                listOf(
                    Withdrawal(20, BigDecimal("100.00"), LocalDate.of(2026, 3, 15)),
                    Withdrawal(21, BigDecimal("75.00"), LocalDate.of(2026, 3, 15)),
                    Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1)),
                ),
            ),
            TimeDepositStatement(2, "student", BigDecimal("2500.00"), 400, emptyList()),
            TimeDepositStatement(
                3, "premium", BigDecimal("5000.00"), 46,
                listOf(Withdrawal(30, BigDecimal("250.00"), LocalDate.of(2026, 8, 20))),
            ),
        )
    }

    @Test
    fun `should load every time deposit with its withdrawals in a single query`() {
        val basic = persistDeposit("basic", 31, "1000.00")
        val student = persistDeposit("student", 40, "2000.00")
        persistWithdrawal(basic, "50.00", "2026-06-01")
        persistWithdrawal(student, "75.00", "2026-03-15")
        entityManager.flush()
        entityManager.clear()
        RecordingStatementInspector.statements.clear()

        val loaded = repository.loadAllWithWithdrawals()

        assertThat(loaded.map { it.withdrawals.single().amount })
            .containsExactly(BigDecimal("50.00"), BigDecimal("75.00"))
        assertThat(RecordingStatementInspector.statements).singleElement().satisfies({
            assertThat(it).containsIgnoringCase("left join withdrawals")
        })
    }

    @Test
    fun `should load the stored balance exactly, even cents a Double can't hold`() {
        // 90071992547409.91 as a Double is 90071992547409.90625; 99999999999999999.99 rounds to 1e17.
        insertDeposit(1, "basic", 0, "90071992547409.91")
        insertDeposit(2, "basic", 0, "99999999999999999.99")

        assertThat(repository.loadAllWithWithdrawals().map { it.balance })
            .containsExactly(BigDecimal("90071992547409.91"), BigDecimal("99999999999999999.99"))
    }

    @Test
    fun `should load nothing when there are no time deposits`() {
        assertThat(repository.loadAllWithWithdrawals()).isEmpty()
    }

    @Test
    fun `should load every time deposit by id for update`() {
        insertDeposit(2, "student", 400, "2500.00")
        insertDeposit(1, "basic", 31, "1000.83")
        RecordingStatementInspector.statements.clear()

        assertThat(repository.loadAllForUpdate()).containsExactly(
            TimeDepositSnapshot(1, "basic", BigDecimal("1000.83"), 31),
            TimeDepositSnapshot(2, "student", BigDecimal("2500.00"), 400),
        )
        // Hibernate 7's PostgreSQL dialect issues PESSIMISTIC_WRITE as FOR NO KEY UPDATE: the row lock
        // Postgres itself takes for an UPDATE of non-key columns. It conflicts with every other write
        // lock on the row, yet still lets foreign-key checks (FOR KEY SHARE) through.
        assertThat(RecordingStatementInspector.statements).singleElement().satisfies({
            assertThat(it).endsWith("order by tde1_0.id for no key update of tde1_0")
        })
    }

    @Test
    fun `should keep the loaded rows locked against other transactions until the transaction ends`() {
        // Committed on a separate connection: rows inserted inside the test transaction would be
        // invisible to the other transaction, so it could never contend for them.
        otherConnection { it.createStatement().execute(INSERT_COMMITTED_DEPOSIT) }
        assertThat(tryLockFromOtherTransaction()).describedAs("free before loading").isNull()

        repository.loadAllForUpdate()

        assertThat(tryLockFromOtherTransaction()).describedAs("SQLSTATE while locked").isEqualTo(LOCK_NOT_AVAILABLE)
    }

    @Test
    fun `should load exact immutable balances for update without withdrawals`() {
        insertDeposit(1, "basic", 0, "90071992547409.91")
        insertDeposit(2, "student", 400, "99999999999999999.99")
        insertWithdrawal(10, 1, "50.00", "2026-06-01")
        RecordingStatementInspector.statements.clear()

        val snapshots = repository.loadAllForUpdate()

        assertThat(snapshots).containsExactly(
            TimeDepositSnapshot(1, "basic", BigDecimal("90071992547409.91"), 0),
            TimeDepositSnapshot(2, "student", BigDecimal("99999999999999999.99"), 400),
        )
        assertThat(RecordingStatementInspector.statements).singleElement().satisfies({
            assertThat(it).doesNotContain("withdrawals")
        })
        repository.saveBalances(mapOf(1 to BigDecimal("1.00")))
        assertThat(snapshots.first().balance).isEqualTo(BigDecimal("90071992547409.91"))
    }

    @Test
    fun `should not write a time deposit whose balance did not change`() {
        // The first one's Double differs from the stored decimal, so writing it back would change the row.
        insertDeposit(1, "basic", 0, "90071992547409.91")
        insertDeposit(2, "basic", 31, "1000.00")
        val deposits = repository.loadAllForUpdate()
        RecordingStatementInspector.statements.clear()

        repository.saveBalances(mapOf(1 to deposits[0].balance, 2 to BigDecimal("1000.83")))
        entityManager.flush()

        assertThat(RecordingStatementInspector.statements)
            .containsExactly("update time_deposits set balance=? where id=?")
        assertThat(storedBalances()).containsExactly(BigDecimal("90071992547409.91"), BigDecimal("1000.83"))
    }

    @Test
    fun `should save only the balance column`() {
        val deposit = persistDeposit("basic", 31, "1000.00")
        val withdrawal = persistWithdrawal(deposit, "50.00", "2026-06-01")
        entityManager.flush()
        repository.loadAllForUpdate()
        RecordingStatementInspector.statements.clear()

        // The write interface accepts only ids and balances; plan type and days cannot be supplied.
        repository.saveBalances(mapOf(deposit.id to BigDecimal("1000.83")))
        entityManager.flush()

        assertThat(RecordingStatementInspector.statements)
            .containsExactly("update time_deposits set balance=? where id=?")
        assertThat(jdbc.queryForList("SELECT id, plan_type, days, balance FROM time_deposits")).containsExactly(
            mapOf("id" to deposit.id, "plan_type" to "basic", "days" to 31, "balance" to BigDecimal("1000.83")),
        )
        assertThat(jdbc.queryForList("SELECT id, time_deposit_id, amount, date FROM withdrawals")).containsExactly(
            mapOf(
                "id" to withdrawal.id, "time_deposit_id" to deposit.id, "amount" to BigDecimal("50.00"),
                "date" to java.sql.Date.valueOf("2026-06-01"),
            ),
        )
    }

    @Test
    fun `should leave a time deposit that is not passed in unchanged`() {
        insertDeposit(1, "basic", 31, "1000.00")
        insertDeposit(2, "student", 31, "2000.00")
        val (first, _) = repository.loadAllForUpdate()

        repository.saveBalances(mapOf(first.id to BigDecimal("1000.83")))
        entityManager.flush()

        assertThat(storedBalances()).containsExactly(BigDecimal("1000.83"), BigDecimal("2000.00"))
    }

    @Test
    fun `should reject a balance for an unknown time deposit`() {
        insertDeposit(1, "basic", 31, "1000.00")

        // The @Repository proxy translates the adapter's IllegalArgumentException.
        assertThatExceptionOfType(InvalidDataAccessApiUsageException::class.java)
            .isThrownBy { repository.saveBalances(mapOf(42 to BigDecimal("1.00"))) }
            .withMessage("No time deposit with id 42")
    }

    @AfterTransaction
    fun deleteCommittedRows() {
        otherConnection { it.createStatement().execute("DELETE FROM time_deposits WHERE id = $COMMITTED_ID") }
    }

    /**
     * Asks, without waiting, for the lock a concurrent [TimeDepositRepository.loadAllForUpdate] would
     * take on the committed row; null when granted, else the SQLSTATE.
     */
    private fun tryLockFromOtherTransaction(): String? = otherConnection { connection ->
        connection.autoCommit = false
        try {
            connection.createStatement()
                .executeQuery("SELECT id FROM time_deposits WHERE id = $COMMITTED_ID FOR NO KEY UPDATE NOWAIT").close()
            null
        } catch (e: SQLException) {
            e.sqlState
        } finally {
            connection.rollback()
        }
    }

    /** A pooled connection outside the test transaction, so its work is a transaction of its own. */
    private fun <T> otherConnection(block: (Connection) -> T): T = dataSource.connection.use(block)

    private fun storedBalances(): List<BigDecimal> =
        jdbc.query("SELECT balance FROM time_deposits ORDER BY id") { rs, _ -> rs.getBigDecimal("balance") }

    /** Fixture through the entities, with generated ids; persist only, so nothing is cached in the port. */
    private fun persistDeposit(planType: String, days: Int, balance: String) =
        entityManager.persist(TimeDepositEntity(0, planType, days, BigDecimal(balance)))

    private fun persistWithdrawal(deposit: TimeDepositEntity, amount: String, date: String) =
        entityManager.persist(WithdrawalEntity(0, deposit, BigDecimal(amount), LocalDate.parse(date)))

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
        const val COMMITTED_ID = 99
        const val INSERT_COMMITTED_DEPOSIT =
            "INSERT INTO time_deposits (id, plan_type, days, balance) VALUES ($COMMITTED_ID, 'basic', 31, 1000.00)"

        /** Postgres `lock_not_available`, raised by NOWAIT when another transaction holds the row lock. */
        const val LOCK_NOT_AVAILABLE = "55P03"
    }
}
