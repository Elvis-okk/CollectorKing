package com.collectorking.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import android.view.View
import android.view.WindowInsetsController
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.collectorking.app.databinding.ActivityMainBinding
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    // Compress quality: 10-100, read from WebView localStorage
    var compressQuality: Int = 70
    // Max photo dimension (pixels) for resize before compress
    companion object {
        private const val MAX_PHOTO_DIMENSION = 1920
    }

    // File upload callback for WebView's built-in file chooser
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // NativeBridge camera/album state
    private var pendingCameraDocType: String = ""
    private var pendingCameraPhotoIndex: Int = 0
    private var pendingCameraUri: Uri? = null

    private var pendingAlbumDocType: String = ""
    private var pendingAlbumPhotoIndex: Int = 0

    private var pendingAlbumMultiDocType: String = ""

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "需要权限才能使用此功能", Toast.LENGTH_SHORT).show()
        }
    }

    // WebView file chooser camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            cameraImageUri?.let { uri ->
                filePathCallback?.onReceiveValue(arrayOf(uri))
            } ?: filePathCallback?.onReceiveValue(null)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // WebView file chooser picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                filePathCallback?.onReceiveValue(arrayOf(uri))
            } else {
                filePathCallback?.onReceiveValue(null)
            }
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // NativeBridge: camera launcher for takePhoto
    private val nativeCameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingCameraUri?.let { uri ->
                val fileName = "photo_${System.currentTimeMillis()}_cam.jpg"
                val filePath = uriToFile(uri, fileName)
                callJsFunction("window.onCameraResult && window.onCameraResult('${escapeJs(pendingCameraDocType)}', ${pendingCameraPhotoIndex}, '$filePath')")
            } ?: run {
                callJsFunction("window.onCameraResult && window.onCameraResult('${escapeJs(pendingCameraDocType)}', ${pendingCameraPhotoIndex}, '')")
            }
        } else {
            callJsFunction("window.onCameraResult && window.onCameraResult('${escapeJs(pendingCameraDocType)}', ${pendingCameraPhotoIndex}, '')")
        }
        pendingCameraUri = null
    }

    // NativeBridge: single album pick launcher
    private val nativeAlbumLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { /* not all URIs support this */ }
                val fileName = "photo_${System.currentTimeMillis()}_album.jpg"
                val filePath = uriToFile(uri, fileName)
                if (filePath.isNotEmpty()) {
                    callJsFunction("window.onAlbumResult && window.onAlbumResult('${escapeJs(pendingAlbumDocType)}', ${pendingAlbumPhotoIndex}, '$filePath')")
                } else {
                    callJsFunction("window.onAlbumResult && window.onAlbumResult('${escapeJs(pendingAlbumDocType)}', ${pendingAlbumPhotoIndex}, '')")
                }
            } else {
                callJsFunction("window.onAlbumResult && window.onAlbumResult('${escapeJs(pendingAlbumDocType)}', ${pendingAlbumPhotoIndex}, '')")
            }
        } else {
            callJsFunction("window.onAlbumResult && window.onAlbumResult('${escapeJs(pendingAlbumDocType)}', ${pendingAlbumPhotoIndex}, '')")
        }
    }

    // NativeBridge: text file import launcher
    private var pendingImportCallback: String = ""
    private val textFileImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    val content = contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: ""
                    if (content.isNotEmpty() && pendingImportCallback.isNotEmpty()) {
                        val escaped = escapeJs(content)
                        callJsFunction("window.${pendingImportCallback} && window.${pendingImportCallback}('$escaped')")
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingImportCallback = ""
    }

    // NativeBridge: multi album pick launcher - SERIAL processing with progress
    private val nativeAlbumMultiLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val total = uris.size
            val docType = pendingAlbumMultiDocType
            Thread {
                uris.forEachIndexed { index, uri ->
                    try {
                        val fileName = "photo_${System.currentTimeMillis()}_$index.jpg"
                        val filePath = uriToFile(uri, fileName)
                        if (filePath.isNotEmpty()) {
                            runOnUiThread {
                                callJsFunction("window.onAlbumMultiProgress && window.onAlbumMultiProgress('${escapeJs(docType)}', '$filePath', ${index + 1}, $total)")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CollectorKing", "Multi photo process error", e)
                    }
                }
                runOnUiThread {
                    callJsFunction("window.onAlbumMultiComplete && window.onAlbumMultiComplete('${escapeJs(docType)}')")
                }
            }.start()
        } else {
            callJsFunction("window.onAlbumMultiComplete && window.onAlbumMultiComplete('${escapeJs(pendingAlbumMultiDocType)}')")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge()

        webView = binding.webView
        setupWebView()
        setupBackPressHandler()

        webView.loadUrl("file:///android_asset/web/index.html")
    }

    // === NativeBridge launch methods (called from NativeBridge class) ===

    fun launchCamera(docType: String, photoIndex: Int) {
        if (!hasPermissions()) {
            checkAndRequestPermissions()
            return
        }
        pendingCameraDocType = docType
        pendingCameraPhotoIndex = photoIndex

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile = createImageFile()
            if (photoFile != null) {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
                pendingCameraUri = uri
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                nativeCameraLauncher.launch(takePictureIntent)
            } else {
                Toast.makeText(this, "无法创建临时文件", Toast.LENGTH_SHORT).show()
                callJsFunction("window.onCameraResult && window.onCameraResult('${escapeJs(docType)}', $photoIndex, '')")
            }
        } else {
            Toast.makeText(this, "没有可用的相机应用", Toast.LENGTH_SHORT).show()
            callJsFunction("window.onCameraResult && window.onCameraResult('${escapeJs(docType)}', $photoIndex, '')")
        }
    }

    fun launchAlbum(docType: String, photoIndex: Int) {
        if (!hasPermissions()) {
            checkAndRequestPermissions()
            return
        }
        pendingAlbumDocType = docType
        pendingAlbumPhotoIndex = photoIndex

        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        // Ensure the intent resolves before launching
        if (intent.resolveActivity(packageManager) != null) {
            try {
                nativeAlbumLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开相册: ${e.message}", Toast.LENGTH_SHORT).show()
                callJsFunction("window.onAlbumResult && window.onAlbumResult('${escapeJs(docType)}', $photoIndex, '')")
            }
        } else {
            Toast.makeText(this, "没有可用的相册应用", Toast.LENGTH_SHORT).show()
            callJsFunction("window.onAlbumResult && window.onAlbumResult('${escapeJs(docType)}', $photoIndex, '')")
        }
    }

    fun launchTextFileImport(callbackName: String) {
        pendingImportCallback = callbackName
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/plain"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            textFileImportLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件选择器: ${e.message}", Toast.LENGTH_SHORT).show()
            pendingImportCallback = ""
        }
    }

    fun launchAlbumMulti(docType: String) {
        if (!hasPermissions()) {
            checkAndRequestPermissions()
            return
        }
        pendingAlbumMultiDocType = docType

        try {
            nativeAlbumMultiLauncher.launch("image/*")
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开相册: ${e.message}", Toast.LENGTH_SHORT).show()
            callJsFunction("window.onAlbumMultiResult && window.onAlbumMultiResult('${escapeJs(docType)}', [])")
        }
    }

    // === Helper methods ===

    private fun uriToBase64(uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                // Decode bitmap with sampling for large images
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)

                // Calculate inSampleSize to keep image within MAX_PHOTO_DIMENSION
                var sampleSize = 1
                if (options.outWidth > MAX_PHOTO_DIMENSION || options.outHeight > MAX_PHOTO_DIMENSION) {
                    val halfWidth = options.outWidth / 2
                    val halfHeight = options.outHeight / 2
                    while ((halfWidth / sampleSize) >= MAX_PHOTO_DIMENSION &&
                           (halfHeight / sampleSize) >= MAX_PHOTO_DIMENSION) {
                        sampleSize *= 2
                    }
                }

                // Decode the actual bitmap
                contentResolver.openInputStream(uri)?.use { input2 ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bitmap = BitmapFactory.decodeStream(input2, null, decodeOptions) ?: return@use ""

                    // Further resize if still too large
                    var resizedBitmap = bitmap
                    if (bitmap.width > MAX_PHOTO_DIMENSION || bitmap.height > MAX_PHOTO_DIMENSION) {
                        val scale = minOf(
                            MAX_PHOTO_DIMENSION.toFloat() / bitmap.width,
                            MAX_PHOTO_DIMENSION.toFloat() / bitmap.height
                        )
                        val newWidth = (bitmap.width * scale).toInt()
                        val newHeight = (bitmap.height * scale).toInt()
                        resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                        if (resizedBitmap !== bitmap) bitmap.recycle()
                    }

                    // Compress to JPEG with user-specified quality
                    val outputStream = ByteArrayOutputStream()
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, outputStream)
                    resizedBitmap.recycle()

                    Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                } ?: ""
            } ?: ""
        } catch (e: Exception) {
            android.util.Log.e("CollectorKing", "uriToBase64 error", e)
            ""
        }
    }

    // Save compressed photo directly to file (avoids base64 in memory)
    private fun uriToFile(uri: Uri, fileName: String): String {
        return try {
            val photosDir = File(getExternalFilesDir(null), "photos")
            if (!photosDir.exists()) photosDir.mkdirs()
            val outputFile = File(photosDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                // Decode bitmap with sampling for large images
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)

                // Calculate inSampleSize
                var sampleSize = 1
                if (options.outWidth > MAX_PHOTO_DIMENSION || options.outHeight > MAX_PHOTO_DIMENSION) {
                    val halfWidth = options.outWidth / 2
                    val halfHeight = options.outHeight / 2
                    while ((halfWidth / sampleSize) >= MAX_PHOTO_DIMENSION &&
                           (halfHeight / sampleSize) >= MAX_PHOTO_DIMENSION) {
                        sampleSize *= 2
                    }
                }

                // Decode the actual bitmap
                contentResolver.openInputStream(uri)?.use { input2 ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bitmap = BitmapFactory.decodeStream(input2, null, decodeOptions) ?: return ""

                    // Further resize if still too large
                    var resizedBitmap = bitmap
                    if (bitmap.width > MAX_PHOTO_DIMENSION || bitmap.height > MAX_PHOTO_DIMENSION) {
                        val scale = minOf(
                            MAX_PHOTO_DIMENSION.toFloat() / bitmap.width,
                            MAX_PHOTO_DIMENSION.toFloat() / bitmap.height
                        )
                        val newWidth = (bitmap.width * scale).toInt()
                        val newHeight = (bitmap.height * scale).toInt()
                        resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                        if (resizedBitmap !== bitmap) bitmap.recycle()
                    }

                    // Compress directly to file (no base64 in memory)
                    outputFile.outputStream().use { fos ->
                        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, fos)
                    }
                    resizedBitmap.recycle()
                    outputFile.absolutePath
                } ?: ""
            } ?: ""
        } catch (e: Exception) {
            android.util.Log.e("CollectorKing", "uriToFile error", e)
            ""
        }
    }

    // Create ZIP package from file paths (memory efficient - reads from disk)
    fun createPackageFromFiles(pkgName: String, filePathsJson: String, namingMode: String, packMode: String, timestampMode: String) {
        try {
            val photosMap = JSONObject(filePathsJson)
            val typeNames = photosMap.keys().asSequence().toList()
            val packagesDir = File(getExternalFilesDir(null), "packages")
            if (!packagesDir.exists()) packagesDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            // Determine zip file name based on timestampMode
            val zipFileName = if (timestampMode == "on") {
                "${pkgName}_${timeStamp}.zip"
            } else {
                "${pkgName}.zip"
            }
            var zipFile = File(packagesDir, zipFileName)
            // If no timestamp and file exists, auto-add timestamp
            if (timestampMode != "on" && zipFile.exists()) {
                zipFile = File(packagesDir, "${pkgName}_${timeStamp}.zip")
            }

            ZipOutputStream(zipFile.outputStream()).use { zos ->
                for (typeName in typeNames) {
                    val pathsArr = photosMap.getJSONArray(typeName)
                    for (i in 0 until pathsArr.length()) {
                        val filePath = pathsArr.getString(i)
                        val photoFile = File(filePath)
                        if (!photoFile.exists()) continue

                        val ext = photoFile.name.substringAfterLast('.', "jpg")
                        val fileName = when (namingMode) {
                            "sequence" -> "${typeName}_${String.format("%02d", i + 1)}.${ext}"
                            "timestamp" -> {
                                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                "${typeName}_${ts}_${i + 1}.${ext}"
                            }
                            else -> "${typeName}_${i + 1}.${ext}"
                        }

                        val entryName = if (packMode == "flat") "${pkgName}/${fileName}" else "${typeName}/${fileName}"
                        zos.putNextEntry(ZipEntry(entryName))
                        photoFile.inputStream().use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }

            // Share the file
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", zipFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, zipFile.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享打包文件"))

            // Save to history
            val historyId = "pack_${System.currentTimeMillis()}"
            val typeCount = typeNames.size
            var photoCount = 0
            for (i in 0 until photosMap.length()) {
                photoCount += photosMap.getJSONArray(typeNames[i]).length()
            }

            callJsFunction("window.onPackageCreated && window.onPackageCreated('${zipFile.absolutePath}', '', '$historyId', $typeCount, $photoCount)")
        } catch (e: Exception) {
            android.util.Log.e("CollectorKing", "createPackageFromFiles error", e)
            callJsFunction("window.onPackageCreated && window.onPackageCreated('', '${e.message?.replace("'", "\\'") ?: "unknown"}')")
        }
    }

    fun callJsFunction(js: String) {
        runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
    }

    fun escapeJs(s: String): String {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    }

    private fun setupEdgeToEdge() {
        // Enable edge-to-edge: transparent status bar and navigation bar
        // WebView handles safe-area insets via CSS env(safe-area-inset-*)
        // so we do NOT add padding to binding.root (that would cause double spacing)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Light status bar (dark text on transparent background)
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
            // Light navigation bar (dark buttons/gesture bar on transparent background)
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Light nav bar for API 26+ (deprecated but needed for pre-R)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                window.decorView.systemUiVisibility
                    or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            allowFileAccessFromFileURLs = true
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("file:///")) {
                    return false
                }
                // Open all external links in default browser
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "无法打开链接", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                if (!hasPermissions()) {
                    checkAndRequestPermissions()
                    return true
                }

                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent.resolveActivity(packageManager) != null) {
                    val photoFile = createImageFile()
                    photoFile?.let {
                        cameraImageUri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            it
                        )
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                    }
                }

                val contentIntent = Intent(Intent.ACTION_PICK).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                val chooserIntent = Intent.createChooser(contentIntent, "选择图片")
                if (cameraImageUri != null) {
                    chooserIntent.putExtra(
                        Intent.EXTRA_INITIAL_INTENTS,
                        arrayOf(takePictureIntent)
                    )
                }

                try {
                    filePickerLauncher.launch(chooserIntent)
                } catch (e: Exception) {
                    filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                }

                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }

        webView.addJavascriptInterface(NativeBridge(this), "NativeBridge")
    }

    private var isHandlingBackPress = false
    private lateinit var backPressCallback: OnBackPressedCallback

    private fun setupBackPressHandler() {
        backPressCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isHandlingBackPress) return  // Prevent re-entrant calls during async callback
                isHandlingBackPress = true
                webView.evaluateJavascript(
                    "if(typeof handleBackPress==='function'){handleBackPress()}else{false}"
                ) { result ->
                    isHandlingBackPress = false
                    if (result == "false" || result == null) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressCallback)
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun hasPermissions(): Boolean {
        val cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val storageOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        return cameraOk && storageOk
    }

    private fun createImageFile(): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile("JPEG_${timestamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create a ZIP package from base64 photos and share it.
     * Returns the file path via onPackageCreated callback.
     */
    fun createAndSharePackage(packageName: String, photosJson: String, namingMode: String, packMode: String) {
        Thread {
            try {
                val photosDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "packages")
                if (!photosDir.exists()) photosDir.mkdirs()

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                val safeName = packageName.replace(Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5-]"), "_")
                val zipFile = File(photosDir, "${safeName}_${timestamp}.zip")

                ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                    val jsonObj = JSONObject(photosJson)
                    val keys = jsonObj.keys()
                    var globalIndex = 0

                    while (keys.hasNext()) {
                        val docType = keys.next()
                        val arr = jsonObj.getJSONArray(docType)

                        for (i in 0 until arr.length()) {
                            globalIndex++
                            val base64Str = arr.getString(i)
                            try {
                                val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
                                // Determine entry path based on naming and pack modes
                                val entryName: String
                                if (namingMode == "order") {
                                    // By capture order: 01.jpg, 02.jpg, ...
                                    val fileName = String.format("%02d", globalIndex) + ".jpg"
                                    entryName = if (packMode == "folder") "${docType}/${fileName}" else fileName
                                } else {
                                    // By doc type name: 身份证_01.jpg, 身份证_02.jpg, ...
                                    val fileName = "${docType}_${String.format("%02d", i + 1)}.jpg"
                                    entryName = if (packMode == "folder") "${docType}/${fileName}" else fileName
                                }
                                val entry = ZipEntry(entryName)
                                zipOut.putNextEntry(entry)
                                zipOut.write(bytes)
                                zipOut.closeEntry()
                            } catch (e: Exception) {
                                // Skip invalid base64 data
                            }
                        }
                    }
                }

                // Share the file
                val appPkg = this@MainActivity.packageName
                val uri = FileProvider.getUriForFile(this@MainActivity, "${appPkg}.fileprovider", zipFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, safeName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                runOnUiThread {
                    startActivity(Intent.createChooser(shareIntent, "分享打包文件"))
                    callJsFunction("window.onPackageCreated && window.onPackageCreated('${escapeJs(zipFile.absolutePath)}', '')")
                }

            } catch (e: Exception) {
                android.util.Log.e("CollectorKing", "打包失败", e)
                runOnUiThread {
                    Toast.makeText(this, "打包失败: ${e.message}", Toast.LENGTH_LONG).show()
                    callJsFunction("window.onPackageCreated && window.onPackageCreated('', '${escapeJs(e.message ?: "unknown")}')")
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        // Re-enable back press callback in case it was disabled when exiting from home screen
        if (::backPressCallback.isInitialized) {
            backPressCallback.isEnabled = true
        }
        // Detect system dark mode and inject into WebView for HyperOS compatibility
        injectSystemDarkMode()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Detect system dark mode changes in real-time (for HyperOS where matchMedia doesn't work)
        val newDarkMode = (newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        webView.evaluateJavascript("if(typeof applySystemDarkMode==='function'){applySystemDarkMode($newDarkMode)}", null)
    }

    private fun injectSystemDarkMode() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        webView.evaluateJavascript("if(typeof applySystemDarkMode==='function'){applySystemDarkMode($isDarkMode)}", null)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}