package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AmberButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors().copy(
        contentColor = Color.Black,
    ),
    textColor: Color = Color.Unspecified,
    text: String,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(
            shape = RoundedCornerShape(20),
            enabled = enabled,
            onClick = onClick,
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(
                text = text,
                color = textColor,
                modifier = Modifier.scale(1.50f),
                textAlign = textAlign,
                maxLines = maxLines,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
fun AmberElevatedButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    textColor: Color = Color.Unspecified,
    text: String,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    contentColor: Color = Color.Unspecified,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        ElevatedButton(
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = contentColor,
            ),
            shape = RoundedCornerShape(20),
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(
                text = text,
                color = textColor,
                modifier = Modifier.scale(1.50f),
                textAlign = textAlign,
                maxLines = maxLines,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}
