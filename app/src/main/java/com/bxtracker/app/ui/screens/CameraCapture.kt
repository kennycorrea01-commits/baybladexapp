package com.bxtracker.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bxtracker.app.R
import com.bxtracker.app.data.PartKind
import com.bxtracker.app.ui.components.FitText
import com.bxtracker.app.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

// The camera stays dark in both themes, so its colors are fixed.
private val CamBg = Color.Black
private val CamText = Color(0xFFF3F7FB)
private val CamMuted = Color(0xFFA9B8C7)
private val CamAccent = Color(0xFF179CD8)

/**
 * The strip above the shutter. Game terms stay in English; the null label is the translated "CX part"
 * chip, which maps to a lock chip unless a CX kind is already chosen.
 */
private val stripKinds: List<Pair<String?, PartKind>> =
    listOf("Blade" to PartKind.BLADE, "Ratchet" to PartKind.RATCHET, "Bit" to PartKind.BIT, null to PartKind.LOCK_CHIP)

private const val GUIDE_FRACTION = 0.74f

private suspend fun cameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        runCatching { future.get() }.onSuccess { cont.resume(it) }.onFailure { cont.resumeWithException(it) }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Full-screen in-app camera with a round guide. The saved photo is cropped to a square around the guide
 * and shrunk to [maxSide]. If the camera can't be used, offers the system camera and the gallery instead.
 */
@Composable
fun CameraCapture(
    kind: PartKind?,
    onKindChange: ((PartKind) -> Unit)?,
    maxSide: Int,
    onPhoto: (String) -> Unit,
    onGallery: () -> Unit,
    onSystemCamera: () -> Unit,
    onSkip: () -> Unit,
) {
    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = CamBg, contentColor = CamText) {
            CameraContent(kind, onKindChange, maxSide, onPhoto, onGallery, onSystemCamera, onSkip)
        }
    }
}

@Composable
private fun CameraContent(
    kind: PartKind?,
    onKindChange: ((PartKind) -> Unit)?,
    maxSide: Int,
    onPhoto: (String) -> Unit,
    onGallery: () -> Unit,
    onSystemCamera: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var error by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    LaunchedEffect(flash) { imageCapture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }

    if (granted) {
        DisposableEffect(lifecycleOwner) {
            var provider: ProcessCameraProvider? = null
            val job = scope.launch {
                runCatching {
                    val p = cameraProvider(context).also { provider = it }
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    p.unbindAll()
                    p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }.onFailure { error = context.getString(R.string.camera_error_start, it.message ?: it.javaClass.simpleName) }
            }
            onDispose {
                job.cancel()
                provider?.unbindAll()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val guide = min(viewW, viewH) * GUIDE_FRACTION

        if (granted && error == null) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            // Dim everything outside the round guide.
            Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                drawRect(Color.Black.copy(alpha = 0.55f))
                drawCircle(Color.Transparent, radius = guide / 2, center = center, blendMode = BlendMode.Clear)
                drawCircle(CamAccent, radius = guide / 2, center = center, style = Stroke(width = 3.dp.toPx()))
                val tick = 10.dp.toPx()
                listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f)).forEach { d ->
                    val from = center + d * (guide / 2 - tick)
                    drawLine(CamAccent, from, from + d * tick, strokeWidth = 2.dp.toPx())
                }
            }
        } else {
            CameraProblem(
                text = error ?: stringResource(if (asked) R.string.camera_access_off else R.string.camera_waiting_access),
                showAllow = error == null && asked,
                onAllow = { permission.launch(Manifest.permission.CAMERA) },
                onSystemCamera = onSystemCamera.takeIf { error != null },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Top: close, hint, flash.
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSkip) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.camera_close), tint = CamText) }
                Spacer(Modifier.weight(1f))
                if (granted && error == null) {
                    IconButton(onClick = { flash = !flash }) {
                        Icon(
                            if (flash) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                            contentDescription = stringResource(if (flash) R.string.camera_flash_on else R.string.camera_flash_off),
                            tint = if (flash) CamAccent else CamText,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.camera_hint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = CamText, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
        }

        // Bottom: kind strip, then gallery, shutter, skip.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onKindChange != null) {
                val cxPart = stringResource(R.string.camera_cx_part)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 18.dp)) {
                    stripKinds.forEach { (label, k) ->
                        val selected = if (k == PartKind.LOCK_CHIP) kind?.isCx == true else kind == k
                        Surface(
                            onClick = { onKindChange(if (k == PartKind.LOCK_CHIP && kind?.isCx == true) kind else k) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) CamAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                            contentColor = if (selected) CamText else CamMuted,
                            border = BorderStroke(1.dp, if (selected) CamAccent else Color.White.copy(alpha = 0.2f)),
                        ) {
                            Text(label ?: cxPart, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    // "Upload image": the icon button plus a visible label, so it doesn't read as a thumbnail.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onGallery,
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                        ) { Icon(Icons.Outlined.PhotoLibrary, contentDescription = stringResource(R.string.camera_pick_gallery), tint = CamText) }
                        Spacer(Modifier.height(4.dp))
                        FitText(
                            stringResource(R.string.parts_upload_image),
                            // The button above already announces it.
                            modifier = Modifier.clearAndSetSemantics {},
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CamText,
                        )
                    }
                }
                val canShoot = granted && error == null && !saving
                Box(
                    Modifier.size(80.dp).clip(CircleShape).border(4.dp, if (canShoot) CamText else CamMuted, CircleShape).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = {
                            saving = true
                            val file = PhotoStore.newFile(context)
                            imageCapture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { PhotoStore.cropToGuide(file, viewW, viewH, guide, maxSide) }
                                            saving = false
                                            onPhoto(file.absolutePath)
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        saving = false
                                        PhotoStore.delete(file.absolutePath)
                                        error = context.getString(R.string.camera_error_capture, exception.message ?: exception.javaClass.simpleName)
                                    }
                                },
                            )
                        },
                        enabled = canShoot,
                        shape = CircleShape,
                        color = if (canShoot) CamText else CamMuted.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (saving) Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), color = CamBg, strokeWidth = 3.dp) }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onSkip, modifier = Modifier.height(52.dp)) { Text(stringResource(R.string.camera_skip), color = CamText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
private fun CameraProblem(text: String, showAllow: Boolean, onAllow: () -> Unit, onSystemCamera: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = CamText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        if (showAllow) {
            Button(onClick = onAllow, colors = ButtonDefaults.buttonColors(containerColor = CamAccent, contentColor = Color(0xFF0B1E2B))) {
                Text(stringResource(R.string.camera_allow))
            }
        }
        if (onSystemCamera != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSystemCamera) { Text(stringResource(R.string.camera_use_system_app), color = CamAccent) }
        }
    }
}
