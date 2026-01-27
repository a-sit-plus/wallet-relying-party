package at.asit.wallet.relyingparty

/** These URLs are also defined in static Javascript files! */
object Paths {
    const val CustomerSuccessUrl = "customer-success.html"
    const val LogsUrl = "/logs"

    object Api {
        const val ItemsUrl = "/api/items"
        const val SingleUrl = "/api/single"
        const val RemoveUrl = "/api/remove"
    }

    object Transaction {
        const val CreateUrl = "/transaction/create"
        const val ResultUrl = "/transaction/result"
        const val GetUrl = "/transaction/get"
    }

    object Schemes {
        const val Haip = "haip://"
        const val HaipVp = "haip-vp://"
        const val MdocOpenId4Vp = "mdoc-openid4vp://"
        const val OpenId4Vp = "openid4vp://"
    }

}