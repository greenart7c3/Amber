package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.RelayHealthTracker
import com.greenart7c3.nostrsigner.desktop.core.Strings
import kotlinx.coroutines.launch

/**
 * Connected/available relay counter with a click-to-reconnect action — the
 * desktop counterpart of the relay widget in the Android `AmberTopAppBar`.
 * Hidden while the client has no relays at all, like on Android.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelayStatusWidget(modifier: Modifier = Modifier) {
    val available by AmberDesktop.client.availableRelaysFlow().collectAsState()
    val connected by AmberDesktop.client.connectedRelaysFlow().collectAsState()
    val language by Strings.currentLanguage.collectAsState()
    val scope = rememberCoroutineScope()

    if (available.isEmpty() && connected.isEmpty()) return

    val label = Strings.get("reconnect", language)
    TooltipArea(
        modifier = modifier,
        tooltip = {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        },
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    scope.launch {
                        // Manual reconnect: give relays marked dead another chance.
                        RelayHealthTracker.reset()
                        AmberDesktop.engine.updateFilter()
                        AmberDesktop.reconnect()
                        Toaster.toast(Strings.get("d_reconnecting", language))
                    }
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${connected.size}/${available.size}", style = MaterialTheme.typography.labelLarge)
            Icon(RelaysIcon, label, Modifier.size(20.dp))
        }
    }
}

/** Port of the Android `R.drawable.relays` vector. */
private val RelaysIcon: ImageVector by lazy {
    val paths = listOf(
        "M13,20.25C13.414,20.25 13.75,20.586 13.75,21C13.75,21.414 13.414,21.75 13,21.75H6C4.796,21.75 3.954,21.741 3.337,21.606C2.792,21.487 2.387,21.276 2.055,20.944C1.724,20.613 1.513,20.208 1.394,19.663C1.259,19.046 1.25,18.204 1.25,17C1.25,15.796 1.259,14.954 1.394,14.337C1.513,13.792 1.724,13.387 2.055,13.056C2.387,12.724 2.792,12.513 3.337,12.394C3.954,12.259 4.796,12.25 6,12.25H18C19.204,12.25 20.046,12.259 20.663,12.394C21.208,12.513 21.613,12.724 21.944,13.056C22.276,13.387 22.487,13.792 22.606,14.337C22.741,14.954 22.75,15.796 22.75,17C22.75,18.204 22.741,19.046 22.606,19.663C22.487,20.208 22.276,20.613 21.944,20.944C21.613,21.276 21.208,21.487 20.663,21.606C20.046,21.741 19.204,21.75 18,21.75H17C16.586,21.75 16.25,21.414 16.25,21C16.25,20.586 16.586,20.25 17,20.25H18C18.9,20.25 19.575,20.253 20.095,20.184C20.442,20.137 20.697,20.071 20.884,19.884C21.071,19.697 21.137,19.442 21.183,19.095C21.253,18.575 21.25,17.9 21.25,17C21.25,16.1 21.253,15.425 21.183,14.905C21.137,14.558 21.071,14.303 20.884,14.116C20.697,13.929 20.442,13.863 20.095,13.816C19.575,13.747 18.9,13.75 18,13.75H6C5.1,13.75 4.425,13.747 3.905,13.816C3.558,13.863 3.303,13.929 3.116,14.116C2.929,14.303 2.863,14.558 2.817,14.905C2.747,15.425 2.75,16.1 2.75,17C2.75,17.9 2.747,18.575 2.817,19.095C2.863,19.442 2.929,19.697 3.116,19.884C3.303,20.071 3.558,20.137 3.905,20.184C4.425,20.253 5.1,20.25 6,20.25H13Z",
        "M11,2.75C10.586,2.75 10.25,2.414 10.25,2C10.25,1.586 10.586,1.25 11,1.25H18C19.204,1.25 20.046,1.259 20.663,1.394C21.208,1.513 21.613,1.724 21.944,2.055C22.276,2.387 22.487,2.792 22.606,3.337C22.741,3.954 22.75,4.796 22.75,6C22.75,7.204 22.741,8.046 22.606,8.662C22.487,9.208 22.276,9.613 21.944,9.945C21.613,10.276 21.208,10.487 20.663,10.606C20.046,10.741 19.204,10.75 18,10.75H6C4.796,10.75 3.954,10.741 3.337,10.606C2.792,10.487 2.387,10.276 2.055,9.945C1.724,9.613 1.513,9.208 1.394,8.662C1.259,8.046 1.25,7.204 1.25,6C1.25,4.796 1.259,3.954 1.394,3.337C1.513,2.792 1.724,2.387 2.055,2.055C2.387,1.724 2.792,1.513 3.337,1.394C3.954,1.259 4.796,1.25 6,1.25H7C7.414,1.25 7.75,1.586 7.75,2C7.75,2.414 7.414,2.75 7,2.75H6C5.1,2.75 4.425,2.747 3.905,2.817C3.558,2.863 3.303,2.929 3.116,3.116C2.929,3.303 2.863,3.558 2.817,3.905C2.747,4.425 2.75,5.1 2.75,6C2.75,6.9 2.747,7.575 2.817,8.095C2.863,8.442 2.929,8.697 3.116,8.884C3.303,9.071 3.558,9.137 3.905,9.183C4.425,9.253 5.1,9.25 6,9.25H18C18.9,9.25 19.575,9.253 20.095,9.183C20.442,9.137 20.697,9.071 20.884,8.884C21.071,8.697 21.137,8.442 21.183,8.095C21.253,7.575 21.25,6.9 21.25,6C21.25,5.1 21.253,4.425 21.183,3.905C21.137,3.558 21.071,3.303 20.884,3.116C20.697,2.929 20.442,2.863 20.095,2.817C19.575,2.747 18.9,2.75 18,2.75H11Z",
        "M11,6.75C10.586,6.75 10.25,6.414 10.25,6C10.25,5.586 10.586,5.25 11,5.25H18C18.414,5.25 18.75,5.586 18.75,6C18.75,6.414 18.414,6.75 18,6.75H11Z",
        "M6,6.75C5.586,6.75 5.25,6.414 5.25,6C5.25,5.586 5.586,5.25 6,5.25H8C8.414,5.25 8.75,5.586 8.75,6C8.75,6.414 8.414,6.75 8,6.75H6Z",
        "M11,17.75C10.586,17.75 10.25,17.414 10.25,17C10.25,16.586 10.586,16.25 11,16.25H18C18.414,16.25 18.75,16.586 18.75,17C18.75,17.414 18.414,17.75 18,17.75H11Z",
        "M6,17.75C5.586,17.75 5.25,17.414 5.25,17C5.25,16.586 5.586,16.25 6,16.25H8C8.414,16.25 8.75,16.586 8.75,17C8.75,17.414 8.414,17.75 8,17.75H6Z",
    )
    ImageVector.Builder(
        name = "Relays",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { addPath(pathData = addPathNodes(it), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black)) }
    }.build()
}
