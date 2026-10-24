package org.ikigaidigital.timedeposit.domain

/**
 * A plan type as data: its code, annual rate and eligible days.
 *
 * Each [eligibleDays] range already includes the README's rule that no interest is paid in the first
 * 30 days, so every plan's range starts at 31 or later. Why plans are data in a sealed class rather
 * than rule objects, strategy classes or an enum: ADR 0009.
 *
 * The domain package must stay free of Spring, JPA and adapter dependencies (enforced by ArchitectureTest).
 */
sealed class PlanType(val code: String, val annualRate: Double, val eligibleDays: IntRange) {
    /** Raw monthly interest in the legacy order (balance * rate / 12); 0.0 outside the eligible days. */
    fun monthlyInterest(balance: Double, days: Int): Double =
        if (days in eligibleDays) balance * annualRate / 12 else 0.0

    data object Basic : PlanType("basic", 0.01, 31..Int.MAX_VALUE)
    data object Student : PlanType("student", 0.03, 31..365)
    data object Premium : PlanType("premium", 0.05, 46..Int.MAX_VALUE)
}

/**
 * Every plan type. Kept outside [PlanType] on purpose: a companion listing its own object subclasses
 * would capture `null` when a subclass is the first class touched (JVM class-init order); out here
 * there is no such cycle. A Set, so no plan can be listed twice. Finding a plan by code is the
 * calculator's job.
 */
val planTypes: Set<PlanType> = setOf(PlanType.Basic, PlanType.Student, PlanType.Premium)
