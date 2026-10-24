package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
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
}
