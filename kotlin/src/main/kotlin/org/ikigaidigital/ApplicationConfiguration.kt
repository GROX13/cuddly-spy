package org.ikigaidigital

import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import org.ikigaidigital.timedeposit.application.TimeDepositService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Composition root for the core: the service and the calculator are plain classes, created here from
 * the port Spring provides (ADR 0011, ADR 0016). The only Spring they carry is `@Transactional`
 * (ADR 0014); Spring still applies it to beans created here.
 */
@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {

    /** The frozen legacy calculator with its default plan types (ADR 0001, ADR 0009). */
    @Bean
    fun timeDepositCalculator() = TimeDepositCalculator()

    @Bean
    fun timeDepositService(repository: TimeDepositRepository, calculator: TimeDepositCalculator) =
        TimeDepositService(repository, calculator)
}
