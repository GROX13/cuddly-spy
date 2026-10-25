package org.ikigaidigital.timedeposit.application

import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositCalculator
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Expected balances come from the legacy rules, checked in jshell: basic 1000.00 at 31 days earns
 * 1000 * 0.01 / 12 = 0.8333… -> 0.83; student at 400 days is past its eligible days and earns nothing.
 */
class UpdateAllBalancesTest {

    private val events = mutableListOf<String>()
    private val withdrawal = Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1))
    private val repository = InMemoryTimeDepositRepository(
        listOf(
            TimeDepositWithWithdrawals(TimeDeposit(1, "basic", 1000.0, 31), listOf(withdrawal)),
            TimeDepositWithWithdrawals(TimeDeposit(2, "student", 2500.0, 400), emptyList()),
        ),
        events,
    )
    private val updateAllBalances = UpdateAllBalances(repository, TimeDepositCalculator())

    @Test
    fun `should save one month of interest on every time deposit`() {
        updateAllBalances.execute()

        assertThat(repository.deposits.map { it.deposit }).containsExactly(
            TimeDeposit(1, "basic", 1000.83, 31),
            TimeDeposit(2, "student", 2500.0, 400),
        )
    }

    @Test
    fun `should return the time deposits re-read after saving, with their withdrawals`() {
        assertThat(updateAllBalances.execute()).containsExactly(
            TimeDepositWithWithdrawals(TimeDeposit(1, "basic", 1000.83, 31), listOf(withdrawal)),
            TimeDepositWithWithdrawals(TimeDeposit(2, "student", 2500.0, 400), emptyList()),
        )
    }

    @Test
    fun `should lock, calculate, save and re-read in that order`() {
        updateAllBalances.execute()

        // The saved balances already carry the interest, so the calculator ran between load and save.
        // The transaction around these calls is checked against real Postgres in UpdateAllBalancesTransactionIT.
        assertThat(events).containsExactly(
            "loadAllForUpdate",
            "saveBalances[1000.83, 2500.0]",
            "loadAllWithWithdrawals",
        )
    }

    @Test
    fun `should return nothing when there are no time deposits`() {
        repository.deposits = emptyList()

        assertThat(updateAllBalances.execute()).isEmpty()
        assertThat(events).containsExactly("loadAllForUpdate", "saveBalances[]", "loadAllWithWithdrawals")
    }
}
