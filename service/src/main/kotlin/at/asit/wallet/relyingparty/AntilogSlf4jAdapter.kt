package at.asit.wallet.relyingparty

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.regex.Pattern

/**
 * Enables logging from Napier (used in our KMM libs) to SLF4J (used by Spring Boot)
 */
object AntilogSlf4jAdapter : Antilog() {

    val lastTransactions = ArrayDeque<String>()
    val transactionLogs: MutableMap<String, MutableList<String>> = mutableMapOf()

    override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
        if (message == null)
            return
        val logger = LoggerFactory.getLogger(tag ?: extractTagFromStackTrace())
        when (priority) {
            LogLevel.VERBOSE -> logger.trace(message.prefixWithRequestId(), throwable)
            LogLevel.DEBUG -> logger.debug(message.prefixWithRequestId(), throwable)
            LogLevel.INFO -> logger.info(message.prefixWithRequestId(), throwable)
            LogLevel.WARNING -> logger.warn(message.prefixWithRequestId(), throwable)
            LogLevel.ERROR -> logger.error(message.prefixWithRequestId(), throwable)
            LogLevel.ASSERT -> logger.error(message.prefixWithRequestId(), throwable)
        }
        storeInTransactionLogs(message)
    }

    private fun storeInTransactionLogs(message: String) {
        MDC.get(MDC_REQUEST_ID)?.let {
            if (!lastTransactions.contains(it)) {
                lastTransactions.addLast(it)
            }
            if (lastTransactions.size > 30) {
                lastTransactions.removeFirst().apply { transactionLogs.remove(it) }
                return
            }
            transactionLogs.getOrPut(it) { mutableListOf() }.add(message)
        }
    }

    // From Napier's DebugAntilog
    private val anonymousClass = Pattern.compile("(\\$\\d+)+$")

    // From Napier's DebugAntilog
    private fun removeAnonymousClasses(className: String): String {
        var tag = className
        val matcher = anonymousClass.matcher(tag)
        if (matcher.find()) {
            tag = matcher.replaceAll("")
        }
        return tag
    }

    // Adapted from Napier's DebugAntilog
    private fun extractTagFromStackTrace(): String {
        val callingMethod = Thread.currentThread().stackTrace.dropWhile {
            it.className.contains(Thread::class.java.name) ||
                    it.className.contains(this::class.java.name) ||
                    it.className.contains(Napier::class.java.name) ||
                    it.className.contains(Antilog::class.java.name)
        }.firstOrNull()
        return callingMethod?.let { removeAnonymousClasses(it.className) } ?: "at.asitplus.wallet.lib"
    }

}

private fun String.prefixWithRequestId(): String =
    MDC.get(MDC_REQUEST_ID)?.let { "[$it] $this" } ?: this

const val MDC_REQUEST_ID = "requestid"