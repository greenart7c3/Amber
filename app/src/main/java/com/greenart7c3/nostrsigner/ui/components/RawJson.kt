package com.greenart7c3.nostrsigner.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun RawJson(
    rawJson: String,
    modifier: Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    OutlinedTextField(
        modifier = modifier,
        value = TextFieldValue(JSONObject(rawJson).toString(2)),
        onValueChange = { },
        readOnly = true
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            shape = ButtonBorder,
            onClick = {
                clipboardManager.setText(AnnotatedString(rawJson))

                coroutineScope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.raw_json_copied_to_the_clipboard),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        ) {
            Text(stringResource(R.string.copy_raw_json))
        }
    }
}
