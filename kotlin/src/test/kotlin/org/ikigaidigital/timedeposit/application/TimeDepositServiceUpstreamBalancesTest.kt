package org.ikigaidigital.timedeposit.application

import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.TimeDepositCalculator
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random
import org.ikigaidigital.upstream.TimeDeposit as UpstreamTimeDeposit
import org.ikigaidigital.upstream.TimeDepositCalculator as UpstreamTimeDepositCalculator

/**
 * The stored balance after one update is the upstream calculator's result rounded to cents, for every
 * balance below 2^45 (about 35 trillion), where a Double still holds each cent. Larger balances are a
 * documented limit (ADR 0015) and are not pinned.
 */
class TimeDepositServiceUpstreamBalancesTest {

    @Test
    fun `should store the upstream calculator's balance rounded to cents below 2^45`() {
        val random = Random(SEED)

        val mismatches = (1..CASES).mapNotNull {
            val stored = BigDecimal.valueOf(random.nextLong(-MAX_CENTS, MAX_CENTS), 2)
            val planType = PLAN_CODES.random(random)
            val days = random.nextInt(0, 500)
            val actual = updatedBalance(stored, planType, days)
            val expected = upstreamBalance(stored, planType, days)
            if (actual.compareTo(expected) == 0) null else "$planType $stored at $days days: $actual, upstream $expected"
        }

        assertThat(mismatches).isEmpty()
    }

    private fun updatedBalance(stored: BigDecimal, planType: String, days: Int): BigDecimal {
        val repository = InMemoryTimeDepositRepository(listOf(TimeDepositStatement(1, planType, stored, days, emptyList())))
        return TimeDepositService(repository, TimeDepositCalculator()).updateAllBalances().single().balance
    }

    private fun upstreamBalance(stored: BigDecimal, planType: String, days: Int): BigDecimal {
        val deposit = UpstreamTimeDeposit(1, planType, stored.toDouble(), days)
        UpstreamTimeDepositCalculator().updateBalance(listOf(deposit))
        return BigDecimal.valueOf(deposit.balance).setScale(2, RoundingMode.HALF_UP)
    }

    private companion object {
        const val SEED = 20261026
        const val CASES = 100_000

        /** Leaves room for a month of premium interest without crossing 2^45. */
        const val MAX_CENTS = 3_480_000_000_000_000L

        val PLAN_CODES = listOf("basic", "student", "premium", "gold")
    }
}
