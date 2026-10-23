package com.vknxworks.textctrl.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.sms.MmsPart
import com.vknxworks.textctrl.ui.components.runTask
import com.vknxworks.textctrl.ui.components.saveToPhone
import com.vknxworks.textctrl.ui.components.shareAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picture from a message, full screen: pinch to zoom, double-tap to zoom in or out, save or share. */
@Composable
fun ImageViewer(part: MmsPart, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = part.uri,
                contentDescription = part.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
                    .transformable(transform)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { shareAttachment(context, part) }) { Icon(Icons.Outlined.Share, stringResource(R.string.share), tint = Color.White) }
                IconButton(onClick = {
                    c.runTask {
                        val ok = withContext(Dispatchers.IO) { saveToPhone(context, part) }
                        Toast.makeText(context, context.getString(if (ok) R.string.saved_to_phone else R.string.save_failed), Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Outlined.SaveAlt, stringResource(R.string.save_to_phone), tint = Color.White) }
            }
        }
    }
}
