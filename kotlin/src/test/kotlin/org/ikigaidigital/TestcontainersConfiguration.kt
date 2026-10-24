package org.ikigaidigital

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Real Postgres for the integration tests, the same image as `kotlin/compose.yaml`.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer() = PostgreSQLContainer(
        DockerImageName.parse("postgres:18.6-alpine")
    )
}
