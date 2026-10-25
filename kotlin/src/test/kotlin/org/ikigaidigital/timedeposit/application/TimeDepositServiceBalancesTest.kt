package org.ikigaidigital.timedeposit.application

import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.timedeposit.domain.TimeDepositCalculator
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** Literal results pin ADR 0015's existing behavior, including its large-balance limitation. */
class TimeDepositServiceBalancesTest {

    @Test
    fun `should add monthly interest to the exact stored balance`() {
        assertUpdatedBalance("basic", 31, "1500.00", "1501.25")
    }

    @Test
    fun `should round binary noise out of the balance change`() {
        assertUpdatedBalance("premium", 46, "10.80", "10.85")
    }

    @Test
    fun `should preserve positive half cent rounding`() {
        assertUpdatedBalance("basic", 31, "150.00", "150.13")
    }

    @Test
    fun `should preserve negative half cent rounding`() {
        assertUpdatedBalance("basic", 31, "-150.00", "-150.13")
    }

    @Test
    fun `should preserve legacy rounding below an apparent half cent`() {
        assertUpdatedBalance("basic", 31, "30.00", "30.02")
    }

    @Test
    fun `should leave the balance unchanged when interest rounds to zero`() {
        assertUpdatedBalance("basic", 31, "0.01", "0.01")
    }

    @Test
    fun `should preserve cents a Double cannot hold when no interest is earned`() {
        assertUpdatedBalance("basic", 0, "90071992547409.91", "90071992547409.91")
    }

    @Test
    fun `should preserve the maximum stored balance when no interest is earned`() {
        assertUpdatedBalance("student", 400, "99999999999999999.99", "99999999999999999.99")
    }

    @Test
    fun `should preserve the existing delta result for a large earning balance`() {
        // The recovered Double delta is .52 interest, not the calculator's .51 (ADR 0015).
        assertUpdatedBalance("basic", 31, "90071992547409.91", "90147052541199.43")
    }

    private fun assertUpdatedBalance(planType: String, days: Int, stored: String, expected: String) {
        val repository = InMemoryTimeDepositRepository(
            listOf(TimeDepositStatement(1, planType, BigDecimal(stored), days, emptyList())),
        )
        val service = TimeDepositService(repository, TimeDepositCalculator())

        val result = service.updateAllBalances()

        assertThat(result.single().balance).isEqualTo(BigDecimal(expected))
        assertThat(service.listAll()).isEqualTo(result)
    }
}
