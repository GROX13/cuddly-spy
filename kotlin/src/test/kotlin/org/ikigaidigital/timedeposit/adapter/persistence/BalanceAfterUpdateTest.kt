package org.ikigaidigital.timedeposit.adapter.persistence

import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositCalculator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * The write rule around the Double calculator (ADR 0015): the stored decimal plus the calculator's
 * change, rounded HALF_UP to cents. Every expected value was checked independently in Python.
 */
class BalanceAfterUpdateTest {

    @Test
    fun `should keep the stored balance itself when the calculator changed nothing`() {
        // 90071992547409.91 has no exact Double (ulp 1/64): its Double reads back as ...409.90625.
        val stored = BigDecimal("90071992547409.91")
        val before = stored.toDouble()

        assertThat(balanceAfterUpdate(stored, before, before)).isSameAs(stored)
    }

    @Test
    fun `should add the interest the calculator added to the stored balance`() {
        assertThat(balanceAfterUpdate(BigDecimal("1500.00"), 1500.0, 1501.25)).isEqualTo(BigDecimal("1501.25"))
    }

    @Test
    fun `should round binary noise in the change away`() {
        // 10.850000000000001 - 10.0 is 0.8500000000000014.
        assertThat(balanceAfterUpdate(BigDecimal("10.00"), 10.0, 10.850000000000001))
            .isEqualTo(BigDecimal("10.85"))
    }

    @Test
    fun `should round the shortest decimal form of the change half up`() {
        // 3.675 - 1.0 is the Double 2.675, stored as 2.67499999999999982236431605997495353221893310546875:
        // BigDecimal.valueOf rounds its shortest form to 2.68, where new BigDecimal(change) would give 2.67.
        assertThat(balanceAfterUpdate(BigDecimal("1.00"), 1.0, 3.675)).isEqualTo(BigDecimal("3.68"))
    }

    /**
     * Above 2^46 a Double can't hold every cent. Derived in Python by simulating binary64:
     * before = 90071992547409.90625; interest = 0.0 + before * 0.01 / 12 = 75059993789.50825500…,
     * which the calculator rounds to 75059993789.51; after = before + that = 90147052541199.421875
     * (ulp 1/64), so the change is 75059993789.515625, which rounds to 75059993789.52. That is one cent
     * above the legacy calculator's own interest, the most the ADR allows at this size.
     */
    @Test
    fun `should stay within one cent of the legacy interest on a balance too large for a Double to hold its cents`() {
        val stored = BigDecimal("90071992547409.91")
        val deposit = TimeDeposit(1, "basic", stored.toDouble(), 31)
        val before = deposit.balance
        val legacyInterest = BigDecimal("75059993789.51")

        TimeDepositCalculator().updateBalance(listOf(deposit))
        val saved = balanceAfterUpdate(stored, before, deposit.balance)

        assertThat(saved).isEqualTo(BigDecimal("90147052541199.43"))
        assertThat((saved - stored - legacyInterest).abs()).isLessThanOrEqualTo(BigDecimal("0.01"))
    }
}
