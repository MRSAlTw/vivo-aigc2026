package com.aicamera.app.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aicamera.app.core.ui.components.CameraPreview
import com.aicamera.app.feature.camera.ui.BottomControls
import com.aicamera.app.feature.camera.viewmodel.CameraViewModel
import com.aicamera.app.feature.composition.ui.CompositionScreen
import com.aicamera.app.feature.composition.viewmodel.CompositionViewModel
import com.aicamera.app.feature.exploration.ui.ExplorationTab
import com.aicamera.app.feature.exploration.viewmodel.ExplorationViewModel
import com.aicamera.app.feature.pose.ui.PoseTab
import com.aicamera.app.feature.pose.viewmodel.PoseViewModel

/**
 * Tab definitions — mirrors the old CameraMode enum visually.
 */
private data class ModeTab(val label: String, val emoji: String)
private val modeTabs = listOf(
    ModeTab("标准", "📷"),
    ModeTab("寻景", "🔍"),
    ModeTab("构图", "⊞"),
    ModeTab("姿势", "🧑"),
)

/**
 * Main camera screen.
 *
 * ═══ Visual layout (identical to before) ═══
 *
 *   ┌──────────────────────────────────┐
 *   │    Camera Preview (full screen)   │
 *   │    + Tab overlay:                 │
 *   │      STANDARD → (empty)           │
 *   │      EXPLORE  → GuidanceOverlay   │
 *   │      COMPOSITION→ Composition UI  │
 *   │      POSE     → Pose UI          │
 *   ├──────────────────────────────────┤
 *   │    [top gradient]                │
 *   │    ModeSelector (📷🔍⊞🧑)        │
 *   │    ZoomIndicator (1.0x)          │
 *   │    BottomControls (📸 🔴 🔄)     │
 *   │    [bottom gradient]             │
 *   └──────────────────────────────────┘
 *
 * ═══ Architecture ═══
 *   - CameraPreview (shared, persistent)
 *   - Each mode has its own ViewModel + Composable
 *   - Bottom controls (shutter/zoom/flip) driven by CameraViewModel
 *   - Capture animation at MainScreen level
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── ViewModels ──
    val cameraViewModel: CameraViewModel = hiltViewModel()
    val cameraState by cameraViewModel.uiState.collectAsState()

    val explorationViewModel: ExplorationViewModel = hiltViewModel()
    val explorationState by explorationViewModel.uiState.collectAsState()

    val compositionViewModel: CompositionViewModel = hiltViewModel()
    val compositionState by compositionViewModel.uiState.collectAsState()

    val poseViewModel: PoseViewModel = hiltViewModel()
    val poseState by poseViewModel.uiState.collectAsState()

    // ── 当前模式 (0=标准, 1=寻景, 2=构图, 3=姿势) ──
    var selectedMode by remember { mutableIntStateOf(0) }

    // ── 构图模式激活/停用：挂载/卸载 FaceDetectionAnalyzer ──
    LaunchedEffect(selectedMode) {
        compositionViewModel.setActive(selectedMode == 2)
    }

    // ── 进入相机界面 → 隐藏状态栏（沉浸式拍照体验） ──
    val activity = LocalActivity.current
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

    // ── 拍照飞入动画 ──
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val capAnimProgress = remember { Animatable(0f) }
    var thumbnailRect by remember { mutableStateOf(Rect.Zero) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        cameraViewModel.immediateAnimBitmap.collect { bitmap ->
            if (bitmap != null) {
                capturedBitmap = bitmap
                capAnimProgress.snapTo(0f)
                capAnimProgress.animateTo(
                    1f,
                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                )
                capturedBitmap = null
                cameraViewModel.onCaptureAnimDone()
            }
        }
    }

    // ── 相机权限 ──
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraViewModel.onPermissionsGranted()
        } else {
            Toast.makeText(context, "需要相机权限才能拍照", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            cameraViewModel.onPermissionsGranted()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Main layout — exactly the same visual as before
    // ═══════════════════════════════════════════════════════
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { screenSize = it }
    ) {
        // ── Layer 1: Camera Preview (shared, persistent) ──
        if (cameraState.hasCameraPermission) {
            CameraPreview(
                cameraController = cameraViewModel.cameraController,
                lifecycleOwner = lifecycleOwner,
                currentZoomRatio = cameraState.currentZoomRatio,
                minZoomRatio = cameraState.minZoomRatio,
                maxZoomRatio = cameraState.maxZoomRatio,
                onZoomChange = cameraViewModel::onZoomChange,
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

        // ── Layer 2: Tab content (overlaid on camera preview) ──
        when (selectedMode) {
            0 -> { /* Standard — 无叠加层 */ }
            1 -> ExplorationTab(
                state = explorationState,
                onStartExplore = explorationViewModel::onStartExplore,
                onStopExplore = explorationViewModel::onStopExplore,
            )
            2 -> CompositionScreen(
                state = compositionState,
                onSelectMode = { mode ->
                    compositionViewModel.onCompositionChange(mode)
                    compositionViewModel.selectMode(mode)
                },
            )
            3 -> PoseTab(state = poseState)
        }

        // ── Layer 3: Top gradient ──
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

        // ── Layer 4: Bottom gradient + controls ──
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
            // ── Mode selector (same visual as old) ──
            ModeSelector(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            // ── Zoom indicator ──
            ZoomIndicator(
                zoomRatio = cameraState.currentZoomRatio,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // ── Bottom controls: thumbnail | shutter | flip ──
            BottomControls(
                lastPhotoUri = cameraState.lastPhotoUri,
                isCapturing = cameraState.isCapturing,
                hasPermission = cameraState.hasCameraPermission,
                isFrontCamera = cameraState.isFrontCamera,
                onShutterClick = cameraViewModel::onShutterClick,
                onFlipCamera = cameraViewModel::onFlipCamera,
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

        // ── Layer 5: Capture animation overlay（独立 Composable，避免全屏重绘） ──
        CaptureAnimOverlay(
            capturedBitmap = capturedBitmap,
            animProgress = capAnimProgress.value,
            screenSize = screenSize,
            thumbnailRect = thumbnailRect,
        )
    }
}

// ====================================================================
//  Sub-components (identical to old visual)
// ====================================================================

/**
 * Mode selector row — EXACT same visual as the old CameraMode selector.
 */
@Composable
private fun ModeSelector(
    selectedMode: Int,
    onModeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        modeTabs.forEachIndexed { index, mode ->
            if (index > 0) Spacer(modifier = Modifier.width(4.dp))

            val isActive = index == selectedMode

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isActive) Color(0xCCFFFFFF) else Color(0x44000000)
                    )
                    .clickable { onModeSelected(index) }
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
 * Zoom ratio indicator.
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
 * Capture animation overlay — 独立 Composable，避免动画每帧触发 MainScreen 全屏重绘。
 *
 * 优化要点：
 * 1. 仅此 Composable 在动画期间重组，不影响 CameraPreview / Tab / 底部控制
 * 2. asImageBitmap() 用 remember 缓存，避免每帧重新上传 GPU 纹理
 */
@Composable
private fun CaptureAnimOverlay(
    capturedBitmap: Bitmap?,
    animProgress: Float,
    screenSize: IntSize,
    thumbnailRect: Rect,
) {
    if (capturedBitmap == null || screenSize == IntSize.Zero) return

    // 关键优化：Bitmap→ImageBitmap 只转换一次，缓存到 capturedBitmap 变化
    val imageBitmap = remember(capturedBitmap) { capturedBitmap.asImageBitmap() }

    val sw = screenSize.width.toFloat()
    val sh = screenSize.height.toFloat()
    val center = Offset(sw / 2f, sh / 2f)
    val thumbCenter = Offset(
        thumbnailRect.left + thumbnailRect.width / 2f,
        thumbnailRect.top + thumbnailRect.height / 2f
    )
    val targetScale = if (sw > 0f) thumbnailRect.width / sw else 0.12f

    val scale = 1.15f + (targetScale - 1.15f) * animProgress
    val dx = (thumbCenter.x - center.x) * animProgress
    val dy = (thumbCenter.y - center.y) * animProgress
    val alpha = if (animProgress < 0.85f) 1f else 1f - (animProgress - 0.85f) / 0.15f

    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = dx
                translationY = dy
                this.alpha = alpha
            },
        contentScale = ContentScale.Crop
    )
}
