package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.mnemonic_12_words
import com.greenart7c3.nostrsigner.commonsui.mnemonic_24_words
import com.greenart7c3.nostrsigner.commonsui.mnemonic_words_entered
import com.greenart7c3.nostrsigner.commonsui.paste_from_clipboard
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark
import com.vitorpamplona.quartz.nip06KeyDerivation.Bip39Mnemonics
import org.jetbrains.compose.resources.stringResource

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
        WordCountRow(wordCount = wordCount, onWordCountChange = onWordCountChange, onPaste = onPaste)

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (indices in listOf(0 until halfCount, halfCount until wordCount)) {
                WordColumn(
                    indices = indices,
                    words = words,
                    wordList = wordList,
                    focusRequesters = focusRequesters,
                    onWordChange = onWordChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        WordsEntered(filledCount = words.count { it.isNotBlank() }, wordCount = wordCount)
    }
}

@Composable
private fun WordCountRow(
    wordCount: Int,
    onWordCountChange: (Int) -> Unit,
    onPaste: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = wordCount == 12,
            onClick = { onWordCountChange(12) },
            label = { Text(stringResource(Res.string.mnemonic_12_words)) },
        )
        FilterChip(
            selected = wordCount == 24,
            onClick = { onWordCountChange(24) },
            label = { Text(stringResource(Res.string.mnemonic_24_words)) },
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onPaste) {
            Icon(
                imageVector = Icons.Outlined.ContentPaste,
                contentDescription = stringResource(Res.string.paste_from_clipboard),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun WordColumn(
    indices: IntRange,
    words: List<String>,
    wordList: List<String>,
    focusRequesters: List<FocusRequester>,
    onWordChange: (index: Int, word: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in indices) {
            WordField(
                displayNumber = i + 1,
                word = words.getOrElse(i) { "" },
                wordList = wordList,
                onWordChange = { onWordChange(i, it) },
                focusRequester = focusRequesters[i],
                nextFocusRequester = focusRequesters.getOrNull(i + 1),
                imeAction = if (i < focusRequesters.lastIndex) ImeAction.Next else ImeAction.Done,
            )
        }
    }
}

@Composable
private fun WordsEntered(
    filledCount: Int,
    wordCount: Int,
) {
    Text(
        text = stringResource(Res.string.mnemonic_words_entered, filledCount, wordCount),
        style = MaterialTheme.typography.bodySmall,
        color = if (filledCount == wordCount) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

internal data class TypedWord(val word: String?, val movesOn: Boolean)

internal fun typedWord(raw: String): TypedWord {
    val cleaned = raw.trimStart().lowercase()
    if (!cleaned.contains(" ")) return TypedWord(cleaned, movesOn = false)
    val firstWord = cleaned.substringBefore(" ").trim()
    return TypedWord(firstWord.ifEmpty { null }, movesOn = true)
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
    @Suppress("NoComments")
    var selectedWord by remember(word) {
        // `remember(word)` resets this state synchronously whenever `word` changes.
        // Initializing to `word` when it is already a complete BIP-39 entry
        // suppresses the dropdown immediately — no async gap — covering both the
        // paste case (word set externally, onValueChange never called) and the
        // case where the user finishes typing a valid word.
        mutableStateOf(if (word in wordList) word else "")
    }

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
                val typed = typedWord(raw)
                if (!typed.movesOn) selectedWord = ""
                typed.word?.let(onWordChange)
                if (typed.movesOn) nextFocusRequester?.requestFocus()
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

        WordSuggestions(
            expanded = menuExpanded,
            suggestions = suggestions,
            onDismiss = { selectedWord = word },
            onPick = { suggestion ->
                selectedWord = suggestion
                onWordChange(suggestion)
                nextFocusRequester?.requestFocus()
            },
        )
    }
}

@Composable
private fun WordSuggestions(
    expanded: Boolean,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = 200.dp),
        properties = PopupProperties(focusable = false),
    ) {
        suggestions.forEach { suggestion ->
            DropdownMenuItem(
                text = { Text(text = suggestion, fontSize = 14.sp) },
                onClick = { onPick(suggestion) },
            )
        }
    }
}

@PreviewsLightDark
@Composable
private fun MnemonicLoginInputPreview() {
    AmberPreview {
        MnemonicLoginInput(
            wordCount = 12,
            words = listOf("leader", "monkey", "parrot", "ring") + List(8) { "" },
            onWordChange = { _, _ -> },
            onWordCountChange = {},
            onPaste = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
