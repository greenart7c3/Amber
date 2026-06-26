package com.greenart7c3.nostrsigner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.greenart7c3.nostrsigner.signer.RemoteSigner

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
        // A ContentProvider query can arrive before Application.onCreate has bound
        // the signer service (provider onCreate runs first). Bind is idempotent.
        RemoteSigner.bind(ctx)
        // External apps are always attributed to their own Android package. The
        // requester identity is taken from the binder's calling package, never from
        // caller-supplied query arguments, so an app cannot impersonate another one.
        val caller = callingPackage
        if (caller == null) {
            AmberLog.d(Amber.TAG, "No calling package")
            return null
        }
        return SignerProviderQuery.query(
            context = ctx,
            requesterId = caller,
            callerPackageName = caller,
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
