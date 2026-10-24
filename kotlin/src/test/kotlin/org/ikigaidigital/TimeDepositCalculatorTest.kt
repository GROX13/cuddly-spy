package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Characterization tests: they pin what `updateBalance` does today, floating-point quirks
 * included. Expected balances are literals captured by running the current code once and
 * are compared bit for bit with `isEqualTo`. A failing row means behavior changed.
 *
 * Hand-checked rows (decimal arithmetic, then HALF_UP to 2 places):
 * - student, 1200.00, 31 days:     1200 * 0.03 / 12 = 3.00     -> 1203.00
 * - premium, 1200.00, 46 days:     1200 * 0.05 / 12 = 5.00     -> 1205.00
 * - basic, 1000.00, 31 days:       1000 * 0.01 / 12 = 0.8333... -> 0.83 -> 1000.83
 * - basic, 1234567.00, 100 days:   1234567 * 0.01 / 12 = 1028.805833... -> 1028.81 -> 1235595.81
 * - basic, 150.00, 100 days:       150 * 0.01 / 12 = 0.125 (exact in binary) -> HALF_UP 0.13 -> 150.13
 */
class TimeDepositCalculatorTest {

    private val calculator = TimeDepositCalculator()

    private fun balanceAfterUpdate(
        planType: String,
        balance: Double,
        days: Int
    ): Double {
        val deposit = TimeDeposit(
            id = 1,
            planType = planType,
            balance = balance,
            days = days
        )
        calculator.updateBalance(xs = listOf(deposit))
        return deposit.balance
    }

    @Nested
    @DisplayName("Basic plan type")
    inner class BasicPlan {
        private val balance = 1000.0

        @ParameterizedTest(name = "{0} days -> balance {1}")
        @CsvSource(
            "0, 1000.0",
            "30, 1000.0"
        )
        fun `should not add interest during the first 30 days`(days: Int, expectedBalance: Double) {
            assertThat(balanceAfterUpdate("basic", balance, days)).isEqualTo(expectedBalance)
        }

        @ParameterizedTest(name = "{0} days -> balance {1}")
        @CsvSource(
            "31, 1000.83",
            "45, 1000.83",
            "46, 1000.83",
            "365, 1000.83",
            "366, 1000.83",
            "1000, 1000.83"
        )
        fun `should add monthly interest after 30 days with no upper limit`(days: Int, expectedBalance: Double) {
            assertThat(balanceAfterUpdate("basic", balance, days)).isEqualTo(expectedBalance)
        }
    }

    @Nested
    @DisplayName("Student plan type")
    inner class StudentPlan {
        private val balance = 1200.0

        @ParameterizedTest(name = "{0} days -> balance {1}")
        @CsvSource(
            "0, 1200.0",
            "30, 1200.0"
        )
        fun `should not add interest during the first 30 days`(days: Int, expectedBalance: Double) {
            assertThat(balanceAfterUpdate("student", balance, days)).isEqualTo(expectedBalance)
        }

        @ParameterizedTest(name = "{0} days -> balance {1}")
        @CsvSource(
            "31, 1203.0",
            "45, 1203.0",
            "46, 1203.0",
            "365, 1203.0"
        )
        fun `should add monthly interest from day 31 to day 365`(days: Int, expectedBalance: Double) {
            assertThat(balanceAfterUpdate("student", balance, days)).isEqualTo(expectedBalance)
        }

        @ParameterizedTest(name = "{0} days -> balance {1}")
        @CsvSource(
            "366, 1200.0",
            "1000, 1200.0"
        )
        fun `should stop adding interest after a year`(days: Int, expectedBalance: Double) {
            assertThat(balanceAfterUpdate("student", balance, days)).isEqualTo(expectedBalance)
        }
    }

