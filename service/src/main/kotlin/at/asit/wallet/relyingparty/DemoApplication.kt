package at.asit.wallet.relyingparty

import at.asit.wallet.relyingparty.AntilogSlf4jAdapter
import at.asitplus.wallet.lib.Initializer.initOpenIdModule
import at.asitplus.wallet.taxid.Initializer
import io.github.aakira.napier.Napier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
	Napier.takeLogarithm()
	Napier.base(AntilogSlf4jAdapter)
	initOpenIdModule()
	Initializer.initWithVCK()
	at.asitplus.wallet.eupid.Initializer.initWithVCK()
	at.asitplus.wallet.eupidsdjwt.Initializer.initWithVCK()
	at.asitplus.wallet.mdl.Initializer.initWithVCK()
	at.asitplus.wallet.cor.Initializer.initWithVCK()
	at.asitplus.wallet.por.Initializer.initWithVCK()
	at.asitplus.wallet.healthid.Initializer.initWithVCK()
	at.asitplus.wallet.companyregistration.Initializer.initWithVCK()
	at.asitplus.wallet.ehic.Initializer.initWithVCK()
	runApplication<DemoApplication>(*args)
}
