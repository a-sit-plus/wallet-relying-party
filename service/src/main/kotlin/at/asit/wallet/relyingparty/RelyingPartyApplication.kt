package at.asit.wallet.relyingparty

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class RelyingPartyApplication

fun main(args: Array<String>) {
    runApplication<RelyingPartyApplication>(*args)
}
