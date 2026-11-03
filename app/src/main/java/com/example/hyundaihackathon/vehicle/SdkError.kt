package com.example.hyundaihackathon.vehicle

/**
 * An SDK failure reduced to what the collector acts on. [code] is safe to log: it names
 * the error and never carries vehicle values.
 *
 * The SDK's `VehicleException` is obfuscated in the published AAR and `ErrorCode` is a
 * runtime-only dependency, so neither can be referenced. The SDK puts the error name
 * (for example `ERROR_NOT_SUPPORTED_PROPERTY`) or its numeric code in the message instead.
 */
sealed class SdkError(val code: String, cause: Throwable?) : Exception(code, cause) {
    class NotSupported(code: String, cause: Throwable?) : SdkError(code, cause)
    class PermissionDenied(code: String, cause: Throwable?) : SdkError(code, cause)
    class Connection(code: String, cause: Throwable?) : SdkError(code, cause)
    class Timeout : SdkError("TIMEOUT", null)
    class Other(code: String, cause: Throwable?) : SdkError(code, cause)

    companion object {
        /** `ai.pleos.playground.vehicle.constant.ErrorCode` names and values (docs/sdk-notes.md 1.7). */
        private val CODES = mapOf(
            30001 to "ERROR_INTERNAL_SERVICE",
            40001 to "ERROR_SERVICE_CONNECTION_FAILURE",
            50001 to "ERROR_DRIVING_RESTRICTED",
            60001 to "ERROR_NOT_SUPPORTED_PROPERTY",
            60002 to "ERROR_NOT_SUPPORTED_VALUE_TYPE",
            60003 to "ERROR_NOT_SUPPORTED_AREA",
            70001 to "ERROR_EMPTY_RESULT",
            800001 to "ERROR_SECURITY_PERMISSION",
            99999 to "ERROR_UNDEFINED",
        )
        private const val UNDEFINED = "ERROR_UNDEFINED"
        const val PERMISSION_DENIED = "ERROR_SECURITY_PERMISSION"
        val NOT_SUPPORTED_CODES = setOf("ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_AREA", "ERROR_NOT_SUPPORTED_VALUE_TYPE")

        fun from(e: Throwable): SdkError {
            if (e is SdkError) return e
            if (e is SecurityException) return PermissionDenied(PERMISSION_DENIED, e)
            val message = e.message?.trim().orEmpty()
            val code = message.toIntOrNull()?.let(CODES::get) ?: message.takeIf { it in CODES.values } ?: UNDEFINED
            return when (code) {
                "ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_AREA", "ERROR_NOT_SUPPORTED_VALUE_TYPE" ->
                    NotSupported(code, e)
                "ERROR_SECURITY_PERMISSION" -> PermissionDenied(code, e)
                "ERROR_SERVICE_CONNECTION_FAILURE" -> Connection(code, e)
                else -> Other(code, e)
            }
        }
    }
}
