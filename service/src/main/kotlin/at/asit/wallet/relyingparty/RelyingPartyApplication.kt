package at.asit.wallet.relyingparty

import io.github.aakira.napier.Napier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
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
