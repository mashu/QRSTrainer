package com.so5km.qrstrainer.utils

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Generic object pool to reduce GC pressure for frequently created objects
 */
class ObjectPool<T>(
    private val factory: () -> T,
    private val reset: (T) -> Unit = {},
    private val maxSize: Int = 20
) {
    private val pool = ConcurrentLinkedQueue<T>()
    
    fun acquire(): T {
        return pool.poll() ?: factory()
    }
    
    fun release(obj: T) {
        if (pool.size < maxSize) {
            reset(obj)
            pool.offer(obj)
        }
    }
    
    fun clear() {
        pool.clear()
    }
}

/**
 * Poolable interface for objects that can be reset
 */
interface Poolable {
    fun reset()
} 