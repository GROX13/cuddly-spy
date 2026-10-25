package org.ikigaidigital.timedeposit.application

import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.timedeposit.domain.TimeDepositCalculator
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Expected balances come from the legacy rules, checked in jshell: basic 1000.00 at 31 days earns
 * 1000 * 0.01 / 12 = 0.8333… -> 0.83; student at 400 days is past its eligible days and earns nothing.
 */
class TimeDepositServiceTest {

    private val events = mutableListOf<String>()
    private val withdrawal = Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1))
    private val repository = InMemoryTimeDepositRepository(
        listOf(
            TimeDepositStatement(1, "basic", BigDecimal("1000.00"), 31, listOf(withdrawal)),
            TimeDepositStatement(2, "student", BigDecimal("2500.00"), 400, emptyList()),
        ),
        events,
    )
    private val service = TimeDepositService(repository, TimeDepositCalculator())

    @Test
    fun `should list the time deposits exactly as the repository returns them`() {
        // Not sorted by id: the repository owns the order, and the service must keep it.
        val premium = TimeDepositStatement(3, "premium", BigDecimal("6000.00"), 90, emptyList())
        val basic = TimeDepositStatement(
            1, "basic", BigDecimal("1500.00"), 400,
            listOf(
                Withdrawal(11, BigDecimal("100.00"), LocalDate.of(2026, 3, 15)),
                Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1)),
            ),
        )
        val service = TimeDepositService(InMemoryTimeDepositRepository(listOf(premium, basic)), TimeDepositCalculator())

        assertThat(service.listAll()).containsExactly(premium, basic)
    }

    @Test
    fun `should list nothing when there are no time deposits`() {
        assertThat(TimeDepositService(InMemoryTimeDepositRepository(), TimeDepositCalculator()).listAll()).isEmpty()
    }

    @Test
    fun `should save one month of interest on every time deposit`() {
        service.updateAllBalances()

        assertThat(repository.deposits.map { it.id to it.balance }).containsExactly(
            1 to BigDecimal("1000.83"),
            2 to BigDecimal("2500.00"),
        )
    }

    @Test
    fun `should return the time deposits re-read after saving, with their withdrawals`() {
        assertThat(service.updateAllBalances()).containsExactly(
            TimeDepositStatement(1, "basic", BigDecimal("1000.83"), 31, listOf(withdrawal)),
            TimeDepositStatement(2, "student", BigDecimal("2500.00"), 400, emptyList()),
        )
    }

    @Test
    fun `should lock, calculate, save and re-read in that order`() {
        service.updateAllBalances()

        // The saved balances already carry the interest, so the calculator ran between load and save.
        // The transaction around these calls is checked against real Postgres in TimeDepositServiceTransactionIT.
        assertThat(events).containsExactly(
            "loadAllForUpdate",
            "saveBalances[1000.83, 2500.0]",
            "loadAllWithWithdrawals",
        )
    }

    @Test
    fun `should return nothing when there are no time deposits`() {
        repository.deposits = emptyList()

        assertThat(service.updateAllBalances()).isEmpty()
        assertThat(events).containsExactly("loadAllForUpdate", "saveBalances[]", "loadAllWithWithdrawals")
    }
}
