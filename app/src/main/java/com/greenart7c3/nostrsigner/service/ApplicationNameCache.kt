package com.greenart7c3.nostrsigner.service

import java.util.concurrent.ConcurrentHashMap

object ApplicationNameCache {
    val names = ConcurrentHashMap<String, String>()
}
