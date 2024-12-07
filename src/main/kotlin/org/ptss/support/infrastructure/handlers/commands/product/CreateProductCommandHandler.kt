package org.ptss.support.infrastructure.handlers.commands.product

import org.ptss.support.domain.commands.CreateProductCommand
import org.ptss.support.domain.interfaces.cqrs.ICommandHandler
import org.ptss.support.domain.models.Product
import org.ptss.support.infrastructure.repositories.ProductRepository
import org.ptss.support.infrastructure.util.executeWithExceptionLoggingAsync
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory

@ApplicationScoped
class CreateProductCommandHandler(
    private val productRepository: ProductRepository
) : ICommandHandler<CreateProductCommand, String> {
    private val logger = LoggerFactory.getLogger(CreateProductCommandHandler::class.java)

    override suspend fun handleAsync(command: CreateProductCommand): String {
        return logger.executeWithExceptionLoggingAsync(
            operation = {
                val product = Product(
                    name = command.name,
                    description = command.description,
                    media = command.media
                )
                productRepository.create(product)
            },
            logMessage = "Error creating product with name: ${command.name}"
        )
    }
}


