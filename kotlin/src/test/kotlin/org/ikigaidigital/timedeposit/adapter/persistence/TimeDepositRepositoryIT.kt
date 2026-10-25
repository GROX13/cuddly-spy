package org.ikigaidigital.timedeposit.adapter.persistence

import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.ikigaidigital.TestcontainersConfiguration
import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
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
 * The persistence adapter against real Postgres with the Liquibase schema. Each test runs in a
 * transaction that is rolled back. Rows are inserted with JDBC on that same transaction where exact
 * ids matter (ordering), otherwise through the entities.
 */
@DataJpaTest(
    properties = [
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=" +
            "org.ikigaidigital.timedeposit.adapter.persistence.RecordingStatementInspector",
    ],
)
@Import(TestcontainersConfiguration::class)
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
    fun `should validate the entities against the Liquibase schema`() {
        // The real proof is that the context started at all (validate rejects any drift from Liquibase's
        // tables); this assertion only pins that validate, and not some other mode, is what ran.
        assertThat(entityManagerFactory.properties["hibernate.hbm2ddl.auto"]).isEqualTo("validate")
    }

    @Test
    fun `should serve the port from the Spring Data repository proxy`() {
        // No hand-written adapter (ADR 0010): the port bean is Spring Data's JDK proxy of the
        // repository interface, which runs the port's Kotlin default methods.
        assertThat(AopUtils.isJdkDynamicProxy(repository)).isTrue
        assertThat(repository).isInstanceOf(TimeDepositJpaRepository::class.java)
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
            TimeDepositWithWithdrawals(
                TimeDeposit(1, "basic", 1000.83, 31),
                listOf(
                    Withdrawal(20, BigDecimal("100.00"), LocalDate.of(2026, 3, 15)),
                    Withdrawal(21, BigDecimal("75.00"), LocalDate.of(2026, 3, 15)),
                    Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1)),
                ),
            ),
            TimeDepositWithWithdrawals(TimeDeposit(2, "student", 2500.0, 400), emptyList()),
            TimeDepositWithWithdrawals(
                TimeDeposit(3, "premium", 5000.0, 46),
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
    fun `should load nothing when there are no time deposits`() {
        assertThat(repository.loadAllWithWithdrawals()).isEmpty()
    }

    @Test
    fun `should load every time deposit by id for update`() {
        insertDeposit(2, "student", 400, "2500.00")
        insertDeposit(1, "basic", 31, "1000.83")
        RecordingStatementInspector.statements.clear()

        assertThat(repository.loadAllForUpdate()).containsExactly(
            TimeDeposit(1, "basic", 1000.83, 31),
            TimeDeposit(2, "student", 2500.0, 400),
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
    fun `should save balances rounded half up to two places`() {
        insertDeposit(1, "basic", 31, "10.00")
        insertDeposit(2, "student", 31, "1000.00")
        insertDeposit(3, "premium", 46, "2.00")
        val deposits = repository.loadAllForUpdate()
        // Checked independently in jshell. 10.850000000000001 is a typical floating-point artifact.
        // 2.675 is stored as 2.67499999999999982236431605997495353221893310546875, so only rounding
        // the shortest decimal form (BigDecimal.valueOf) gives 2.68; new BigDecimal(2.675) gives 2.67.
        deposits[0].balance = 10.850000000000001
        deposits[1].balance = 1000.835
        deposits[2].balance = 2.675

        repository.saveBalances(deposits)
        entityManager.flush()

        assertThat(storedBalances()).containsExactly(
            BigDecimal("10.85"),
            BigDecimal("1000.84"),
            BigDecimal("2.68"),
        )
    }

    @Test
    fun `should save only the balance column`() {
        val deposit = persistDeposit("basic", 31, "1000.00")
        val withdrawal = persistWithdrawal(deposit, "50.00", "2026-06-01")
        entityManager.flush()
        repository.loadAllForUpdate()
        RecordingStatementInspector.statements.clear()

        // Only the id and balance are read: a different plan type or days must not reach the row.
        repository.saveBalances(listOf(TimeDeposit(deposit.id, "premium", 1000.83, 999)))
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
        first.balance = 1000.83

        repository.saveBalances(listOf(first))
        entityManager.flush()

        assertThat(storedBalances()).containsExactly(BigDecimal("1000.83"), BigDecimal("2000.00"))
    }

    @Test
    fun `should reject a balance for an unknown time deposit`() {
        insertDeposit(1, "basic", 31, "1000.00")

        // The repository proxy translates the default method's IllegalArgumentException.
        assertThatExceptionOfType(InvalidDataAccessApiUsageException::class.java)
            .isThrownBy { repository.saveBalances(listOf(TimeDeposit(42, "basic", 1.0, 31))) }
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
