package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews

@Composable
fun TagsSection(
    label: String,
    tags: Array<Array<String>>,
    onCopy: () -> Unit,
    horizontalPadding: Int = 16,
    verticalPadding: Int = 12,
    showFullContent: Boolean = false,
) {
    var expanded by remember { mutableStateOf(showFullContent) }
    val tagsToShow = if (expanded) tags.toList() else tags.take(10)
    val moreCount = if (expanded) 0 else tags.size - 10

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (tags.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable { expanded = !expanded },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tagsToShow.forEach { tag ->
                        val formattedTag = tag.joinToString(separator = ", ") {
                            "\"${it}\""
                        }
                        Text(
                            text = formattedTag,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                            ),
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (moreCount > 0) {
                        Text(
                            text = stringResource(id = R.string.more, moreCount),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Icon(
            modifier = Modifier
                .size(16.dp)
                .clickable { onCopy() },
            imageVector = Icons.Default.ContentCopy,
            contentDescription = stringResource(id = R.string.copy_to_clipboard),
        )
    }
}

@ThemePreviews
@Composable
fun TagsSectionPreview() {
    AmberPreview {
        TagsSection(
            label = "Tags",
            tags = arrayOf(
                arrayOf("p", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                arrayOf("e", "3d842afecd5e293f28b6627933704a3fb8ce153aa91d790ab11f6a752d44a42d"),
                arrayOf("t", "amber"),
            ),
            onCopy = {},
        )
    }
}
