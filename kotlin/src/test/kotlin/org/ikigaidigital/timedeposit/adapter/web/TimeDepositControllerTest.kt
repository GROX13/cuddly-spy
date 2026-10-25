package org.ikigaidigital.timedeposit.adapter.web

import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.ApplicationConfiguration
import org.ikigaidigital.timedeposit.application.InMemoryTimeDepositRepository
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The web slice with the real service and its real wiring (`ApplicationConfiguration`) over an
 * in-memory port, so nothing is mocked. Bodies are compared as raw text to pin the number and date formats.
 */
@WebMvcTest(TimeDepositController::class)
@Import(ApplicationConfiguration::class, TimeDepositControllerTest.FakePorts::class)
class TimeDepositControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    class FakePorts {
        @Bean
        fun timeDepositRepository() = InMemoryTimeDepositRepository()
    }

    @Autowired
    private lateinit var mvc: MockMvcTester

    @Autowired
    private lateinit var repository: InMemoryTimeDepositRepository

    @Test
    fun `should return time deposits with camelCase fields, two-decimal money and ISO dates`() {
        repository.deposits = listOf(
            TimeDepositStatement(
                1, "basic", BigDecimal("1000.00"), 400,
                listOf(
                    Withdrawal(11, BigDecimal("100.00"), LocalDate.of(2026, 3, 15)),
                    Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1)),
                ),
            ),
        )

        assertThat(mvc.get().uri("/time-deposits"))
            .hasStatusOk()
            .hasContentType(MediaType.APPLICATION_JSON)
            .bodyText().isEqualTo(
                """[{"id":1,"planType":"basic","balance":1000.00,"days":400,"withdrawals":[""" +
                    """{"id":11,"amount":100.00,"date":"2026-03-15"},""" +
                    """{"id":10,"amount":50.00,"date":"2026-06-01"}]}]"""
            )
    }

    /**
     * Balances are the stored NUMERIC(19,2) decimals and leave exactly as stored (ADR 0015): cents a
     * Double can't hold (90071992547409.91 would read back as ...409.90625), the column's maximum, and
     * no exponent notation for 10000000.00.
     */
    @Test
    fun `should return the stored balance exactly and keep empty withdrawals as an empty array`() {
        repository.deposits = listOf(
            TimeDepositStatement(1, "premium", BigDecimal("90071992547409.91"), 46, emptyList()),
            TimeDepositStatement(2, "basic", BigDecimal("99999999999999999.99"), 31, emptyList()),
            TimeDepositStatement(3, "student", BigDecimal("10000000.00"), 200, emptyList()),
        )

        assertThat(mvc.get().uri("/time-deposits"))
            .hasStatusOk()
            .bodyText().isEqualTo(
                """[{"id":1,"planType":"premium","balance":90071992547409.91,"days":46,"withdrawals":[]},""" +
                    """{"id":2,"planType":"basic","balance":99999999999999999.99,"days":31,"withdrawals":[]},""" +
                    """{"id":3,"planType":"student","balance":10000000.00,"days":200,"withdrawals":[]}]"""
            )
    }

    @Test
    fun `should return an empty array when there are no time deposits`() {
        repository.deposits = emptyList()

        assertThat(mvc.get().uri("/time-deposits")).hasStatusOk().bodyText().isEqualTo("[]")
    }

    /**
     * One month by the legacy rules (checked in jshell): basic 1000.00 at 400 days earns
     * 1000 * 0.01 / 12 = 0.8333… -> 0.83; premium at 40 days is before its eligible days.
     */
    @Test
    fun `should add one month of interest on a POST with no body and return every time deposit`() {
        repository.deposits = listOf(
            TimeDepositStatement(
                1, "basic", BigDecimal("1000.00"), 400,
                listOf(Withdrawal(11, BigDecimal("100.00"), LocalDate.of(2026, 3, 15))),
            ),
            TimeDepositStatement(2, "premium", BigDecimal("5000.00"), 40, emptyList()),
        )

        assertThat(mvc.post().uri("/time-deposits/balance-updates"))
            .hasStatusOk()
            .hasContentType(MediaType.APPLICATION_JSON)
            .bodyText().isEqualTo(
                """[{"id":1,"planType":"basic","balance":1000.83,"days":400,"withdrawals":[""" +
                    """{"id":11,"amount":100.00,"date":"2026-03-15"}]},""" +
                    """{"id":2,"planType":"premium","balance":5000.00,"days":40,"withdrawals":[]}]"""
            )
    }
}
