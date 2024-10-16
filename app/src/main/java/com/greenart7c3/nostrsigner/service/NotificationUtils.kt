/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.greenart7c3.nostrsigner.MainActivity
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.ui.navigation.Route

object NotificationUtils {
    private var dmChannel: NotificationChannel? = null

    fun getOrCreateDMChannel(applicationContext: Context): NotificationChannel {
        if (dmChannel != null) return dmChannel!!

        dmChannel =
            NotificationChannel(
                "BunkerID",
                applicationContext.getString(R.string.bunker_notifications),
                NotificationManager.IMPORTANCE_HIGH,
            )
                .apply {
                    description = applicationContext.getString(R.string.notifications_for_approving_or_rejecting_bunker_requests)
                }

        // Register the channel with the system
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(dmChannel!!)

        return dmChannel!!
    }

    fun NotificationManager.sendNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        uri: String,
        channelId: String,
        applicationContext: Context,
        bunkerRequest: BunkerRequest,
    ) {
        sendNotification(
            id = id,
            messageBody = messageBody,
            messageTitle = messageTitle,
            picture = null,
            uri = uri,
            channelId,
            applicationContext = applicationContext,
            bunkerRequest,
        )
    }

    private fun NotificationManager.sendNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        picture: BitmapDrawable?,
        uri: String,
        channelId: String,
        applicationContext: Context,
        bunkerRequest: BunkerRequest,
    ) {
        val notId = id.hashCode()
        val notifications: Array<StatusBarNotification> = getActiveNotifications()
        for (notification in notifications) {
            if (notification.id == notId) {
                return
            }
        }

        val contentIntent = Intent(applicationContext, MainActivity::class.java).apply { data = Uri.parse(uri) }
        contentIntent.putExtra("bunker", bunkerRequest.toJson())
        contentIntent.putExtra("route", Route.IncomingRequest.route)
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                notId,
                contentIntent,
                PendingIntent.FLAG_MUTABLE,
            )

        IntentUtils.addRequest(notId.toString(), bunkerRequest)

        // Build the notification
        val builderPublic =
            NotificationCompat.Builder(
                applicationContext,
                channelId,
            )
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setColor(0xFFBF00)
                .setContentTitle(messageTitle)
                .setContentText(applicationContext.getString(R.string.new_event_to_sign))
                .setLargeIcon(picture?.bitmap)
                // .setGroup(messageTitle)
                // .setGroup(notificationGroupKey) //-> Might need a Group summary as well before we
                // activate this
                .setContentIntent(contentPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setExtras(
                    Bundle().apply {
                        putString("route", Route.IncomingRequest.route)
                    },
                )

        // Build the notification
        val builder =
            NotificationCompat.Builder(
                applicationContext,
                channelId,
            )
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setColor(0xFFBF00)
                .setContentTitle(messageTitle)
                .setContentText(messageBody)
                .setLargeIcon(picture?.bitmap)
                // .setGroup(messageTitle)
                // .setGroup(notificationGroupKey)  //-> Might need a Group summary as well before we
                // activate this
                .setContentIntent(contentPendingIntent)
                .setPublicVersion(builderPublic.build())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setExtras(
                    Bundle().apply {
                        putString("route", Route.IncomingRequest.route)
                    },
                )

        notify(notId, builder.build())
    }
}
