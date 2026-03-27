package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R

@Composable
fun MnemonicLoginInput(
    wordCount: Int,
    words: List<String>,
    onWordChange: (index: Int, word: String) -> Unit,
    onWordCountChange: (Int) -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(wordCount) { List(wordCount) { FocusRequester() } }
    val halfCount = wordCount / 2

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = wordCount == 12,
                onClick = { onWordCountChange(12) },
                label = { Text(stringResource(R.string.mnemonic_12_words)) },
            )
            FilterChip(
                selected = wordCount == 24,
                onClick = { onWordCountChange(24) },
                label = { Text(stringResource(R.string.mnemonic_24_words)) },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPaste) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = stringResource(R.string.paste_from_clipboard),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Left column: words 1..halfCount
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in 0 until halfCount) {
                    WordField(
                        displayNumber = i + 1,
                        word = words.getOrElse(i) { "" },
                        onWordChange = { onWordChange(i, it) },
                        focusRequester = focusRequesters[i],
                        nextFocusRequester = focusRequesters.getOrNull(i + 1),
                        imeAction = ImeAction.Next,
                    )
                }
            }

            // Right column: words halfCount+1..wordCount
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in halfCount until wordCount) {
                    WordField(
                        displayNumber = i + 1,
                        word = words.getOrElse(i) { "" },
                        onWordChange = { onWordChange(i, it) },
                        focusRequester = focusRequesters[i],
                        nextFocusRequester = focusRequesters.getOrNull(i + 1),
                        imeAction = if (i < wordCount - 1) ImeAction.Next else ImeAction.Done,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val filledCount = words.count { it.isNotBlank() }
        Text(
            text = stringResource(R.string.mnemonic_words_entered, filledCount, wordCount),
            style = MaterialTheme.typography.bodySmall,
            color = if (filledCount == wordCount) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun WordField(
    displayNumber: Int,
    word: String,
    onWordChange: (String) -> Unit,
    focusRequester: FocusRequester,
    nextFocusRequester: FocusRequester?,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = word,
        onValueChange = { raw ->
            val cleaned = raw.trimStart().toLowerCase(Locale.current)
            if (cleaned.contains(" ")) {
                val firstWord = cleaned.substringBefore(" ").trim()
                if (firstWord.isNotEmpty()) {
                    onWordChange(firstWord)
                }
                nextFocusRequester?.requestFocus()
            } else {
                onWordChange(cleaned)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        prefix = {
            Text(
                text = "$displayNumber.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        textStyle = TextStyle(fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { nextFocusRequester?.requestFocus() },
            onDone = {},
        ),
    )
}
