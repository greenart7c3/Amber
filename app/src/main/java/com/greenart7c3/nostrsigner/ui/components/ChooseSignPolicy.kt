package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R

@Composable
fun ChooseSignPolicy(
    selectedOption: Int,
    onSelected: (Int) -> Unit,
) {
    val radioOptions = listOf(
        TitleExplainer(
            title = stringResource(R.string.sign_policy_basic),
            explainer = stringResource(R.string.sign_policy_basic_explainer),
        ),
        TitleExplainer(
            title = stringResource(R.string.sign_policy_manual_new_app),
            explainer = stringResource(R.string.sign_policy_manual_new_app_explainer),
        ),
        TitleExplainer(
            title = stringResource(R.string.sign_policy_fully),
            explainer = stringResource(R.string.sign_policy_fully_explainer),
        ),
    )
    radioOptions.forEachIndexed { index, option ->
        val selected = selectedOption == index
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .selectable(
                    selected = selected,
                    onClick = {
                        onSelected(index)
                    },
                )
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(10.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF1D8802),
                )
            } else {
                RadioButton(
                    selected = false,
                    onClick = {
                        onSelected(index)
                    },
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = option.title,
                    modifier = Modifier.padding(start = 16.dp),
                    fontWeight = FontWeight.Medium,
                )
                option.explainer?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}
