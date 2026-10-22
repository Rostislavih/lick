package com.rostis.lick

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform