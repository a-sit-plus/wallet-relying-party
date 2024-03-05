package at.asit.apps.terminal_sp.prototype.server.util

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * Enables logging from Napier (used in our KMM libs) to SLF4J (used by Spring Boot)
 */
class AntilogSlf4jAdapter : Antilog() {

    override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
        val logger = LoggerFactory.getLogger(tag ?: extractTagFromStackTrace())
        when (priority) {
            LogLevel.VERBOSE -> logger.trace(message, throwable)
            LogLevel.DEBUG -> logger.debug(message, throwable)
            LogLevel.INFO -> logger.info(message, throwable)
            LogLevel.WARNING -> logger.warn(message, throwable)
            LogLevel.ERROR -> logger.error(message, throwable)
            LogLevel.ASSERT -> logger.error(message, throwable)
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