package at.asit.apps.terminal_sp.prototype.server

import at.asit.apps.terminal_sp.prototype.server.util.AntilogSlf4jAdapter
import at.asitplus.wallet.lib.Initializer.initOpenIdModule
import io.github.aakira.napier.Napier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
	Napier.takeLogarithm()
	Napier.base(AntilogSlf4jAdapter)
	initOpenIdModule()
	at.asitplus.wallet.eupid.Initializer.initWithVCK()
	at.asitplus.wallet.mdl.Initializer.initWithVCK()
	at.asitplus.wallet.cor.Initializer.initWithVCK()
	at.asitplus.wallet.por.Initializer.initWithVCK()
	at.asitplus.wallet.eprescription.Initializer.initWithVCK()
	at.asitplus.wallet.companyregistration.Initializer.initWithVCK()
	runApplication<DemoApplication>(*args)
}
