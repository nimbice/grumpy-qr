package io.github.nimbice.grumpyqr

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig

class GrumpyApp : Application(), CameraXConfig.Provider {

    /**
     * Only set up the camera we actually use. CameraX otherwise checks every
     * camera the phone claims to have, and on phones that misreport their
     * cameras that check can stall startup for seconds. Devices without a
     * rear camera (some tablets and Chromebooks) keep the default so the
     * front camera can still be used.
     */
    // Deliberately asks about the *rear* camera: everything else falls back to "any camera".
    @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
    override fun getCameraXConfig(): CameraXConfig {
        val builder = CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            builder.setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
        }
        return builder.build()
    }
}
