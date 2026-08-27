package com.baniterio.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform