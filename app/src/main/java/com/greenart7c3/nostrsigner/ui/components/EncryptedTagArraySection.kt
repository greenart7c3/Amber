package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.tags
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import org.jetbrains.compose.resources.stringResource

@Composable
fun EncryptedTagArraySection(
    modifier: Modifier,
    tags: TagArray,
) {
    val copyToClipboard = rememberCopyToClipboard()
    LazyColumn(
        modifier = modifier,
    ) {
        item {
            TagsSection(
                stringResource(Res.string.tags),
                tags,
                {
                    copyToClipboard(tags.joinToString(separator = ", ") { "[${it.joinToString(separator = ", ") { tag -> "\"${tag}\"" }}]" })
                },
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun EncryptedTagArraySectionPreview() {
    AmberPreview {
        EncryptedTagArraySection(
            modifier = Modifier,
            tags = arrayOf(
                arrayOf("p", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                arrayOf("t", "amber"),
            ),
        )
    }
}
