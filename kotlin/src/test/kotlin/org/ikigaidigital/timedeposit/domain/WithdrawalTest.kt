package org.ikigaidigital.timedeposit.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class WithdrawalTest {

    @Test
    fun `should treat withdrawals with equal values as equal`() {
        val withdrawal = Withdrawal(20, BigDecimal("100.00"), LocalDate.of(2026, 3, 15))

        assertThat(withdrawal).isEqualTo(Withdrawal(20, BigDecimal("100.00"), LocalDate.of(2026, 3, 15)))
        assertThat(withdrawal).isNotEqualTo(Withdrawal(20, BigDecimal("50.00"), LocalDate.of(2026, 3, 15)))
    }

    @Test
    fun `should keep a time deposit with its withdrawals in the given order`() {
        val first = Withdrawal(20, BigDecimal("100.00"), LocalDate.of(2026, 3, 15))
        val second = Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1))

        val loaded = TimeDepositWithWithdrawals(TimeDeposit(1, "basic", 1000.83, 31), listOf(first, second))

        assertThat(loaded.deposit).isEqualTo(TimeDeposit(1, "basic", 1000.83, 31))
        assertThat(loaded.withdrawals).containsExactly(
            Withdrawal(20, BigDecimal("100.00"), LocalDate.of(2026, 3, 15)),
            Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1)),
        )
    }
}
