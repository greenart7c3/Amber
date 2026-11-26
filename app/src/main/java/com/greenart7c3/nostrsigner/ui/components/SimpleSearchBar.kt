package com.greenart7c3.nostrsigner.ui.components

import android.view.ViewTreeObserver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.search
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleSearchBar(
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    searchResults: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var localSearchResults by remember { mutableStateOf(searchResults) }

    KeyboardHiddenEffect {
        onSearch(textFieldState.text.toString())
        expanded = false
    }

    Box(
        modifier
            .semantics { isTraversalGroup = true },
    ) {
        SearchBar(
            windowInsets = WindowInsets(0),
            inputField = {
                SearchBarDefaults.InputField(
                    modifier = Modifier.fillMaxWidth(),
                    query = textFieldState.text.toString(),
                    onQueryChange = {
                        textFieldState.edit { replace(0, length, it) }
                        localSearchResults = searchResults.filter { result ->
                            result.contains(textFieldState.text.toString(), ignoreCase = true)
                        }
                    },
                    onSearch = {
                        onSearch(textFieldState.text.toString())
                        expanded = false
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = { Text(stringResource(Res.string.search)) },
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            SearchSuggestions(
                suggestions = localSearchResults,
                onPick = { result ->
                    textFieldState.edit { replace(0, length, result) }
                    onSearch(textFieldState.text.toString())
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun KeyboardHiddenEffect(onKeyboardHide: () -> Unit) {
    val view = LocalView.current
    val viewTreeObserver = view.viewTreeObserver
    val currentOnKeyboardHide by rememberUpdatedState(onKeyboardHide)
    DisposableEffect(viewTreeObserver) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val isKeyboardOpen = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) != false
            if (!isKeyboardOpen) {
                currentOnKeyboardHide()
            }
        }

        viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
}

@Composable
private fun SearchSuggestions(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(suggestions) { result ->
            ListItem(
                headlineContent = { Text(result) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(result) },
            )
        }
    }
}

@PreviewsLightDark
@Composable
private fun SimpleSearchBarPreview() {
    AmberPreview {
        SimpleSearchBar(
            textFieldState = TextFieldState("amber"),
            onSearch = {},
            searchResults = emptyList(),
        )
    }
}
