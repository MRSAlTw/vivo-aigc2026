package com.aicamera.app.feature.exploration.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.ScaleGestureDetector
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aicamera.app.feature.exploration.state.CameraMode
import com.aicamera.app.feature.exploration.state.ExplorationUiEvent
import com.aicamera.app.feature.exploration.state.ExplorationUiState
import com.aicamera.app.feature.exploration.viewmodel.ExplorationViewModel

/**
 * Viewfinder preview Route (called by NavGraph).
 * 负责权限申请，并将 ViewModel 传入无状态的 Screen。
 */
@Composable
fun ExplorationRoute(
    onNavigateToComposition: () -> Unit = {},
    onNavigateToPose: () -> Unit = {},
    viewModel: ExplorationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current

    // 进入相机界面 → 隐藏状态栏（沉浸式拍照体验）
    DisposableEffect(Unit) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(WindowInsets.Type.statusBars())
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.statusBars())
                window.setDecorFitsSystemWindows(true)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    // 一次性事件处理
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExplorationUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is ExplorationUiEvent.ShutterTriggered -> { }
                else -> {}
            }
        }
    }

    // 相机权限申请
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onPermissionsGranted()
        } else {
            Toast.makeText(context, "需要相机权限才能拍照", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            viewModel.onPermissionsGranted()
        }
    }

    ExplorationScreen(
        state = state,
        viewModel = viewModel,
        onShutterClick = viewModel::onShutterClick,
        onFlipCamera = viewModel::onFlipCamera,
        onZoomChange = viewModel::onZoomChange,
        onModeChange = viewModel::onModeChange,
    )
}

/**
 * 现代相机风格拍照界面 — 参考第三方相机 App 设计。
 *
 * 布局层级（从底到顶）：
 *   1. Camera Preview（全屏）
 *   2. 寻景叠加层（九宫格 + 方向箭头）
 *   3. 顶部渐变 + 内容
 *   4. 底部渐变 + 控制栏
 */