    @Nested
    @DisplayName("Premium plan type")
    inner class PremiumPlan {
        private val balance = 1200.0

        @ParameterizedTest(name = "{0} days -> balance {1}")
        @CsvSource(
            "0, 1200.0",
            "30, 1200.0",
            "31, 1200.0",
            "45, 1200.0"
        )
        fun `should not add interest during the first 45 days`(days: Int, expectedBalance: Double) {
            assertThat(balanceAfterUpdate("premium", balance, days)).isEqualTo(expectedBalance)
        }

        @ParameterizedTest(name = "{0} days -> balance {1}")
        @CsvSource(
            "46, 1205.0",
            "365, 1205.0",
            "366, 1205.0",
            "1000, 1205.0"
        )
        fun `should add monthly interest after 45 days with no upper limit`(days: Int, expectedBalance: Double) {
            assertThat(balanceAfterUpdate("premium", balance, days)).isEqualTo(expectedBalance)
        }
    }

    @Nested
    @DisplayName("Unknown plan types")
    inner class UnknownPlanTypes {
        private val balance = 1200.0

        @ParameterizedTest(name = "plan type ''{0}'', {1} days -> balance unchanged")
        @CsvSource(
            "gold, 31",
            "gold, 1000",
            "'', 31",
            "'', 1000"
        )
        fun `should not add interest for an unknown plan type`(planType: String, days: Int) {
            assertThat(balanceAfterUpdate(planType, balance, days)).isEqualTo(balance)
        }

        @ParameterizedTest(name = "plan type ''{0}'', {1} days -> balance unchanged")
        @CsvSource(
            "Basic, 31",
            "Basic, 1000"
        )
        fun `should match plan types case-sensitively`(planType: String, days: Int) {
            assertThat(balanceAfterUpdate(planType, balance, days)).isEqualTo(balance)
        }
    }

    @Nested
    @DisplayName("Balance edge cases")
    inner class BalanceEdgeCases {
        private val days = 100

        @ParameterizedTest(name = "{0} plan type -> balance stays 0.0")
        @CsvSource(
            "basic",
            "student",
            "premium"
        )
        fun `should keep a zero balance at zero`(planType: String) {
            assertThat(balanceAfterUpdate(planType, 0.0, days)).isEqualTo(0.0)
        }

        @ParameterizedTest(name = "{0} plan type -> balance stays 0.01")
        @CsvSource(
            "basic",
            "student",
            "premium"
        )
        fun `should add nothing when the monthly interest rounds to zero`(planType: String) {
            assertThat(balanceAfterUpdate(planType, 0.01, days)).isEqualTo(0.01)
        }

        @ParameterizedTest(name = "{0} plan type, balance 1234567.0 -> balance {1}")
        @CsvSource(
            "basic, 1235595.81",
            "student, 1237653.42",
            "premium, 1239711.03"
        )
        fun `should add monthly interest to a large balance`(planType: String, expectedBalance: Double) {
            assertThat(balanceAfterUpdate(planType, 1234567.0, days)).isEqualTo(expectedBalance)
        }

        /**
         * A negative balance is not rejected: negative interest is added (pinned as-is).
         */
        @ParameterizedTest(name = "{0} plan type, balance -1200.0 -> balance {1}")
        @CsvSource(
            "basic, -1201.0",
            "student, -1203.0",
            "premium, -1205.0"
        )
        fun `should add negative interest to a negative balance`(planType: String, expectedBalance: Double) {
            assertThat(balanceAfterUpdate(planType, -1200.0, days)).isEqualTo(expectedBalance)
        }
    }

