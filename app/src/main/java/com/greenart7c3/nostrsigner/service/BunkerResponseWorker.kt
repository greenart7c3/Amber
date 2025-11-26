package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BunkerResponseWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        TODO("Not yet implemented")
    }
}
