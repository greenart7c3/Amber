package com.greenart7c3.nostrsigner.ui.components

import android.content.ClipData
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun EncryptedTagArraySection(
    modifier: Modifier,
    tags: TagArray,
) {
    val clipboard = LocalClipboard.current
    LazyColumn(
        modifier = modifier,
    ) {
        item {
            TagsSection(
                stringResource(R.string.tags),
                tags,
                {
                    Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                        clipboard.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText("Tags", tags.joinToString(separator = ", ") { "[${it.joinToString(separator = ", ") { tag -> "\"${tag}\"" }}]" }),
                            ),
                        )
                    }
                },
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
