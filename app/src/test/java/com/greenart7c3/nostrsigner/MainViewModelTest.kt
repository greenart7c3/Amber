package com.greenart7c3.nostrsigner

import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.IntentUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelTest {

    @After
    fun tearDown() {
        IntentUtils.clear()
    }

    private fun intentData(id: String, data: String = "data") = IntentData(
        data = data,
        name = "TestApp",
        type = SignerType.GET_PUBLIC_KEY,
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
    )

    @Test
    fun `addAll adds new intents`() {
        IntentUtils.addAll(listOf(intentData("id1"), intentData("id2")))
        assertEquals(2, IntentUtils.intents.value.size)
    }

    @Test
    fun `addAll ignores intents with duplicate id already in state`() {
        IntentUtils.addAll(listOf(intentData("id1")))
        IntentUtils.addAll(listOf(intentData("id1")))
        assertEquals(1, IntentUtils.intents.value.size)
    }

    @Test
    fun `addAll ignores intents with duplicate id even when other fields differ`() {
        IntentUtils.addAll(listOf(intentData("id1", data = "original")))
        IntentUtils.addAll(listOf(intentData("id1", data = "different")))
        assertEquals(1, IntentUtils.intents.value.size)
        assertEquals("original", IntentUtils.intents.value.first().data)
    }

    @Test
    fun `addAll deduplicates within a single batch`() {
        IntentUtils.addAll(listOf(intentData("id1"), intentData("id1")))
        assertEquals(1, IntentUtils.intents.value.size)
    }

    @Test
    fun `addAll allows intents with different ids`() {
        IntentUtils.addAll(listOf(intentData("id1"), intentData("id2"), intentData("id3")))
        assertEquals(3, IntentUtils.intents.value.size)
    }

    @Test
    fun `addAll appends to existing intents`() {
        IntentUtils.addAll(listOf(intentData("id1")))
        IntentUtils.addAll(listOf(intentData("id2")))
        assertEquals(2, IntentUtils.intents.value.size)
    }

    @Test
    fun `addAll with empty list leaves state unchanged`() {
        IntentUtils.addAll(listOf(intentData("id1")))
        IntentUtils.addAll(emptyList())
        assertEquals(1, IntentUtils.intents.value.size)
    }
}
