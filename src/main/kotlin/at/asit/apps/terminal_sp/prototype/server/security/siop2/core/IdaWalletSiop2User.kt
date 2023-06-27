package at.asit.apps.terminal_sp.prototype.server.security.siop2.core

import at.asit.apps.terminal_sp.prototype.server.security.siop2.core.user.Siop2User
import at.asitplus.wallet.lib.data.VerifiablePresentationParsed
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.SpringSecurityCoreVersion
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.util.Assert
import java.io.Serializable
import java.util.*

open class IdaWalletSiop2User(
    val presentation: VerifiablePresentationParsed
) : Siop2User, Serializable {

    override fun getName(): String {
        TODO()
    }
}

