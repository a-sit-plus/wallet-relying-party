package at.asit.terminal.prototype.server;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class OneToOneMapping<Type1, Type2> {
    final Map<Type1, Type2> byFirst = new HashMap<>();
    final Map<Type2, Type1> bySecond = new HashMap<>();

    private Lock lock = new ReentrantLock();

    public void put(Type1 value1, Type2 value2) {
        lock.lock();
        Type2 old2 = byFirst.put(value1, value2);
        Type1 old1 = bySecond.put(value2, value1);
        if(old2 != value2) {
            bySecond.remove(old2);
        }
        if(old1 != value1) {
            byFirst.remove(old1);
        }
        lock.unlock();
    }

    public Type2 getByFirst(Type1 value1) {
        lock.lock();
        Type2 value2 = byFirst.get(value1);
        lock.unlock();
        return value2;
    }
    public Type1 getBySecond(Type2 value2) {
        lock.lock();
        Type1 value1 = bySecond.get(value2);
        lock.unlock();
        return value1;
    }

    public void removeByFirst(Type1 value1) {
        lock.lock();
        Type2 value2 = byFirst.remove(value1);
        bySecond.remove(value2);
        lock.unlock();
    }
    public void removeBySecond(Type2 value2) {
        lock.lock();
        Type1 value1 = bySecond.remove(value2);
        bySecond.remove(value1);
        lock.unlock();
    }
}