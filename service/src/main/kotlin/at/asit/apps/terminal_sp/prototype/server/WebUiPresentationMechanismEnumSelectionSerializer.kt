package at.asit.apps.terminal_sp.prototype.server

import at.asitplus.signum.indispensable.io.TransformingSerializerTemplate
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

object WebUiPresentationMechanismEnumSelectionSerializer : KSerializer<PresentationMechanismEnum> by TransformingSerializerTemplate<PresentationMechanismEnum, String>(
    parent = String.serializer(),
    encodeAs = {
        when (it) {
            PresentationMechanismEnum.PresentationExchange -> "presentation_definition"
            PresentationMechanismEnum.DCQL -> "dcql_query"
        }
    },
    decodeAs = {
        when (it) {
            "presentation_definition" -> PresentationMechanismEnum.PresentationExchange
            "dcql_query" -> PresentationMechanismEnum.DCQL
            else -> throw IllegalArgumentException("Unsupported presentation mechanism identifier.")
        }
    }
)