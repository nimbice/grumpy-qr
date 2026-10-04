package io.github.nimbice.grumpyqr.ui

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nimbice.grumpyqr.BuildConfig
import io.github.nimbice.grumpyqr.R
import io.github.nimbice.grumpyqr.scan.Decoder
import io.github.nimbice.grumpyqr.scan.RepeatSuppressor
import io.github.nimbice.grumpyqr.scan.Scan
import io.github.nimbice.grumpyqr.scan.toScans
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live camera preview that decodes every frame it can keep up with. Pinch to
 * zoom and tap to focus come built in with CameraX's controller.
 */
@Composable
fun CameraPreview(
    paused: Boolean,
    torchOn: Boolean,
    suppressor: RepeatSuppressor,
    onScanned: (List<Scan>) -> Unit,
    onTorchUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnTorchUnavailable by rememberUpdatedState(onTorchUnavailable)
    val pausedFlag = remember { AtomicBoolean(paused) }

    LaunchedEffect(paused) { pausedFlag.set(paused) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val reader = try {
            Decoder.forCamera()
        } catch (_: UnsatisfiedLinkError) {
            null
        }

        controller.setImageAnalysisAnalyzer(executor) { image ->
            image.use { frame ->
                if (reader == null || pausedFlag.get()) return@use
                val scans = try {
                    reader.read(frame).toScans()
                } catch (e: RuntimeException) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Decoding a camera frame failed", e)
                    emptyList()
                }
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "${frame.width}x${frame.height} rot=${frame.imageInfo.rotationDegrees} -> ${scans.size} code(s)")
                }
                val fresh = suppressor.filter(scans)
                if (fresh.isNotEmpty() && pausedFlag.compareAndSet(false, true)) {
                    suppressor.suppress(fresh)
                    mainExecutor.execute { currentOnScanned(fresh) }
                }
            }
        }
        controller.bindToLifecycle(lifecycleOwner)
        controller.initializationFuture.addListener({
            // Some tablets and Chromebooks only have a front camera.
            runCatching {
                if (!controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
                    controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                ) {
                    controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                }
            }
        }, mainExecutor)

        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor.shutdown()
        }
    }

    LaunchedEffect(torchOn) {
        if (torchOn && controller.cameraInfo?.hasFlashUnit() != true) {
            currentOnTorchUnavailable()
        } else {
            runCatching { controller.enableTorch(torchOn) }
        }
    }

    val description = stringResource(R.string.camera_preview_description)
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier.semantics { contentDescription = description },
    )
}

private const val TAG = "GrumpyCamera"
