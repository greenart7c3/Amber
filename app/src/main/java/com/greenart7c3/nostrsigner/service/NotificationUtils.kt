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
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.greenart7c3.nostrsigner.MainActivity
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.ui.navigation.Route

object NotificationUtils {
    private var bunkerChannel: NotificationChannel? = null
    private var errorsChannel: NotificationChannel? = null
    private var bunkerGroup: NotificationChannelGroup? = null
    private var errorsGroup: NotificationChannelGroup? = null

    fun getOrCreateBunkerGroup(applicationContext: Context): NotificationChannelGroup {
        if (bunkerGroup != null) return bunkerGroup!!
        bunkerGroup =
            NotificationChannelGroup(
                "BunkerGroup",
                applicationContext.getString(R.string.bunker_notifications),
            )
        return bunkerGroup!!
    }

    fun getOrCreateErrorsGroup(applicationContext: Context): NotificationChannelGroup {
        if (errorsGroup != null) return errorsGroup!!
        errorsGroup =
            NotificationChannelGroup(
                "ErrorsGroup",
                applicationContext.getString(R.string.error_notifications),
            )
        return errorsGroup!!
    }

    fun getOrCreateBunkerChannel(applicationContext: Context): NotificationChannel {
        if (bunkerChannel != null) return bunkerChannel!!

        getOrCreateBunkerGroup(applicationContext)

        bunkerChannel =
            NotificationChannel(
                "BunkerID",
                applicationContext.getString(R.string.bunker_notifications),
                NotificationManager.IMPORTANCE_HIGH,
            )
                .apply {
                    description = applicationContext.getString(R.string.notifications_for_approving_or_rejecting_bunker_requests)
                    group = bunkerGroup?.id
                }

        // Register the channel with the system
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(bunkerChannel!!)

        return bunkerChannel!!
    }

    fun getOrCreateErrorsChannel(applicationContext: Context): NotificationChannel {
        if (errorsChannel != null) return errorsChannel!!

        getOrCreateErrorsGroup(applicationContext)

        errorsChannel =
            NotificationChannel(
                "ErrorID",
                applicationContext.getString(R.string.error_notifications),
                NotificationManager.IMPORTANCE_HIGH,
            )
                .apply {
                    description = applicationContext.getString(R.string.notifications_for_approving_or_rejecting_bunker_requests)
                    group = errorsGroup?.id
                }

        // Register the channel with the system
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(bunkerChannel!!)

        return bunkerChannel!!
    }

    fun NotificationManager.sendNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        channelId: String,
        applicationContext: Context,
        bunkerRequest: AmberBunkerRequest,
    ) {
        sendNotification(
            id = id,
            messageBody = messageBody,
            messageTitle = messageTitle,
            picture = null,
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
        channelId: String,
        applicationContext: Context,
        bunkerRequest: AmberBunkerRequest,
    ) {
        val notId = id.hashCode()
        val notifications: Array<StatusBarNotification> = activeNotifications
        for (notification in notifications) {
            if (notification.id == notId) {
                return
            }
        }

        val contentIntent = Intent(applicationContext, MainActivity::class.java)
        contentIntent.putExtra("route", Route.IncomingRequest.route)
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                notId,
                contentIntent,
                PendingIntent.FLAG_MUTABLE,
            )

        BunkerRequestUtils.addRequest(bunkerRequest)

        // Build the notification
        val builderPublic =
            NotificationCompat.Builder(
                applicationContext,
                channelId,
            )
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(messageTitle)
                .setContentText(applicationContext.getString(R.string.new_event_to_sign))
                .setLargeIcon(picture?.bitmap)
                .setGroup(bunkerGroup?.id)
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
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(messageTitle)
                .setContentText(messageBody)
                .setLargeIcon(picture?.bitmap)
                .setGroup(bunkerGroup?.id)
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

    private fun NotificationManager.sendErrorNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        picture: BitmapDrawable?,
        channelId: String,
        applicationContext: Context,
        bunkerRequest: AmberBunkerRequest,
    ) {
        val notId = id.hashCode()
        val notifications: Array<StatusBarNotification> = activeNotifications
        for (notification in notifications) {
            if (notification.id == notId) {
                return
            }
        }

        val contentIntent = Intent(applicationContext, MainActivity::class.java)
        contentIntent.putExtra("route", Route.IncomingRequest.route)
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                notId,
                contentIntent,
                PendingIntent.FLAG_MUTABLE,
            )

        BunkerRequestUtils.addRequest(bunkerRequest)

        // Build the notification
        val builderPublic =
            NotificationCompat.Builder(
                applicationContext,
                channelId,
            )
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(messageTitle)
                .setContentText(applicationContext.getString(R.string.new_event_to_sign))
                .setLargeIcon(picture?.bitmap)
                .setGroup(errorsGroup?.id)
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
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(messageTitle)
                .setContentText(messageBody)
                .setLargeIcon(picture?.bitmap)
                .setGroup(errorsGroup?.id)
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
