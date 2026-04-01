package com.greenart7c3.nostrsigner

import android.content.Context
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MainViewModelTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        viewModel = MainViewModel(mockk<Context>(relaxed = true))
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
        viewModel.addAll(listOf(intentData("id1"), intentData("id2")))
        assertEquals(2, viewModel.intents.value.size)
    }

    @Test
    fun `addAll ignores intents with duplicate id already in state`() {
        viewModel.addAll(listOf(intentData("id1")))
        viewModel.addAll(listOf(intentData("id1")))
        assertEquals(1, viewModel.intents.value.size)
    }

    @Test
    fun `addAll ignores intents with duplicate id even when other fields differ`() {
        viewModel.addAll(listOf(intentData("id1", data = "original")))
        viewModel.addAll(listOf(intentData("id1", data = "different")))
        assertEquals(1, viewModel.intents.value.size)
        assertEquals("original", viewModel.intents.value.first().data)
    }

    @Test
    fun `addAll deduplicates within a single batch`() {
        viewModel.addAll(listOf(intentData("id1"), intentData("id1")))
        assertEquals(1, viewModel.intents.value.size)
    }

    @Test
    fun `addAll allows intents with different ids`() {
        viewModel.addAll(listOf(intentData("id1"), intentData("id2"), intentData("id3")))
        assertEquals(3, viewModel.intents.value.size)
    }

    @Test
    fun `addAll appends to existing intents`() {
        viewModel.addAll(listOf(intentData("id1")))
        viewModel.addAll(listOf(intentData("id2")))
        assertEquals(2, viewModel.intents.value.size)
    }

    @Test
    fun `addAll with empty list leaves state unchanged`() {
        viewModel.addAll(listOf(intentData("id1")))
        viewModel.addAll(emptyList())
        assertEquals(1, viewModel.intents.value.size)
    }
}
