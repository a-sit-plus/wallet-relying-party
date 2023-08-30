package at.asit.apps.terminal_sp.prototype.server.security.siop2.core

import at.asit.apps.terminal_sp.prototype.server.security.siop2.core.user.Siop2User
import at.asitplus.wallet.lib.data.IsoDocumentParsed
import java.io.Serializable

open class WalletMdocUser(
    val document: IsoDocumentParsed
) : Siop2User, Serializable {

    override fun getName(): String {
        TODO()
    }
}

