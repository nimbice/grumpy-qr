package io.github.nimbice.grumpyqr.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.nimbice.grumpyqr.Actions
import io.github.nimbice.grumpyqr.AppViewModel
import io.github.nimbice.grumpyqr.LaunchMode
import io.github.nimbice.grumpyqr.R
import io.github.nimbice.grumpyqr.Screen
import io.github.nimbice.grumpyqr.ui.theme.BrandInk
import io.github.nimbice.grumpyqr.ui.theme.BrandMustard

private val OverlayScrim = Color.Black.copy(alpha = 0.55f)

@Composable
fun ScannerScreen(viewModel: AppViewModel, snackbar: SnackbarHostState, callerName: String?) {
    val context = LocalContext.current
    val view = LocalView.current
    val settings = viewModel.settings
    val deviceHasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        settings.cameraDeclined = !granted
        val activity = context.findActivity()
        permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }

    // The permission can change behind our back (system settings, "only this time").
    LifecycleResumeEffect(Unit) {
        hasPermission = context.hasCameraPermission()
        if (hasPermission) settings.cameraDeclined = false
        onPauseOrDispose { }
    }

    // Ask for the camera once, when you first open the scanner. Say no and we
    // won't ask again on our own: the button below is there if you change your mind.
    LaunchedEffect(Unit) {
        if (deviceHasCamera && !hasPermission && !settings.cameraDeclined &&
            !viewModel.askedForCamera && !viewModel.pickImageOnStart
        ) {
            viewModel.askedForCamera = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::scanPickedFile)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::scanPickedFile)
    }
    val pickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val pickFile = { filePicker.launch(arrayOf("image/*", "application/pdf")) }

    LaunchedEffect(viewModel.pickImageOnStart) {
        if (viewModel.pickImageOnStart) {
            viewModel.pickImageOnStart = false
            pickPhoto()
        }
    }

    val scanningForCaller = viewModel.mode == LaunchMode.SCAN_FOR_RESULT

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomTools(
                onPhotos = pickPhoto,
                onFiles = pickFile,
                onHistory = { viewModel.screen = Screen.HISTORY }.takeUnless { scanningForCaller },
                onSettings = { viewModel.screen = Screen.SETTINGS }.takeUnless { scanningForCaller },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // The camera runs edge to edge, under the bars...
            if (deviceHasCamera && hasPermission) {
                CameraPreview(
                    paused = viewModel.results != null || viewModel.busy,
                    torchOn = torchOn,
                    suppressor = viewModel.cameraSuppressor,
                    onScanned = { scans ->
                        if (settings.vibrate) {
                            view.performHapticFeedback(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    HapticFeedbackConstants.CONFIRM
                                } else {
                                    HapticFeedbackConstants.LONG_PRESS
                                },
                            )
                        }
                        viewModel.onScanned(scans)
                    },
                    onTorchUnavailable = {
                        torchOn = false
                        viewModel.showMessage(R.string.message_no_flash)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ...while everything else stays clear of them.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    !deviceHasCamera -> CameraMessage(
                        title = stringResource(R.string.no_camera_title),
                        body = stringResource(R.string.no_camera_body),
                        primaryLabel = stringResource(R.string.action_scan_picture),
                        onPrimary = pickPhoto,
                    )
                    hasPermission -> Viewfinder()
                    else -> CameraMessage(
                        title = stringResource(R.string.camera_off_title),
                        body = stringResource(R.string.camera_off_body),
                        primaryLabel = stringResource(
                            if (permanentlyDenied) R.string.action_open_settings else R.string.action_turn_on_camera,
                        ),
                        onPrimary = {
                            if (permanentlyDenied) {
                                Actions.openAppSettings(context)
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        secondaryLabel = stringResource(R.string.action_scan_picture),
                        onSecondary = pickPhoto,
                    )
                }

                if (viewModel.busy) {
                    Surface(
                        color = OverlayScrim,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BrandMustard)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.looking_for_codes), color = Color.White)
                        }
                    }
                }
            }

            TopBar(
                callerName = callerName,
                showTorch = hasPermission && deviceHasCamera,
                torchOn = torchOn,
                onToggleTorch = { torchOn = !torchOn },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun TopBar(
    callerName: String?,
    showTorch: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = OverlayScrim, shape = RoundedCornerShape(50)) {
            Text(
                text = callerName?.let { stringResource(R.string.scanning_for, it) } ?: stringResource(R.string.app_name),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (showTorch) {
            IconButton(
                onClick = onToggleTorch,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (torchOn) BrandMustard else OverlayScrim,
                    contentColor = if (torchOn) Color.Black else Color.White,
                ),
            ) {
                Icon(
                    imageVector = if (torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    contentDescription = stringResource(if (torchOn) R.string.torch_off else R.string.torch_on),
                )
            }
        }
    }
}

@Composable
private fun BottomTools(
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onHistory: (() -> Unit)?,
    onSettings: (() -> Unit)?,
) {
    Surface(color = OverlayScrim, contentColor = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolButton(Icons.Outlined.PhotoLibrary, stringResource(R.string.tool_photos), onPhotos)
            ToolButton(Icons.Outlined.FolderOpen, stringResource(R.string.tool_files), onFiles)
            onHistory?.let { ToolButton(Icons.Outlined.History, stringResource(R.string.tool_history), it) }
            onSettings?.let { ToolButton(Icons.Outlined.Settings, stringResource(R.string.tool_settings), it) }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.widthIn(min = 72.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Corner brackets showing where to aim. The whole frame is scanned, this is just a guide. */
@Composable
private fun Viewfinder() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = minOf(maxWidth, maxHeight) * 0.66f
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(side)) {
                val stroke = 4.dp.toPx()
                val arm = 32.dp.toPx()
                val inset = stroke / 2
                val w = size.width
                val h = size.height
                val corners = listOf(
                    Triple(Offset(inset, inset), 1f, 1f),
                    Triple(Offset(w - inset, inset), -1f, 1f),
                    Triple(Offset(inset, h - inset), 1f, -1f),
                    Triple(Offset(w - inset, h - inset), -1f, -1f),
                )
                for ((corner, dx, dy) in corners) {
                    drawLine(BrandMustard, corner, corner + Offset(arm * dx, 0f), stroke, StrokeCap.Round)
                    drawLine(BrandMustard, corner, corner + Offset(0f, arm * dy), stroke, StrokeCap.Round)
                }
            }
            Spacer(Modifier.height(20.dp))
            Surface(color = OverlayScrim, shape = RoundedCornerShape(50)) {
                Text(
                    text = stringResource(R.string.aim_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraMessage(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.NoPhotography,
            contentDescription = null,
            tint = BrandMustard,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        // This screen is always dark, whatever the theme, so use the brand colours directly.
        Button(
            onClick = onPrimary,
            colors = ButtonDefaults.buttonColors(containerColor = BrandMustard, contentColor = BrandInk),
        ) { Text(primaryLabel) }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSecondary) { Text(secondaryLabel, color = Color.White) }
        }
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
