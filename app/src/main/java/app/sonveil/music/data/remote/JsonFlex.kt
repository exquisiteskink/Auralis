package app.sonveil.music.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when (element) {
            JsonNull -> ""
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object FlexibleStringOrNullSerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("FlexStringNull", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when (element) {
            JsonNull -> null
            is JsonPrimitive -> element.content.ifBlank { null }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

open class FlexListSerializer<T>(element: KSerializer<T>) :
    JsonTransformingSerializer<List<T>>(ListSerializer(element)) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> element
        JsonNull -> JsonArray(emptyList())
        else -> JsonArray(listOf(element))
    }
}
