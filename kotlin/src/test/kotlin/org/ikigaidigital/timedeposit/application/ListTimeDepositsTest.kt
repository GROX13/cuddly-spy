package org.ikigaidigital.timedeposit.application

import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ListTimeDepositsTest {

    @Test
    fun `should list the time deposits exactly as the repository returns them`() {
        // Not sorted by id: the repository owns the order, and the use case must keep it.
        val premium = TimeDepositWithWithdrawals(TimeDeposit(3, "premium", 6000.0, 90), emptyList())
        val basic = TimeDepositWithWithdrawals(
            TimeDeposit(1, "basic", 1500.0, 400),
            listOf(
                Withdrawal(11, BigDecimal("100.00"), LocalDate.of(2026, 3, 15)),
                Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1)),
            ),
        )
        val listTimeDeposits = ListTimeDeposits(InMemoryTimeDepositRepository(listOf(premium, basic)))

        assertThat(listTimeDeposits.execute()).containsExactly(premium, basic)
    }

    @Test
    fun `should list nothing when there are no time deposits`() {
        assertThat(ListTimeDeposits(InMemoryTimeDepositRepository()).execute()).isEmpty()
    }
}
