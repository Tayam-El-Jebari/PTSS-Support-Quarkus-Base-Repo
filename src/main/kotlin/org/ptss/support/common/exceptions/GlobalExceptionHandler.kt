package org.ptss.support.common.exceptions

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import io.quarkus.logging.Log
import io.smallrye.faulttolerance.api.RateLimitException
import jakarta.inject.Inject
import jakarta.validation.ConstraintViolationException
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException
import org.jboss.resteasy.reactive.ClientWebApplicationException
import org.ptss.support.core.context.IRequestContextService
import org.ptss.support.metrics.CustomMetrics
import org.ptss.support.domain.enums.ErrorCode

@Provider
@Produces(MediaType.APPLICATION_JSON)
class GlobalExceptionHandler @Inject constructor(
    private val requestContextService: IRequestContextService,
    private val customMetrics: CustomMetrics
) : ExceptionMapper<Throwable> {

    override fun toResponse(exception: Throwable): Response {
        // Increment the failed requests counter
        customMetrics.incrementFailedRequests()

        val requestId = requestContextService.getCurrentRequestId()
        val path = requestContextService.getCurrentPath()

        return when (exception) {
            is APIException -> createResponse(
                errorCode = exception.errorCode,
                message = exception.message,
                requestId = requestId,
                details = exception.details
            )

            is ClientWebApplicationException -> {
                val message = exception.message ?: "Client error: ${exception.response.status}"
                Log.error("Client error occurred for request $requestId: $message")
                createResponse(
                    errorCode = ErrorCode.INTERNAL_ERROR,
                    message = message,
                    requestId = requestId
                )
            }

            is NotFoundException -> {
                val message = exception.message ?: "Resource not found: ${exception.response.status}"
                Log.error("Resource not found: $requestId: $message")
                createResponse(
                    errorCode = ErrorCode.NOT_FOUND,
                    message = message,
                    requestId = requestId
                )
            }

            is ForbiddenException -> {
                Log.warn("Unauthorized access attempt for request $requestId at path $path")
                createResponse(
                    errorCode = ErrorCode.INSUFFICIENT_PERMISSIONS,
                    message = "Client does not have the proper rights to access this resource",
                    requestId = requestId
                )
            }

            is ConstraintViolationException -> {
                val details = ErrorDetails(
                    constraints = exception.constraintViolations.associate { violation ->
                        violation.propertyPath.toString() to mapOf(
                            "constraint" to violation.constraintDescriptor.annotation.annotationClass.simpleName,
                            "message" to violation.message,
                            "value" to violation.invalidValue?.toString()
                        )
                    }
                )
                createResponse(
                    errorCode = ErrorCode.VALIDATION_ERROR,
                    message = "Validation failed",
                    requestId = requestId,
                    details = details
                )
            }

            is RateLimitException -> {
                Log.warn("Rate limit exceeded for request $requestId at path $path")
                createResponse(
                    errorCode = ErrorCode.RATE_LIMIT_EXCEEDED,
                    message = exception.message ?: "Too many concurrent requests",
                    requestId = requestId,
                )
            }

            is TimeoutException -> {
                Log.error("Request timeout for request $requestId at path $path")
                createResponse(
                    errorCode = ErrorCode.GATEWAY_TIMEOUT,
                    message = exception.message ?: "Request timed out",
                    requestId = requestId
                )
            }

            is CircuitBreakerOpenException -> {
                Log.error("Circuit breaker open for request $requestId at path $path")
                createResponse(
                    errorCode = ErrorCode.SERVICE_UNAVAILABLE,
                    message = exception.message ?: "Service temporarily unavailable",
                    requestId = requestId
                )
            }

            is ValueInstantiationException -> {
                Log.error("Request deserialization error for request $requestId: ${exception.message}")
                createResponse(
                    errorCode = ErrorCode.VALIDATION_ERROR,
                    message = "Invalid request format: ${getNullFieldFromError(exception)}",
                    requestId = requestId
                )
            }

            is JsonMappingException -> {
                Log.error("JSON mapping error for request $requestId: ${exception.message}")
                createResponse(
                    errorCode = ErrorCode.VALIDATION_ERROR,
                    message = "Invalid request format: ${exception.message}",
                    requestId = requestId
                )
            }

            is WebApplicationException -> {
                val message = when (val cause = exception.cause) {
                    is ValueInstantiationException -> getNullFieldFromError(cause)
                    else -> exception.message ?: "Request processing failed"
                }

                Log.error("Web application error for request $requestId: $message")
                createResponse(
                    errorCode = ErrorCode.VALIDATION_ERROR,
                    message = message,
                    requestId = requestId
                )
            }

            is com.azure.data.tables.models.TableServiceException -> {
                Log.error("Azure Table Storage error for request $requestId: ${exception.message}")
                createResponse(
                    errorCode = ErrorCode.SERVICE_UNAVAILABLE,
                    message = "Storage service error",
                    requestId = requestId
                )
            }

            else -> {
                Log.error("Unhandled exception for request $requestId", exception)
                createResponse(
                    errorCode = ErrorCode.INTERNAL_ERROR,
                    message = "Internal server error",
                    requestId = requestId
                )
            }
        }
    }

    private fun createResponse(
        errorCode: ErrorCode,
        message: String,
        requestId: String? = null,
        details: ErrorDetails? = null
    ): Response {
        val error = ServiceError(
            code = errorCode.code,
            message = message,
            requestId = requestId,
            details = details
        )

        return Response.status(errorCode.status)
            .entity(error)
            .build()
    }

    private fun getNullFieldFromError(exception: ValueInstantiationException): String {
        val nullFieldMatch = "parameter (\\w+)".toRegex()
            .find(exception.message ?: "")?.groupValues?.get(1)

        return if (nullFieldMatch != null) {
            "Field '$nullFieldMatch' cannot be null"
        } else {
            "Required field is missing"
        }
    }
}