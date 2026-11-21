package com.limer.antigooglity

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform