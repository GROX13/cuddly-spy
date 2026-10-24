package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The local profile with compose switched off, so Testcontainers supplies the database instead.
 */
@SpringBootTest(properties = ["spring.docker.compose.enabled=false"])
@ActiveProfiles("local")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class LocalSeedIT {

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `should seed an earning and a non-earning time deposit per plan type`() {
        val deposits = jdbc.query("SELECT plan_type, days, balance FROM time_deposits ORDER BY id") { rs, _ ->
            tuple(rs.getString("plan_type"), rs.getInt("days"), rs.getBigDecimal("balance"))
        }

        assertThat(deposits).containsExactly(
            tuple("basic", 20, BigDecimal("1000.00")),
            tuple("basic", 400, BigDecimal("1500.00")),
            tuple("student", 200, BigDecimal("2000.00")),
            tuple("student", 400, BigDecimal("2500.00")),
            tuple("premium", 40, BigDecimal("5000.00")),
            tuple("premium", 90, BigDecimal("6000.00"))
        )
    }

    @Test
    fun `should seed withdrawals as history linked to their time deposits`() {
        val withdrawals = jdbc.query(
            """
            SELECT d.plan_type, d.days, w.amount, w.date
            FROM withdrawals w JOIN time_deposits d ON d.id = w.time_deposit_id
            ORDER BY w.date
            """.trimIndent()
        ) { rs, _ ->
            tuple(rs.getString("plan_type"), rs.getInt("days"), rs.getBigDecimal("amount"), rs.getDate("date").toLocalDate())
        }

        assertThat(withdrawals).containsExactly(
            tuple("basic", 400, BigDecimal("100.00"), LocalDate.of(2026, 3, 15)),
            tuple("basic", 400, BigDecimal("50.00"), LocalDate.of(2026, 6, 1)),
            tuple("premium", 90, BigDecimal("250.00"), LocalDate.of(2026, 8, 20))
        )
    }
}
