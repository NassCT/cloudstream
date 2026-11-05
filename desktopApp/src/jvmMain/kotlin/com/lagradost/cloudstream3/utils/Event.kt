package com.lagradost.cloudstream3.utils

/** Same as the Android app's Event (app module), used by extensions to trigger reloads. */
class Event<T> {
    private val observers = mutableSetOf<(T) -> Unit>()

    val size: Int get() = observers.size

    operator fun plusAssign(observer: (T) -> Unit) {
        synchronized(observers) { observers.add(observer) }
    }

    operator fun minusAssign(observer: (T) -> Unit) {
        synchronized(observers) { observers.remove(observer) }
    }

    operator fun invoke(value: T) {
        synchronized(observers) { observers.toList() }.forEach { it(value) }
    }
}
