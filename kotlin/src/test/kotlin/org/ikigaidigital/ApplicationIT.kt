package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList
import org.springframework.jdbc.core.queryForObject
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIT {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `should start the application context`() {
        assertThat(context.getBean<TimeDepositApplication>()).isNotNull
    }

    @Test
    fun `should create the schema with empty tables`() {
        val tables = jdbc.queryForList<String>(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
        )

        assertThat(tables).contains("time_deposits", "withdrawals")
        assertThat(jdbc.queryForObject<Int>("SELECT count(*) FROM time_deposits")).isZero
        assertThat(jdbc.queryForObject<Int>("SELECT count(*) FROM withdrawals")).isZero
    }

    /**
     * The full application puts the `@Repository` adapter behind Spring's exception translation
     * (ADR 0006); `TimeDepositRepositoryIT` imports the same auto-configuration into its slice.
     */
    @Test
    fun `should translate the persistence adapter's exceptions`() {
        assertThatExceptionOfType(InvalidDataAccessApiUsageException::class.java)
            .isThrownBy { context.getBean<TimeDepositRepository>().saveBalances(mapOf(42 to BigDecimal("1.00"))) }
            .withMessage("No time deposit with id 42")
    }
}
