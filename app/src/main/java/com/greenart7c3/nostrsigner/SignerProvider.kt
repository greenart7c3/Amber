package com.greenart7c3.nostrsigner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

class SignerProvider : ContentProvider() {
    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context
        if (ctx == null) {
            AmberLog.d(Amber.TAG, "No context")
            return null
        }
        val binderCallingPackage = callingPackage
        if (binderCallingPackage == null) {
            AmberLog.d(Amber.TAG, "No calling package")
            return null
        }
        val amber = ctx.amber
        return SignerProviderQuery(amber, amber.accountDatabases, amber.applicationIOScope).query(
            context = ctx,
            requesterId = binderCallingPackage,
            callerPackageName = binderCallingPackage,
            operationUri = uri,
            arguments = projection,
        )
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0
}
