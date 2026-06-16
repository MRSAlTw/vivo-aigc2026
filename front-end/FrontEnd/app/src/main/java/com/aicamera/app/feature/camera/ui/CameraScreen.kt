package com.aicamera.app.feature.camera.ui

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ====================================================================
//  Shared bottom control components (used by MainScreen)
// ====================================================================

/**
 * Bottom control row: thumbnail | shutter | flip
 *
 * Same visual as the old ExplorationScreen's BottomControls.
 */
@Composable
fun BottomControls(
    lastPhotoUri: Uri?,
    isCapturing: Boolean,
    hasPermission: Boolean,
    isFrontCamera: Boolean,
    onShutterClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onThumbnailPositioned: ((Offset, IntSize) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: photo thumbnail → open gallery
        PhotoThumbnail(
            uri = lastPhotoUri,
            onClick = { openGalleryDirectly(context) },
            onPositioned = onThumbnailPositioned
        )

        // Center: shutter button
        ShutterButton(
            enabled = !isCapturing && hasPermission,
            onClick = onShutterClick
        )

        // Right: flip camera
        IconButton(onClick = onFlipCamera) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x44000000)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFrontCamera) "🤳" else "📷",
                    fontSize = 22.sp
                )
            }
        }
    }
}

/**
 * Photo thumbnail — click opens gallery.
 */
@Composable
fun PhotoThumbnail(
    uri: Uri?,
    onClick: () -> Unit,
    onPositioned: ((Offset, IntSize) -> Unit)? = null,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x33000000))
            .clickable(onClick = onClick)
            .onGloballyPositioned { coords ->
                onPositioned?.invoke(coords.positionInRoot(), coords.size)
            },
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            val thumbnail = remember(uri) {
                try {
                    context.contentResolver
                        .loadThumbnail(uri, Size(200, 200), null)
                        ?.let { it.asImageBitmap() }
                } catch (e: Exception) { null }
            }
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail,
                    contentDescription = "最近照片",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                return
            }
        }
        Text(text = "📸", fontSize = 20.sp)
    }
}

/**
 * Shutter button (large white circle).
 */
@Composable
fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color(0x88FFFFFF))
        )
    }
}

/** Open system gallery directly (same behavior as old code). */
private fun openGalleryDirectly(context: android.content.Context) {
    // Fallback A: CATEGORY_APP_GALLERY
    try {
        val intent = Intent.makeMainSelectorActivity(
            Intent.ACTION_MAIN,
            Intent.CATEGORY_APP_GALLERY
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) { }

    // Fallback B: find gallery app manually
    try {
        val baseIntent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val activities = context.packageManager.queryIntentActivities(baseIntent, 0)
        val galleryApp = activities.firstOrNull { act ->
            val pkg = act.activityInfo.packageName.lowercase()
            pkg.contains("gallery") || pkg.contains("photo") ||
            pkg.contains("album") || pkg.contains("相册") ||
            !pkg.contains("file")
        } ?: activities.firstOrNull()

        if (galleryApp != null) {
            val intent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                setPackage(galleryApp.activityInfo.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    } catch (_: Exception) { }
}