    @Nested
    @DisplayName("Legacy rounding")
    inner class LegacyRounding {
        private val days = 100

        /**
         * 150 -> 0.125 is exact in binary, and HALF_UP rounds it away from zero (-150 -> -0.13).
         */
        @ParameterizedTest(name = "basic plan type, balance {0} -> balance {1}")
        @CsvSource(
            "150.0, 150.13",
            "-150.0, -150.13"
        )
        fun `should round an exact half cent away from zero`(balance: Double, expectedBalance: Double) {
            assertThat(balanceAfterUpdate("basic", balance, days)).isEqualTo(expectedBalance)
        }

        /**
         * 6.0 -> 0.005 (binary 0.00500000000000000010...) rounds up; 5.99 -> 0.00499... rounds down.
         */
        @ParameterizedTest(name = "basic plan type, balance {0} -> balance {1}")
        @CsvSource(
            "6.0, 6.01",
            "5.99, 5.99"
        )
        fun `should round on either side of half a cent`(balance: Double, expectedBalance: Double) {
            assertThat(balanceAfterUpdate("basic", balance, days)).isEqualTo(expectedBalance)
        }

        /**
         * INTENTIONAL LEGACY BEHAVIOR: BigDecimal(Double) sees the exact binary value, so a
         * clear .xx5 can round DOWN. 30 * 0.01 / 12 is 0.025 in decimal, but the double is
         * 0.02499999999999999791..., so it becomes 0.02 (not 0.03) -> 30.02. Same for student
         * 10 * 0.03 / 12 (binary 0.02499999...) -> 10.02 and premium 18 * 0.05 / 12
         * (binary 0.07499999...) -> 18.07.
         */
        @ParameterizedTest(name = "{0} plan type, balance {1} -> balance {2}")
        @CsvSource(
            "basic, 30.0, 30.02",
            "student, 10.0, 10.02",
            "premium, 18.0, 18.07"
        )
        fun `should round an apparent half cent down when its binary value is just below`(
            planType: String,
            balance: Double,
            expectedBalance: Double,
        ) {
            assertThat(balanceAfterUpdate(planType, balance, days)).isEqualTo(expectedBalance)
        }

        /**
         * INTENTIONAL LEGACY BEHAVIOR: the rounded interest is added as a Double, so the sum
         * can carry binary noise: 10.8 + 0.05 = 10.850000000000001.
         */
        @Test
        fun `should keep the binary noise of adding the rounded interest as a Double`() {
            assertThat(balanceAfterUpdate("premium", 10.8, days)).isEqualTo(10.850000000000001)
        }
    }

    /**
     * Each call adds one more monthly interest to the current balance and leaves `days` as it is,
     * so repeated calls compound. The balance-update API keeps this behaviour (docs/adr/0008-*).
     *
     * Hand-checked: 1000 * 0.01 / 12 = 0.8333.. -> 0.83; 1000.83 * 0.01 / 12 = 0.8340.. -> 0.83;
     * 1001.66 * 0.01 / 12 = 0.8347.. -> 0.83. INTENTIONAL LEGACY BEHAVIOR: adding 0.83 as a Double
     * leaves binary noise from the second call on (1001.6600000000001, 1002.4900000000001).
     */
    @Test
    fun `should add monthly interest again on every call to the same time deposit`() {
        val deposit = TimeDeposit(1, "basic", 1000.0, 31)
        val balances = mutableListOf<Double>()

        repeat(3) {
            calculator.updateBalance(listOf(deposit))
            balances += deposit.balance
        }

        assertThat(balances).containsExactly(1000.83, 1001.6600000000001, 1002.4900000000001)
        assertThat(deposit.days).isEqualTo(31)
    }

    @Test
    fun `should update each time deposit independently and in place`() {
        val basic = TimeDeposit(1, "basic", 1000.0, 31)
        val student = TimeDeposit(2, "student", 1200.0, 366)
        val premium = TimeDeposit(3, "premium", 1200.0, 46)
        val unknown = TimeDeposit(4, "gold", 1200.0, 100)
        val deposits = listOf(basic, student, premium, unknown)

        calculator.updateBalance(deposits)

        assertThat(deposits.map { it.balance }).containsExactly(1000.83, 1200.0, 1205.0, 1200.0)
        assertThat(basic).isEqualTo(TimeDeposit(1, "basic", 1000.83, 31))
        assertThat(premium).isEqualTo(TimeDeposit(3, "premium", 1205.0, 46))
    }

    @Test
    fun `should accept an empty list of time deposits`() {
        val deposits = emptyList<TimeDeposit>()

        calculator.updateBalance(deposits)

        assertThat(deposits).isEmpty()
    }
}
