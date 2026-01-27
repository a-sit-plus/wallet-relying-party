package at.asit.wallet.relyingparty

import io.github.aakira.napier.Napier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
class RelyingPartyApplication

fun main(args: Array<String>) {
    Napier.takeLogarithm()
    Napier.base(AntilogSlf4jAdapter)
    at.asitplus.wallet.taxid.Initializer.initWithVCK()
    at.asitplus.wallet.eupid.Initializer.initWithVCK()
    at.asitplus.wallet.eupidsdjwt.Initializer.initWithVCK()
    at.asitplus.wallet.mdl.Initializer.initWithVCK()
    at.asitplus.wallet.cor.Initializer.initWithVCK()
    at.asitplus.wallet.por.Initializer.initWithVCK()
    at.asitplus.wallet.ehic.Initializer.initWithVCK()
    at.asitplus.wallet.ageverification.Initializer.initWithVCK()
    runApplication<RelyingPartyApplication>(*args)
}
