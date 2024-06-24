package at.asit.apps.terminal_sp.prototype.server

import at.asit.apps.terminal_sp.prototype.server.util.AntilogSlf4jAdapter
import io.github.aakira.napier.Napier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
	Napier.base(AntilogSlf4jAdapter())
	at.asitplus.wallet.idaustria.Initializer.initWithVcLib()
	at.asitplus.wallet.eupid.Initializer.initWithVcLib()
	at.asitplus.wallet.mdl.Initializer.initWithVcLib()
	runApplication<DemoApplication>(*args)
}
