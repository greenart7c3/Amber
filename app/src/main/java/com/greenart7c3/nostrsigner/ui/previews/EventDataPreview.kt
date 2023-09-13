package com.greenart7c3.nostrsigner.ui.previews

import android.annotation.SuppressLint
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.model.Event
import com.greenart7c3.nostrsigner.ui.EventData
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Preview(device = "id:Nexus S")
@Composable
fun EventDataPreview() {
    val event = Event("123", "7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19", TimeUtils.now(), 1, listOf(), "This is a test 123 123 123 123 123 123", "")
    val data = event.toJson()
    val remember = remember { mutableStateOf(false) }

    NostrSignerTheme(darkTheme = true) {
        Scaffold {
            EventData(
                false,
                remember,
                null,
                "App",
                event,
                data,
                { },
                { }
            )
        }
    }
}