package at.asit.wallet.relyingparty

import at.asitplus.dif.PresentationDefinition
import at.asitplus.openid.dcql.DCQLQuery
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionRequestQueries(
    @SerialName(SerialNames.PRESENTATION_DEFINITION)
    val presentationDefinition: PresentationDefinition? = null,
    @SerialName(SerialNames.PRESENTATION_DEFINITION_ERROR)
    val presentationDefinitionError: String? = null,
    @SerialName(SerialNames.DCQL_QUERY)
    val dcqlQuery: DCQLQuery? = null,
    @SerialName(SerialNames.DCQL_QUERY_ERROR)
    val dcqlQueryError: String? = null,
) {
    object SerialNames {
        const val PRESENTATION_DEFINITION = "presentationDefinition"
        const val PRESENTATION_DEFINITION_ERROR = "presentationDefinitionError"
        const val DCQL_QUERY = "dcqlQuery"
        const val DCQL_QUERY_ERROR = "dcqlQueryError"
    }
}