package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random
import org.ikigaidigital.upstream.TimeDeposit as UpstreamTimeDeposit
import org.ikigaidigital.upstream.TimeDepositCalculator as UpstreamTimeDepositCalculator

/**
 * Runs the calculator and the upstream original (`org.ikigaidigital.upstream`, copied from c4ea358 with
 * only the package line changed) on the same seeded inputs, and compares balances bit for bit and
 * exception types. The characterization tests pin chosen rows; this covers the space between them.
 */
class TimeDepositCalculatorDifferentialTest {

    @Test
    fun `should match the upstream calculator on every special balance, plan code and boundary day`() {
        val mismatches = SPECIAL_BALANCES.flatMap { balance ->
            PLAN_CODES.flatMap { code -> BOUNDARY_DAYS.mapNotNull { days -> mismatch(listOf(Input(balance, code, days)), 1) } }
        }

        assertThat(mismatches).isEmpty()
    }

    @Test
    fun `should match the upstream calculator on random balances, days and repeated calls`() {
        val random = Random(SEED)

        val mismatches = (1..RANDOM_CASES).mapNotNull {
            val input = Input(randomBalance(random), PLAN_CODES.random(random), randomDays(random))
            mismatch(listOf(input), times = if (random.nextInt(10) == 0) random.nextInt(2, 30) else 1)
        }

        assertThat(mismatches).isEmpty()
    }

    @Test
    fun `should match the upstream calculator on lists, including an aliased entry`() {
        val random = Random(SEED)

        val mismatches = (1..LIST_CASES).mapNotNull {
            val inputs = List(random.nextInt(0, 6)) { Input(randomBalance(random), PLAN_CODES.random(random), randomDays(random)) }
            mismatch(inputs, times = 1, aliasFirst = true)
        }

        assertThat(mismatches).isEmpty()
    }

    private data class Input(val balance: Double, val planType: String, val days: Int)

    /** A description of the first difference, or null when both calculators agree. */
    private fun mismatch(inputs: List<Input>, times: Int, aliasFirst: Boolean = false): String? {
        val upstream = inputs.map { UpstreamTimeDeposit(1, it.planType, it.balance, it.days) }
        val current = inputs.map { TimeDeposit(1, it.planType, it.balance, it.days) }
        val upstreamError = runCatching {
            repeat(times) { UpstreamTimeDepositCalculator().updateBalance(if (aliasFirst) upstream + upstream.take(1) else upstream) }
        }.exceptionOrNull()?.javaClass
        val currentError = runCatching {
            repeat(times) { TimeDepositCalculator().updateBalance(if (aliasFirst) current + current.take(1) else current) }
        }.exceptionOrNull()?.javaClass
        val upstreamBits = upstream.map { it.balance.toRawBits() }
        val currentBits = current.map { it.balance.toRawBits() }

        return if (upstreamError == currentError && upstreamBits == currentBits) null
        else "$inputs x$times: upstream ${upstream.map { it.balance }} $upstreamError, current ${current.map { it.balance }} $currentError"
    }

    private fun randomBalance(random: Random): Double = when (random.nextInt(6)) {
        0 -> random.nextLong(-100_000_000, 100_000_000) / 100.0
        1 -> random.nextDouble(-1e6, 1e6)
        2 -> random.nextLong(0, 9_999_999_999_999_999L) / 100.0
        3 -> Math.scalb(random.nextDouble(), random.nextInt(-1074, 1024)) * if (random.nextBoolean()) 1 else -1
        4 -> SPECIAL_BALANCES.random(random)
        else -> Double.fromBits(random.nextLong())
    }

    private fun randomDays(random: Random): Int =
        if (random.nextBoolean()) BOUNDARY_DAYS.random(random) else random.nextInt(-10, 2000)

    private companion object {
        const val SEED = 20261025
        const val RANDOM_CASES = 200_000
        const val LIST_CASES = 20_000

        val PLAN_CODES = listOf("basic", "student", "premium", "Basic", "PREMIUM", "gold", "", " basic", "premium ")
        val BOUNDARY_DAYS = listOf(Int.MIN_VALUE, -1, 0, 1, 29, 30, 31, 32, 44, 45, 46, 47, 364, 365, 366, 367, Int.MAX_VALUE)
        val SPECIAL_BALANCES = listOf(
            0.0, -0.0, 0.01, -0.01, 0.005, 1e-300, Double.MIN_VALUE, -Double.MIN_VALUE, 5.99, 6.0, 10.0, 10.8, 18.0,
            30.0, 150.0, -150.0, 1e13, 7.03e13, 9.0071992547409e13, 1e15, 1e17, 1e300, Double.MAX_VALUE,
            -Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        )
    }
}
