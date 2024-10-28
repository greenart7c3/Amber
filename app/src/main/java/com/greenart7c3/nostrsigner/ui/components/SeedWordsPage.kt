package com.greenart7c3.nostrsigner.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R

@Composable
fun SeedWordsPage(
    seedWords: Set<String>,
    showNextButton: Boolean = true,
    onNextPage: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
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
                    clipboardManager.setText(AnnotatedString(seedWords.joinToString(" ")))
                },
                content = {
                    Text(text = stringResource(R.string.copy_to_clipboard))
                },
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
                    content = {
                        Text(text = stringResource(R.string.next))
                    },
                )
            }
        }
    }
}
