package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.timedeposit.domain.PlanType
import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositCalculator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TimeDepositCalculatorPlansTest {

    @Test
    fun `should give no interest to a plan type that is not among the active plans`() {
        val calculator = TimeDepositCalculator(plans = setOf(PlanType.Basic))
        val student = TimeDeposit(id = 1, planType = "student", balance = 1200.0, days = 31)
        val basic = TimeDeposit(id = 2, planType = "basic", balance = 1000.0, days = 31)

        calculator.updateBalance(listOf(student, basic))

        assertThat(student.balance).isEqualTo(1200.0)
        assertThat(basic.balance).isEqualTo(1000.83)
    }

    /** `plans` is a Set, so a plan passed twice is kept once and its interest is added once. */
    @Test
    fun `should add interest once when the same plan is passed more than once`() {
        val plans = setOf(PlanType.Basic, PlanType.Basic, PlanType.Student, PlanType.Student)
        val calculator = TimeDepositCalculator(plans = plans)
        val basic = TimeDeposit(id = 1, planType = "basic", balance = 1000.0, days = 31)
        val student = TimeDeposit(id = 2, planType = "student", balance = 1200.0, days = 31)

        calculator.updateBalance(listOf(basic, student))

        assertThat(plans).hasSize(2)
        assertThat(basic.balance).isEqualTo(1000.83)
        assertThat(student.balance).isEqualTo(1203.0)
    }

    /** Codes match exactly; unknown and wrong-case codes are already pinned by TimeDepositCalculatorTest. */
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = [" basic", "basic "])
    fun `should give no interest for a plan code with surrounding whitespace`(code: String) {
        val deposit = TimeDeposit(id = 1, planType = code, balance = 1000.0, days = 31)

        TimeDepositCalculator().updateBalance(listOf(deposit))

        assertThat(deposit.balance).isEqualTo(1000.0)
    }
}
