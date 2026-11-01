package io.github.seyud.weave.core.registry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Repository formats this build can read. */
enum class ModuleRepoFormat {
    /** KernelSU Modules registry (`{baseUrl}/api/v1/index.json`, schema-versioned). */
    REGISTRY,

    /** Magisk modules repository index: `{"name": …, "modules": [{prop_url, zip_url, …}]}`. */
    MAGISK,
}

/**
 * Identifies the format of a repository index document.
 *
 * The deprecated KernelSU repository published a bare JSON array and is deliberately not
 * supported; its URL now returns 404. A document of an unknown shape is reported as a format
 * error instead of being guessed at.
 */
object ModuleRepoFormatDetector {

    private val json = Json { ignoreUnknownKeys = true; isLenient = false; explicitNulls = false }

    fun detect(indexText: String): ModuleRepoFormat {
        val element = runCatching { json.parseToJsonElement(indexText) }
            .getOrElse { throw RegistryFormatException("Malformed repository index", it) }
        if (element is JsonArray) {
            throw RegistryFormatException(
                "The deprecated KernelSU repository format is not supported; " +
                    "enter a KernelSU Modules site or a Magisk modules repository index"
            )
        }
        val obj = element as? JsonObject ?: throw RegistryFormatException("Unexpected repository index")
        val version = (obj["schemaVersion"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
        if (version != null) {
            // A schema-versioned document belongs to the registry protocol, whose parser reports
            // an unsupported version itself.
            return ModuleRepoFormat.REGISTRY
        }
        val modules = obj["modules"]
        if (modules is JsonArray && modules.isEmpty()) return ModuleRepoFormat.MAGISK
        if (modules is JsonArray) {
            val first = modules.firstOrNull() as? JsonObject ?: throw RegistryFormatException("Unexpected repository index")
            if ("prop_url" in first || "zip_url" in first) return ModuleRepoFormat.MAGISK
        }
        throw RegistryFormatException("Unrecognised repository index format")
    }

    /** `last_update` of a legacy index, or 0 when the field is missing. */
    internal fun indexTimestamp(obj: JsonObject): Long =
        (obj["last_update"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: 0L
}
