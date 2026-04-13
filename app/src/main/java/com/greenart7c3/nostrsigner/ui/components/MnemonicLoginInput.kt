package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.PopupProperties
import com.greenart7c3.nostrsigner.R
import com.vitorpamplona.quartz.nip06KeyDerivation.Bip39Mnemonics

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
    val wordList = remember { Bip39Mnemonics.englishWordlist.toList() }

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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in 0 until halfCount) {
                    WordField(
                        displayNumber = i + 1,
                        word = words.getOrElse(i) { "" },
                        wordList = wordList,
                        onWordChange = { onWordChange(i, it) },
                        focusRequester = focusRequesters[i],
                        nextFocusRequester = focusRequesters.getOrNull(i + 1),
                        imeAction = ImeAction.Next,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in halfCount until wordCount) {
                    WordField(
                        displayNumber = i + 1,
                        word = words.getOrElse(i) { "" },
                        wordList = wordList,
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
    wordList: List<String>,
    onWordChange: (String) -> Unit,
    focusRequester: FocusRequester,
    nextFocusRequester: FocusRequester?,
    imeAction: ImeAction,
) {
    // `remember(word)` resets this state synchronously whenever `word` changes.
    // Initialising to `word` when it is already a complete BIP-39 entry
    // suppresses the dropdown immediately — no async gap — covering both the
    // paste case (word set externally, onValueChange never called) and the
    // case where the user finishes typing a valid word.
    var selectedWord by remember(word) { mutableStateOf(if (word in wordList) word else "") }

    val suggestions = remember(word) {
        if (word.length >= 2) {
            wordList.filter { it.startsWith(word) }.take(5)
        } else {
            emptyList()
        }
    }

    val menuExpanded = suggestions.isNotEmpty() && word != selectedWord

    Box {
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
                    selectedWord = "" // reset so menu can reappear while typing
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

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { selectedWord = word },
            modifier = Modifier.heightIn(max = 200.dp),
            properties = PopupProperties(focusable = false),
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(text = suggestion, fontSize = 14.sp) },
                    onClick = {
                        selectedWord = suggestion
                        onWordChange(suggestion)
                        nextFocusRequester?.requestFocus()
                    },
                )
            }
        }
    }
}
