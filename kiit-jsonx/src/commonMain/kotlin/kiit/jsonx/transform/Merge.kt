/** url: www.kiit.dev */
package kiit.jsonx.transform

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXObject

/**
 * Combines [base] and [override] into a single tree: object keys merge recursively (a key present
 * in both is merged rather than replaced wholesale); everything else — arrays, scalars, tagged
 * nodes, or a type mismatch between [base] and [override] at the same position — is a plain
 * override-wins replacement.
 *
 * No configurable precedence policy: this one fixed rule is the entire primitive, callers compose
 * it (e.g. `files.reduce(::merge)`) for layered config (base + environment overrides).
 *
 * Always operates on plain jsonx-produced trees: [JsonXElement] is a closed hierarchy with no
 * consumer-extensible node type, so there is nothing else it could ever see.
 */
fun merge(base: JsonXElement, override: JsonXElement): JsonXElement {
    if (base !is JsonXObject || override !is JsonXObject) return override

    val entries = LinkedHashMap<String, JsonXElement>(base.entries)
    for ((key, overrideValue) in override.entries) {
        val baseValue = entries[key]
        entries[key] = if (baseValue != null) merge(baseValue, overrideValue) else overrideValue
    }
    return JsonXObject(entries)
}
