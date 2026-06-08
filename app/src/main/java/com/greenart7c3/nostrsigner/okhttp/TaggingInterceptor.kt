package com.greenart7c3.nostrsigner.okhttp

import android.net.TrafficStats
import okhttp3.Interceptor
import okhttp3.Response

class TaggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (TrafficStats.getThreadStatsTag() == -1) {
            TrafficStats.setThreadStatsTag(0x0001)
        }
        return chain.proceed(chain.request())
    }
}
