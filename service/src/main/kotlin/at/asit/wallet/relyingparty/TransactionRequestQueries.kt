package at.asit.wallet.relyingparty

import at.asitplus.openid.dcql.DCQLQuery
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionRequestQueries(
    @SerialName(SerialNames.DCQL_QUERY)
    val dcqlQuery: DCQLQuery? = null,
    @SerialName(SerialNames.DCQL_QUERY_ERROR)
    val dcqlQueryError: String? = null,
) {
    object SerialNames {
        const val DCQL_QUERY = "dcqlQuery"
        const val DCQL_QUERY_ERROR = "dcqlQueryError"
    }
}
