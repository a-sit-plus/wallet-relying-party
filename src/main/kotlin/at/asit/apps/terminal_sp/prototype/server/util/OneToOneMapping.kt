package at.asit.apps.terminal_sp.prototype.server.util

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


class OneToOneMapping<Type1, Type2> {
    val byFirst: MutableMap<Type1, Type2> = HashMap()
    val bySecond: MutableMap<Type2, Type1> = HashMap()
    private val lock: Lock = ReentrantLock()
    fun put(value1: Type1, value2: Type2) {
        lock.lock()
        val old2 = byFirst.put(value1, value2)
        val old1 = bySecond.put(value2, value1)
        if (old2 !== value2) {
            bySecond.remove(old2)
        }
        if (old1 !== value1) {
            byFirst.remove(old1)
        }
        lock.unlock()
    }

    fun getByFirst(value1: Type1): Type2? {
        lock.lock()
        val value2 = byFirst[value1]
        lock.unlock()
        return value2
    }

    fun getBySecond(value2: Type2): Type1? {
        lock.lock()
        val value1 = bySecond[value2]
        lock.unlock()
        return value1
    }

    fun removeByFirst(value1: Type1) {
        lock.lock()
        val value2 = byFirst.remove(value1)
        bySecond.remove(value2)
        lock.unlock()
    }

    fun removeBySecond(value2: Type2) {
        lock.lock()
        val value1 = bySecond.remove(value2)
        byFirst.remove(value1)
        lock.unlock()
    }
}