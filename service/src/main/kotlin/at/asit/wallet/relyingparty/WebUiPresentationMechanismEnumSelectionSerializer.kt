package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.io.TransformingSerializerTemplate
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

object WebUiPresentationMechanismEnumSelectionSerializer :
    KSerializer<PresentationMechanismEnum> by TransformingSerializerTemplate(
        parent = String.serializer(),
        encodeAs = {
            when (it) {
                PresentationMechanismEnum.PresentationExchange -> "presentation_definition"
                PresentationMechanismEnum.DCQL -> "dcql_query"
                PresentationMechanismEnum.DeviceRequest -> "device_request"
            }
        },
        decodeAs = {
            when (it) {
                "presentation_definition" -> PresentationMechanismEnum.PresentationExchange
                "dcql_query" -> PresentationMechanismEnum.DCQL
                "device_request" -> PresentationMechanismEnum.DeviceRequest
                else -> throw IllegalArgumentException("Unsupported presentation mechanism identifier.")
            }
        }
    )