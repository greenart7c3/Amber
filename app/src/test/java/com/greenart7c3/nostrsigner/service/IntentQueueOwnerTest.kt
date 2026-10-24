package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NV-02: each calling app gets its own SignerActivity, but they shared one
 * request queue, so a second app's screen showed and returned the first
 * app's pending request. Requests now carry the screen that queued them.
 */
class IntentQueueOwnerTest {
    private fun intent(id: String, owner: String) = IntentData(
        data = "",
        name = "",
        type = SignerType.SIGN_EVENT,
        pubKey = "",
        id = id,
        callBackUrl = null,
        compression = CompressionType.NONE,
        returnType = ReturnType.SIGNATURE,
        permissions = null,
        currentAccount = "",
        route = null,
        event = null,
        encryptedData = null,
        owner = owner,
    )

    @After
    fun clearQueue() = IntentUtils.clear()

    @Test
    fun twoAppsUsingTheSameRequestIdKeepBothRequests() {
        IntentUtils.addAll(listOf(intent("1", "screen-a")))
        IntentUtils.addAll(listOf(intent("1", "screen-b")))
        assertEquals(2, IntentUtils.intents.value.size)
    }

    @Test
    fun aRepeatedRequestFromTheSameScreenIsQueuedOnce() {
        IntentUtils.addAll(listOf(intent("1", "screen-a")))
        IntentUtils.addAll(listOf(intent("1", "screen-a")))
        assertEquals(1, IntentUtils.intents.value.size)
    }

    @Test
    fun eachScreenSeesOnlyItsOwnRequests() {
        IntentUtils.addAll(listOf(intent("a1", "screen-a"), intent("b1", "screen-b")))
        assertEquals(listOf("b1"), IntentUtils.intents.value.filter { it.owner == "screen-b" }.map { it.id })
        assertEquals("screen-b:b1", intent("b1", "screen-b").queueKey)
    }
}
