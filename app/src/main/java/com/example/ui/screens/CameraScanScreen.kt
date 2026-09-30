package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.BookEntity
import com.example.network.BookLookupResult
import com.example.network.BookLookupService
import com.example.network.ExtractedBookData
import com.example.network.GeminiVisionService
import com.example.ui.components.NothingBadge
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingTextField
import com.example.ui.theme.CmfOrange
import com.example.ui.theme.MatrixAmber
import com.example.ui.theme.MatrixCyan
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

enum class ScanMode {
    COVER_AI,
    BARCODE_ISBN,
    MANUAL_SEARCH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    onBookScannedAndSaved: (BookEntity) -> Unit,
    onClose: () -> Unit,
    onSaveBitmapCover: suspend (Bitmap) -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scanMode by remember { mutableStateOf(ScanMode.COVER_AI) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisStatusText by remember { mutableStateOf("Analisando...") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scannedResult by remember { mutableStateOf<ExtractedBookData?>(null) }

    // Bottom sheet review state
    var showReviewSheet by remember { mutableStateOf(false) }
    var reviewTitle by remember { mutableStateOf("") }
    var reviewAuthor by remember { mutableStateOf("") }
    var reviewIsbn by remember { mutableStateOf("") }
    var reviewGenre by remember { mutableStateOf("") }
    var reviewPageCount by remember { mutableStateOf("") }
    var reviewIsWishlist by remember { mutableStateOf(false) }

    // Manual quick search sheet state
    var showManualSearchSheet by remember { mutableStateOf(false) }
    var manualQuery by remember { mutableStateOf("") }
    var manualSearchResults by remember { mutableStateOf<List<BookLookupResult>>(emptyList()) }
    var isManualSearching by remember { mutableStateOf(false) }

    // Photo picker fallback
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isAnalyzing = true
                analysisStatusText = "Processando imagem da galeria..."
                val bitmap = loadBitmapFromUri(context, uri)
                if (bitmap != null) {
                    capturedBitmap = bitmap
                    analysisStatusText = "Consultando código de barras e IA nas 5 APIs..."
                    val extracted = GeminiVisionService.analyzeBookCover(bitmap)
                    scannedResult = extracted
                    reviewTitle = extracted.title
                    reviewAuthor = extracted.author
                    reviewIsbn = extracted.isbn
                    reviewGenre = extracted.genre
                    reviewPageCount = if (extracted.pageCount > 0) extracted.pageCount.toString() else "250"
                    showReviewSheet = true
                }
                isAnalyzing = false
            }
        }
    }

    fun capturePhotoAndAnalyze() {
        val capture = imageCapture ?: return
        isAnalyzing = true
        analysisStatusText = if (scanMode == ScanMode.BARCODE_ISBN) {
            "Decodificando código de barras e consultando BrasilAPI/Google Books..."
        } else {
            "Identificando capa por IA e enriquecendo com 5 APIs..."
        }

        val executor = Executors.newSingleThreadExecutor()
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    coroutineScope.launch {
                        if (bitmap != null) {
                            capturedBitmap = bitmap
                            val extracted = GeminiVisionService.analyzeBookCover(bitmap)
                            scannedResult = extracted
                            reviewTitle = extracted.title
                            reviewAuthor = extracted.author
                            reviewIsbn = extracted.isbn
                            reviewGenre = extracted.genre
                            reviewPageCount = if (extracted.pageCount > 0) extracted.pageCount.toString() else "320"
                            showReviewSheet = true
                        }
                        isAnalyzing = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraScanScreen", "Capture failed: ${exception.message}", exception)
                    isAnalyzing = false
                }
            }
        )
    }

    // Quick demo physical book cover testing (for emulator / rapid testing)
    fun simulateDemoScan(sampleTitle: String, sampleAuthor: String, sampleIsbn: String, sampleGenre: String, samplePages: Int) {
        coroutineScope.launch {
            isAnalyzing = true
            analysisStatusText = "Simulando leitura de livro..."
            val demoBitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(demoBitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1B1D22")
            }
            canvas.drawRect(0f, 0f, 400f, 600f, paint)

            capturedBitmap = demoBitmap
            val extracted = ExtractedBookData(
                title = sampleTitle,
                author = sampleAuthor,
                isbn = sampleIsbn,
                genre = sampleGenre,
                synopsis = "Livro físico catalogado por scanner com sucesso.",
                pageCount = samplePages,
                confidence = "SIMULADOR // TESTE RÁPIDO"
            )
            scannedResult = extracted
            reviewTitle = extracted.title
            reviewAuthor = extracted.author
            reviewIsbn = extracted.isbn
            reviewGenre = extracted.genre
            reviewPageCount = extracted.pageCount.toString()
            showReviewSheet = true
            isAnalyzing = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NothingBlack)
    ) {
        // Camera Preview Layer
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture
                            )
                        } catch (exc: Exception) {
                            Log.e("CameraScan", "Use case binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission placeholder
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ACESSO À CÂMERA NECESSÁRIO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Para escanear livros físicos em tempo real, permita o acesso à câmera.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                NothingButton(
                    text = "Permitir Câmera",
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    testTag = "grant_camera_permission"
                )
            }
        }

        // Scanner Reticle Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val isBarcode = scanMode == ScanMode.BARCODE_ISBN
            val frameW = if (isBarcode) w * 0.85f else w * 0.78f
            val frameH = if (isBarcode) frameW * 0.55f else frameW * 1.45f
            val left = (w - frameW) / 2
            val top = (h - frameH) / 2 - 30.dp.toPx()

            // Corner brackets [   ]
            val bracketLen = 28.dp.toPx()
            val bracketColor = if (isBarcode) Color(0xFF00E676) else Color(0xFFFF4815)
            val strokeW = 3.dp.toPx()

            // Top-left
            drawLine(bracketColor, Offset(left, top), Offset(left + bracketLen, top), strokeW)
            drawLine(bracketColor, Offset(left, top), Offset(left, top + bracketLen), strokeW)

            // Top-right
            drawLine(bracketColor, Offset(left + frameW, top), Offset(left + frameW - bracketLen, top), strokeW)
            drawLine(bracketColor, Offset(left + frameW, top), Offset(left + frameW, top + bracketLen), strokeW)

            // Bottom-left
            drawLine(bracketColor, Offset(left, top + frameH), Offset(left + bracketLen, top + frameH), strokeW)
            drawLine(bracketColor, Offset(left, top + frameH), Offset(left, top + frameH - bracketLen), strokeW)

            // Bottom-right
            drawLine(bracketColor, Offset(left + frameW, top + frameH), Offset(left + frameW - bracketLen, top + frameH), strokeW)
            drawLine(bracketColor, Offset(left + frameW, top + frameH), Offset(left + frameW, top + frameH - bracketLen), strokeW)

            // Laser beam across barcode
            if (isBarcode) {
                val cy = top + frameH / 2
                drawLine(Color.Red.copy(alpha = 0.85f), Offset(left + 8.dp.toPx(), cy), Offset(left + frameW - 8.dp.toPx(), cy), 2.dp.toPx())
            } else {
                // Center target crosshair
                val cx = left + frameW / 2
                val cy = top + frameH / 2
                val crossSize = 10.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - crossSize, cy), Offset(cx + crossSize, cy), 1.5.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.5f), Offset(cx, cy - crossSize), Offset(cx, cy + crossSize), 1.5.dp.toPx())
            }
        }

        // Top HUD Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("close_camera_scanner")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
                }

                // HUD Status Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (scanMode == ScanMode.BARCODE_ISBN) MatrixGreen else NothingRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (scanMode == ScanMode.BARCODE_ISBN) "SCAN // CÓDIGO BARRAS" else "SCAN // IA VISION 4K",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .testTag("switch_camera_lens")
                    ) {
                        Icon(Icons.Filled.FlipCameraAndroid, contentDescription = "Inverter Câmera", tint = Color.White)
                    }
                }
            }

            // Mode Selector Pill (Cover vs Barcode vs Manual Search)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = scanMode == ScanMode.COVER_AI,
                            onClick = { scanMode = ScanMode.COVER_AI },
                            label = { Text("CAPA (IA)", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CmfOrange,
                                selectedLabelColor = Color.White,
                                containerColor = Color.Transparent,
                                labelColor = Color.LightGray
                            )
                        )
                        FilterChip(
                            selected = scanMode == ScanMode.BARCODE_ISBN,
                            onClick = { scanMode = ScanMode.BARCODE_ISBN },
                            label = { Text("CÓDIGO BARRAS", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MatrixGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = Color.Transparent,
                                labelColor = Color.LightGray
                            )
                        )
                        FilterChip(
                            selected = false,
                            onClick = { showManualSearchSheet = true },
                            label = { Text("DIGITAR ISBN", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                labelColor = MatrixCyan
                            ),
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = MatrixCyan, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }
        }

        // Telemetry HUD info above shutter
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick Demo Bar (ideal for testing in emulator)
            Text(
                text = "TESTE RÁPIDO // TOQUE PARA IDENTIFICAR UM LIVRO",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = { simulateDemoScan("Clean Code", "Robert C. Martin", "9780132350884", "Tecnologia", 464) },
                    label = { Text("Clean Code", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black.copy(alpha = 0.7f), labelColor = Color.White),
                    modifier = Modifier.testTag("demo_clean_code")
                )
                FilterChip(
                    selected = false,
                    onClick = { simulateDemoScan("1984", "George Orwell", "9788535914849", "Ficção Científica", 336) },
                    label = { Text("1984", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black.copy(alpha = 0.7f), labelColor = Color.White),
                    modifier = Modifier.testTag("demo_1984")
                )
                FilterChip(
                    selected = false,
                    onClick = { simulateDemoScan("Dom Casmurro", "Machado de Assis", "9788520925201", "Literatura Brasileira", 256) },
                    label = { Text("Dom Casmurro", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black.copy(alpha = 0.7f), labelColor = Color.White),
                    modifier = Modifier.testTag("demo_dom_casmurro")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shutter Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery picker button
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .testTag("pick_photo_from_gallery")
                ) {
                    Icon(Icons.Filled.Image, contentDescription = "Escolher da Galeria", tint = Color.White)
                }

                // Shutter Capture Button (Nothing Vermilion Orb)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(if (scanMode == ScanMode.BARCODE_ISBN) MatrixGreen else CmfOrange)
                        .clickable(
                            enabled = !isAnalyzing,
                            onClick = { capturePhotoAndAnalyze() }
                        )
                        .testTag("shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }

                // Quick Barcode / Manual Search action
                IconButton(
                    onClick = { showManualSearchSheet = true },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .testTag("open_manual_search_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = "Digitar ISBN ou Título",
                        tint = MatrixCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Analyzing Loading HUD
        AnimatedVisibility(
            visible = isAnalyzing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = if (scanMode == ScanMode.BARCODE_ISBN) MatrixGreen else CmfOrange,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "IDENTIFICANDO LIVRO NAS 5 APIS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = analysisStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    // Modal Review Sheet when a book is scanned
    if (showReviewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReviewSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MatrixGreen)
                        )
                        Text(
                            text = "LIVRO IDENTIFICADO COM SUCESSO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatrixGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    scannedResult?.confidence?.let { conf ->
                        NothingBadge(
                            text = conf,
                            color = MatrixCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Display captured photo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (capturedBitmap != null) {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Foto da Capa do Livro",
                            modifier = Modifier
                                .width(90.dp)
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dados extraídos e validados",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reviewTitle.ifBlank { "Sem título" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = reviewAuthor.ifBlank { "Autor não identificado" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Editable Fields so user has total control
                NothingTextField(
                    value = reviewTitle,
                    onValueChange = { reviewTitle = it },
                    label = "Nome do Livro (Exato)",
                    placeholder = "Ex: O Senhor dos Anéis",
                    testTag = "input_review_title"
                )

                Spacer(modifier = Modifier.height(12.dp))

                NothingTextField(
                    value = reviewAuthor,
                    onValueChange = { reviewAuthor = it },
                    label = "Autor(es)",
                    placeholder = "Ex: J.R.R. Tolkien",
                    testTag = "input_review_author"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NothingTextField(
                        value = reviewIsbn,
                        onValueChange = { reviewIsbn = it },
                        label = "ISBN",
                        placeholder = "Ex: 97885...",
                        modifier = Modifier.weight(1.3f),
                        testTag = "input_review_isbn"
                    )

                    NothingTextField(
                        value = reviewGenre,
                        onValueChange = { reviewGenre = it },
                        label = "Gênero",
                        placeholder = "Ex: Ficção",
                        modifier = Modifier.weight(1f),
                        testTag = "input_review_genre"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Target Destination (Library vs Wishlist)
                Text(
                    text = "DESTINO DE SALVAMENTO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = !reviewIsWishlist,
                        onClick = { reviewIsWishlist = false },
                        label = { Text("MINHA BIBLIOTECA (TENHO)") },
                        modifier = Modifier.weight(1f).testTag("chip_save_library")
                    )
                    FilterChip(
                        selected = reviewIsWishlist,
                        onClick = { reviewIsWishlist = true },
                        label = { Text("LISTA DE DESEJOS") },
                        modifier = Modifier.weight(1f).testTag("chip_save_wishlist")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save button
                NothingButton(
                    text = "Salvar na Biblioteca",
                    onClick = {
                        coroutineScope.launch {
                            val coverUri = if (capturedBitmap != null) {
                                onSaveBitmapCover(capturedBitmap!!)
                            } else ""

                            val pages = reviewPageCount.toIntOrNull() ?: 250
                            val book = BookEntity(
                                title = reviewTitle.ifBlank { "Livro Escaneado" },
                                author = reviewAuthor.ifBlank { "Autor" },
                                isbn = reviewIsbn,
                                coverUri = coverUri,
                                status = if (reviewIsWishlist) "WISHLIST" else "OWNED",
                                isWishlist = reviewIsWishlist,
                                genre = reviewGenre.ifBlank { "Geral" },
                                pageCount = pages,
                                isSynced = true
                            )
                            onBookScannedAndSaved(book)
                            showReviewSheet = false
                            onClose()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "confirm_save_book"
                )
            }
        }
    }

    // Modal Sheet for Quick Manual ISBN / Title Search right inside scanner
    if (showManualSearchSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManualSearchSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "BUSCA MANUAL NAS 5 APIS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showManualSearchSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                NothingTextField(
                    value = manualQuery,
                    onValueChange = {
                        manualQuery = it
                        if (it.length >= 3) {
                            coroutineScope.launch {
                                isManualSearching = true
                                val res = BookLookupService.searchBooks(it)
                                manualSearchResults = res
                                isManualSearching = false
                            }
                        } else if (it.isBlank()) {
                            manualSearchResults = emptyList()
                        }
                    },
                    label = "Digite o ISBN (978...) ou Título do Livro",
                    placeholder = "Ex: 9788576573135 ou Duna",
                    leadingIcon = Icons.Filled.Search,
                    testTag = "input_manual_scanner_query"
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isManualSearching) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MatrixCyan, modifier = Modifier.size(28.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(manualSearchResults) { result ->
                            NothingCard(
                                onClick = {
                                    reviewTitle = result.title
                                    reviewAuthor = result.author
                                    reviewIsbn = result.isbn
                                    reviewGenre = result.genre
                                    reviewPageCount = if (result.pageCount > 0) result.pageCount.toString() else "300"
                                    scannedResult = ExtractedBookData(
                                        title = result.title,
                                        author = result.author,
                                        isbn = result.isbn,
                                        genre = result.genre,
                                        synopsis = result.description,
                                        pageCount = result.pageCount,
                                        confidence = result.source
                                    )
                                    showManualSearchSheet = false
                                    showReviewSheet = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(result.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(result.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (result.isbn.isNotBlank()) {
                                            Text("ISBN: ${result.isbn}", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
                                        }
                                    }
                                    NothingBadge(text = result.source, color = MatrixCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helpers
private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val planeProxy = image.planes[0]
    val buffer: ByteBuffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    val rotation = image.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotation.toFloat())
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (_: Exception) {
        null
    }
}
