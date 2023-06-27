package at.asit.apps.terminal_sp.prototype.server

import at.asit.apps.terminal_sp.prototype.server.util.AntilogSlf4jAdapter
import at.asitplus.wallet.idaustria.Initializer
import io.github.aakira.napier.Napier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
	Initializer.initWithVcLib()
	Napier.base(AntilogSlf4jAdapter())
	runApplication<DemoApplication>(*args)
}
