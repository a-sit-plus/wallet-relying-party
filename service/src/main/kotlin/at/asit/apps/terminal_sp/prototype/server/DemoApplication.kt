package at.asit.apps.terminal_sp.prototype.server

import at.asit.apps.terminal_sp.prototype.server.util.AntilogSlf4jAdapter
import io.github.aakira.napier.Napier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
	Napier.takeLogarithm()
	Napier.base(AntilogSlf4jAdapter())
	at.asitplus.wallet.idaustria.Initializer.initWithVCK()
	at.asitplus.wallet.eupid.Initializer.initWithVCK()
	at.asitplus.wallet.mdl.Initializer.initWithVCK()
	at.asitplus.wallet.cor.Initializer.initWithVCK()
	at.asitplus.wallet.por.Initializer.initWithVCK()
	at.asitplus.wallet.eprescription.Initializer.initWithVCK()
	runApplication<DemoApplication>(*args)
}