@Composable
fun ExplorationScreen(
    state: ExplorationUiState,
    viewModel: ExplorationViewModel,
    onShutterClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onModeChange: (CameraMode) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── 变焦基准倍率（在 View 层回调与 Compose 层之间共享） ──
    class ZoomRef(var v: Float)
    val zoomBaseRef = remember { ZoomRef(1f) }
    val currentZoomRef = remember { ZoomRef(1f) }
    LaunchedEffect(state.currentZoomRatio) {
        currentZoomRef.v = state.currentZoomRatio
    }

    // ── 拍照画面飞入缩略图动画 ──
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val capAnimProgress = remember { Animatable(0f) }
    var thumbnailRect by remember { mutableStateOf(Rect.Zero) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        // 用 immediateAnimBitmap：按下快门立即触发（使用预览帧），不等照片保存
        viewModel.immediateAnimBitmap.collect { bitmap ->
            if (bitmap != null) {
                capturedBitmap = bitmap
                capAnimProgress.snapTo(0f)
                capAnimProgress.animateTo(
                    1f,
                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                )
                capturedBitmap = null
                viewModel.onCaptureAnimDone()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { screenSize = it }
    ) {
        // ================================================================
        //  1. Camera Preview + 双指变焦
        // ================================================================
        if (state.hasCameraPermission) {

            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        viewModel.bindCameraToPreview(this, lifecycleOwner)

                        // 双指变焦 — 使用 Android 原生 ScaleGestureDetector
                        // scaleFactor 是事件之间的增量，因此需要累积
                        val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                                zoomBaseRef.v = currentZoomRef.v
                                return true
                            }
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                zoomBaseRef.v *= detector.scaleFactor  // 累积增量
                                val newZoom = zoomBaseRef.v.coerceIn(
                                    state.minZoomRatio.coerceAtMost(1.0f),
                                    state.maxZoomRatio.coerceAtLeast(8.0f)
                                )
                                onZoomChange(newZoom)
                                return true
                            }
                        }
                        val scaleDetector = ScaleGestureDetector(ctx, scaleListener)
                        setOnTouchListener { _, event ->
                            scaleDetector.onTouchEvent(event)
                            true // 消耗事件，确保 ScaleGestureDetector 完整接收手势流
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "需要相机权限",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // ================================================================
        //  2. 寻景叠加层
        // ================================================================
        AnimatedVisibility(
            visible = state.isExploring,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            com.aicamera.app.core.ui.components.GuidanceOverlay(
                showGrid = true,
                arrowAngle = state.guidanceAngle,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ================================================================
        //  3. 顶部渐变（无文字）
        // ================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xBB000000),
                            Color(0x44000000),
                            Color.Transparent
                        )
                    )
                )
                .padding(top = 8.dp)
        ) { }

        // ================================================================
        //  4. 底部渐变 + 控制栏
        // ================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x44000000),
                            Color(0xBB000000),
                            Color(0xDD000000)
                        )
                    )
                )
        ) {
            // ── 模式选择器 ──
            ModeSelector(
                currentMode = state.currentMode,
                onModeChange = onModeChange,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            // ── 缩放倍率显示 ──
            ZoomIndicator(
                zoomRatio = state.currentZoomRatio,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // ── 底部控制栏 ──
            BottomControls(
                lastPhotoUri = state.lastPhotoUri,
                isCapturing = state.isCapturing,
                hasPermission = state.hasCameraPermission,
                isFrontCamera = state.isFrontCamera,
                onShutterClick = onShutterClick,
                onFlipCamera = onFlipCamera,
                onThumbnailPositioned = { pos, size ->
                    thumbnailRect = Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
                },
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 16.dp
                )
            )
        }

        // ── 拍照画面缩放飞入缩略图动画 ──
        if (capturedBitmap != null && screenSize != IntSize.Zero) {
            val sw = screenSize.width.toFloat()
            val sh = screenSize.height.toFloat()
            val center = Offset(sw / 2f, sh / 2f)
            val thumbCenter = Offset(
                thumbnailRect.left + thumbnailRect.width / 2f,
                thumbnailRect.top + thumbnailRect.height / 2f
            )
            val targetScale = if (sw > 0f) thumbnailRect.width / sw else 0.12f

            val progress = capAnimProgress.value
            // 手动 lerp：1.15f → targetScale
            val scale = 1.15f + (targetScale - 1.15f) * progress
            // 手动 lerp：Offset.Zero → 目标位移
            val dx = (thumbCenter.x - center.x) * progress
            val dy = (thumbCenter.y - center.y) * progress

            androidx.compose.foundation.Image(
                bitmap = capturedBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = dx
                        translationY = dy
                        alpha = if (progress < 0.85f) 1f else 1f - (progress - 0.85f) / 0.15f
                    },
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ====================================================================
//  子组件
// ====================================================================

/**
 * 模式选择器：Photo / Video / Portrait / More
 */
@Composable
private fun ModeSelector(
    currentMode: CameraMode,
    onModeChange: (CameraMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraMode.entries.forEachIndexed { index, mode ->
            if (index > 0) Spacer(modifier = Modifier.width(4.dp))

            val isActive = mode == currentMode

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isActive) Color(0xCCFFFFFF) else Color(0x44000000)
                    )
                    .clickable { onModeChange(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${mode.emoji} ${mode.label}",
                    color = if (isActive) Color(0xFF000000) else Color(0xAAFFFFFF),
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * 缩放倍率指示器（显示在快门上方的"1.0x"）
 */
@Composable
private fun ZoomIndicator(
    zoomRatio: Float,
    modifier: Modifier = Modifier,
) {
    val displayText = String.format("%.1fx", zoomRatio)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

/**
 * 底部控制栏：左=缩略图 → 系统相册/相机 | 中=快门 | 右=翻转
 */
@Composable
private fun BottomControls(
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
        // 左：最近照片缩略图 → 点击直接打开系统相册（无选择弹窗）
        PhotoThumbnail(
            uri = lastPhotoUri,
            onClick = {
                openGalleryDirectly(context)
            },
            onPositioned = onThumbnailPositioned
        )

        // 中：快门按钮
        ShutterButton(
            enabled = !isCapturing && hasPermission,
            onClick = onShutterClick
        )

        // 右：翻转镜头按钮
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

/** 直接打开系统相册（无选择弹窗） */
private fun openGalleryDirectly(context: android.content.Context) {
    // 方案一：用 CATEGORY_APP_GALLERY 让系统选择默认相册（Android 4.0.3+）
    try {
        val intent = Intent.makeMainSelectorActivity(
            Intent.ACTION_MAIN,
            Intent.CATEGORY_APP_GALLERY
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) {
        // 某些 ROM 不支持 CATEGORY_APP_GALLERY，走降级
    }

    // 方案二：手动找相册 App（过滤掉非相册应用）
    try {
        val baseIntent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val activities = context.packageManager.queryIntentActivities(baseIntent, 0)
        // 优先匹配包名含 gallery/photo/album 的应用
        val galleryApp = activities.firstOrNull { act ->
            val pkg = act.activityInfo.packageName.lowercase()
            pkg.contains("gallery") || pkg.contains("photo") ||
            pkg.contains("album") || pkg.contains("相册") ||
            !pkg.contains("file")   // 排除文件管理器
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

/**
 * 最近照片缩略图 — 点击跳转系统应用
 * 新照片到达时附带脉冲动画
 */
@Composable
private fun PhotoThumbnail(
    uri: Uri?,
    onClick: () -> Unit,
    onPositioned: ((Offset, IntSize) -> Unit)? = null,
) {
    val context = LocalContext.current
    val animScale = remember { Animatable(1f) }

    // 新照片到达 → 脉冲放大回弹
    LaunchedEffect(uri) {
        if (uri != null) {
            animScale.snapTo(1.25f)
            animScale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer(scaleX = animScale.value, scaleY = animScale.value)
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
                    context.contentResolver.loadThumbnail(uri, Size(200, 200), null)
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
        // 占位图标
        Text(text = "📸", fontSize = 20.sp)
    }
}

/**
 * 快门按钮（大圆白底 + 内圆）
 */
@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
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
