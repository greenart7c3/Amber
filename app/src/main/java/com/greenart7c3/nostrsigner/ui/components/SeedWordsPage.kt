package com.greenart7c3.nostrsigner.ui.components

import android.content.ClipData
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import kotlinx.coroutines.launch

@Composable
fun SeedWordsPage(
    seedWords: Set<String>,
    showNextButton: Boolean = true,
    onNextPage: () -> Unit = {},
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.seed_words_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.seed_words_explainer),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            AmberButton(
                onClick = {
                    scope.launch {
                        clipboardManager.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText("", seedWords.joinToString(" ")),
                            ),
                        )
                    }
                },
                text = stringResource(R.string.copy_to_clipboard),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                val size = seedWords.size
                val count = size / 2

                val firstColumnWords = seedWords.mapIndexedNotNull {
                        index, word ->
                    if (index < count) {
                        word
                    } else {
                        null
                    }
                }
                val secondColumnWords = seedWords.mapIndexedNotNull {
                        index, word ->
                    if (index >= count) {
                        word
                    } else {
                        null
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    firstColumnWords.forEachIndexed { index, word ->
                        OutlinedTextField(
                            word,
                            onValueChange = {},
                            modifier = Modifier.padding(8.dp),
                            readOnly = true,
                            prefix = {
                                Text("${index + 1} - ")
                            },
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    secondColumnWords.forEachIndexed { index, word ->
                        OutlinedTextField(
                            word,
                            onValueChange = {},
                            readOnly = true,
                            prefix = {
                                Text("${index + 1 + firstColumnWords.size} - ")
                            },
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            if (showNextButton) {
                AmberButton(
                    onClick = onNextPage,
                    text = stringResource(R.string.next),
                )
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    Amber.instance.getMainActivity()?.window?.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                }

                Lifecycle.Event.ON_PAUSE -> {
                    Amber.instance.getMainActivity()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            Amber.instance.getMainActivity()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
