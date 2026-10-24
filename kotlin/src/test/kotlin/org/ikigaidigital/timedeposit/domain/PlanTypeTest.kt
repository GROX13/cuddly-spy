package org.ikigaidigital.timedeposit.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PlanTypeTest {

    @Test
    fun `should list every sealed subclass in planTypes`() {
        val subclasses = PlanType::class.sealedSubclasses.map { it.objectInstance }

        assertThat(subclasses.toSet()).isEqualTo(planTypes)
    }

    @Test
    fun `should give every plan a unique code`() {
        // The calculator finds plans by code; two plans sharing a code would silently shadow each other.
        assertThat(planTypes.map { it.code }).doesNotHaveDuplicates()
    }

    @Test
    fun `should give every plan at least 31 days before it earns interest`() {
        planTypes.forEach { assertThat(it.eligibleDays.first).isGreaterThanOrEqualTo(31) }
    }

    @Test
    fun `should define the annual rate of each plan`() {
        assertThat(PlanType.Basic.annualRate).isEqualTo(0.01)
        assertThat(PlanType.Student.annualRate).isEqualTo(0.03)
        assertThat(PlanType.Premium.annualRate).isEqualTo(0.05)
    }

    @ParameterizedTest(name = "{0} at {1} days eligible: {2}")
    @CsvSource(
        "basic,   30,         false",
        "basic,   31,         true",
        "basic,   2147483647, true",
        "student, 30,         false",
        "student, 31,         true",
        "student, 365,        true",
        "student, 366,        false",
        "premium, 45,         false",
        "premium, 46,         true",
        "premium, 2147483647, true",
    )
    fun `should bound the eligible days of each plan`(code: String, days: Int, eligible: Boolean) {
        assertThat(days in planOf(code).eligibleDays).isEqualTo(eligible)
    }

    @ParameterizedTest(name = "{0}: 1200.00 at {1} days -> {2}")
    @CsvSource(
        "basic,   31,  1.0",
        "student, 31,  3.0",
        "premium, 46,  5.0",
        "basic,   30,  0.0",
        "student, 366, 0.0",
        "premium, 45,  0.0",
    )
    fun `should compute raw monthly interest only inside the eligible days`(code: String, days: Int, expected: Double) {
        assertThat(planOf(code).monthlyInterest(1200.0, days)).isEqualTo(expected)
    }

    @Test
    fun `should compute monthly interest as balance times rate divided by twelve`() {
        // Pins the legacy `balance * rate / 12` order; `balance * (rate / 12)` would give 4.166625.
        assertThat(PlanType.Premium.monthlyInterest(999.99, 46)).isEqualTo(4.166625000000001)
    }

    private companion object {
        fun planOf(code: String): PlanType = planTypes.single { it.code == code }
    }
}
