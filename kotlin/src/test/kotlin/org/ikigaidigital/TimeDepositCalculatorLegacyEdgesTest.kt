package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

/** Literals and exception types captured from upstream c4ea3585; these are calculator inputs, not HTTP inputs. */
class TimeDepositCalculatorLegacyEdgesTest {
    private val calculator = TimeDepositCalculator()

    @Test
    fun `should turn negative zero into positive zero even when no interest is earned`() {
        for (planType in listOf("basic", "student", "premium", "unknown")) {
            for (days in listOf(0, 100)) {
                val deposit = TimeDeposit(1, planType, -0.0, days)

                calculator.updateBalance(listOf(deposit))

                assertThat(deposit.balance.toRawBits()).describedAs("%s at %s days", planType, days).isZero()
            }
        }
    }

    @Test
    fun `should reject non-finite earning balances without changing that balance`() {
        for (planType in listOf("basic", "student", "premium")) {
            for (balance in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                val deposit = TimeDeposit(1, planType, balance, 100)

                assertThatThrownBy { calculator.updateBalance(listOf(deposit)) }
                    .describedAs("%s with %s", planType, balance)
                    .isInstanceOf(NumberFormatException::class.java)
                assertThat(deposit.balance.toRawBits()).isEqualTo(balance.toRawBits())
            }
        }
    }

    @Test
    fun `should preserve non-finite balances when no interest is earned`() {
        for ((planType, days) in listOf("basic" to 0, "student" to 0, "premium" to 0, "unknown" to 100)) {
            for (balance in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                val deposit = TimeDeposit(1, planType, balance, days)

                calculator.updateBalance(listOf(deposit))

                assertThat(deposit.balance.toRawBits()).describedAs("%s with %s", planType, balance)
                    .isEqualTo(balance.toRawBits())
            }
        }
    }

    @Test
    fun `should reject a null list passed by a Java caller`() {
        // Reflection supplies Java's null without adding a Java source root or weakening Kotlin types.
        val method = TimeDepositCalculator::class.java.getMethod("updateBalance", List::class.java)

        assertThatThrownBy { method.invoke(calculator, null as Any?) }
            .isInstanceOf(InvocationTargetException::class.java)
            .hasCauseInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun `should retain earlier mutations and stop at a null element passed by a Java caller`() {
        val first = TimeDeposit(1, "basic", 1000.0, 31)
        val last = TimeDeposit(2, "basic", 1000.0, 31)
        val method = TimeDepositCalculator::class.java.getMethod("updateBalance", List::class.java)

        assertThatThrownBy { method.invoke(calculator, listOf(first, null, last)) }
            .isInstanceOf(InvocationTargetException::class.java)
            .hasCauseInstanceOf(NullPointerException::class.java)
        assertThat(first.balance).isEqualTo(1000.83)
        assertThat(last.balance).isEqualTo(1000.0)
    }

    @Test
    fun `should update the same time deposit again for each aliased list entry`() {
        val deposit = TimeDeposit(1, "basic", 1000.0, 31)

        calculator.updateBalance(listOf(deposit, deposit))

        assertThat(deposit.balance).isEqualTo(1001.6600000000001)
        assertThat(deposit.days).isEqualTo(31)
    }
}
