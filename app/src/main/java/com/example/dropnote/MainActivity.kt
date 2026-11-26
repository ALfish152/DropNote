package com.example.dropnote

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.dropnote.server.NoteStorage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var running by remember { mutableStateOf(DropNoteServer.isRunning()) }
            var darkTheme by remember { mutableStateOf(systemDark) }
            var keepAwake by remember { mutableStateOf(true) }
            var qrExpanded by remember { mutableStateOf(true) }
            var serverUrl by remember { mutableStateOf("") }
            var localIp by remember { mutableStateOf("Offline") }
            var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

            val connectedCount by DropNoteServer.connectedClientsCount.collectAsState()
            val notes by NoteStorage.notes.collectAsState()

            var pendingFileCallback by remember {
                mutableStateOf<ValueCallback<Array<Uri>>?>(null)
            }
            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val uris = WebChromeClient.FileChooserParams.parseResult(
                    result.resultCode,
                    result.data
                )
                pendingFileCallback?.onReceiveValue(uris)
                pendingFileCallback = null
            }

            LaunchedEffect(running, keepAwake) {
                if (running && keepAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val colorScheme = if (darkTheme) {
                darkColorScheme(
                    primary = Color(0xFF60A5FA),
                    onPrimary = Color(0xFF0F172A),
                    background = Color(0xFF0B1120),
                    surface = Color(0xFF111827),
                    surfaceVariant = Color(0xFF1E293B),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF8FAFC),
                    onSurfaceVariant = Color(0xFF94A3B8),
                    error = Color(0xFFEF4444)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF2563EB),
                    onPrimary = Color.White,
                    background = Color(0xFFF1F5F9),
                    surface = Color.White,
                    surfaceVariant = Color(0xFFE2E8F0),
                    onBackground = Color(0xFF0F172A),
                    onSurface = Color(0xFF0F172A),
                    onSurfaceVariant = Color(0xFF475569),
                    error = Color(0xFFDC2626)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DropNoteHostScreen(
                        running = running,
                        darkTheme = darkTheme,
                        keepAwake = keepAwake,
                        qrExpanded = qrExpanded,
                        serverUrl = serverUrl,
                        localIp = localIp,
                        qrBitmap = qrBitmap,
                        connectedCount = connectedCount,
                        noteCount = notes.size,
                        onToggleTheme = { darkTheme = !darkTheme },
                        onToggleKeepAwake = { keepAwake = it },
                        onToggleQrExpanded = { qrExpanded = !qrExpanded },
                        onToggleServer = {
                            if (running) {
                                DropNoteServer.stop()
                                qrBitmap = null
                                serverUrl = ""
                                localIp = "Offline"
                            } else {
                                val ip = getLocalIpAddress()
                                DropNoteServer.start(this@MainActivity, ip)
                                val url = "http://$ip:8080"
                                localIp = ip
                                serverUrl = url
                                qrBitmap = generateQrCode(url, 640)
                                qrExpanded = true
                            }
                            running = !running
                        },
                        onClearAllNotes = {
                            NoteStorage.clearAll()
                            Toast.makeText(this@MainActivity, "Cleared board", Toast.LENGTH_SHORT).show()
                        },
                        onOpenFileChooser = { callback, intent ->
                            pendingFileCallback?.onReceiveValue(null)
                            pendingFileCallback = callback
                            filePickerLauncher.launch(intent)
                        },
                        onShowToast = { msg ->
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    private fun generateQrCode(text: String, size: Int): Bitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            val qrDarkColor = android.graphics.Color.parseColor("#0F172A")
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) qrDarkColor else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@Composable
fun DropNoteHostScreen(
    running: Boolean,
    darkTheme: Boolean,
    keepAwake: Boolean,
    qrExpanded: Boolean,
    serverUrl: String,
    localIp: String,
    qrBitmap: Bitmap?,
    connectedCount: Int,
    noteCount: Int,
    onToggleTheme: () -> Unit,
    onToggleKeepAwake: (Boolean) -> Unit,
    onToggleQrExpanded: () -> Unit,
    onToggleServer: () -> Unit,
    onClearAllNotes: () -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
    onShowToast: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DN",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "DropNote Host",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (running) "$noteCount notes • Port 8080" else "Server Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (running) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (connectedCount > 0) Color(0xFF10B981).copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = when (connectedCount) {
                                0 -> "No guests yet"
                                1 -> "1 guest"
                                else -> "$connectedCount guests"
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (connectedCount > 0) Color(0xFF10B981)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onToggleTheme,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (darkTheme) "Light" else "Dark",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWideScreen = maxWidth >= 650.dp

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HostControlCard(
                            running = running,
                            qrExpanded = qrExpanded,
                            serverUrl = serverUrl,
                            localIp = localIp,
                            qrBitmap = qrBitmap,
                            keepAwake = keepAwake,
                            hasNotes = noteCount > 0,
                            onToggleQrExpanded = onToggleQrExpanded,
                            onToggleKeepAwake = onToggleKeepAwake,
                            onToggleServer = onToggleServer,
                            onClearAllNotes = onClearAllNotes,
                            onCopyUrl = {
                                clipboardManager.setText(AnnotatedString(serverUrl))
                                onShowToast("Copied $serverUrl")
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        if (running) {
                            EmbeddedDropNoteBoard(
                                darkTheme = darkTheme,
                                onOpenFileChooser = onOpenFileChooser
                            )
                        } else {
                            OfflineBoardPlaceholder(onStartServer = onToggleServer)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HostControlCard(
                        running = running,
                        qrExpanded = qrExpanded,
                        serverUrl = serverUrl,
                        localIp = localIp,
                        qrBitmap = qrBitmap,
                        keepAwake = keepAwake,
                        hasNotes = noteCount > 0,
                        onToggleQrExpanded = onToggleQrExpanded,
                        onToggleKeepAwake = onToggleKeepAwake,
                        onToggleServer = onToggleServer,
                        onClearAllNotes = onClearAllNotes,
                        onCopyUrl = {
                            clipboardManager.setText(AnnotatedString(serverUrl))
                            onShowToast("Copied $serverUrl")
                        }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        if (running) {
                            EmbeddedDropNoteBoard(
                                darkTheme = darkTheme,
                                onOpenFileChooser = onOpenFileChooser
                            )
                        } else {
                            OfflineBoardPlaceholder(onStartServer = onToggleServer)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedDropNoteBoard(
    darkTheme: Boolean,
    onOpenFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(darkTheme, webViewRef) {
        webViewRef?.evaluateJavascript(
            "if (window.setHostDarkMode) { window.setHostDarkMode($darkTheme); }",
            null
        )
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewRef = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.userAgentString = "${settings.userAgentString} DropNoteHost"
                clearCache(true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            "if (window.setHostDarkMode) { window.setHostDarkMode($darkTheme); }",
                            null
                        )
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        if (filePathCallback == null) return false
                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        onOpenFileChooser(filePathCallback, intent)
                        return true
                    }
                }

                loadUrl("http://127.0.0.1:8080/#board")
            }
        }
    )
}

@Composable
fun OfflineBoardPlaceholder(onStartServer: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Start Server to Open Board",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Once started, you can post notes, pick colors, upload photos/files, and share your QR code with guests.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartServer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Server", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HostControlCard(
    running: Boolean,
    qrExpanded: Boolean,
    serverUrl: String,
    localIp: String,
    qrBitmap: Bitmap?,
    keepAwake: Boolean,
    hasNotes: Boolean,
    onToggleQrExpanded: () -> Unit,
    onToggleKeepAwake: (Boolean) -> Unit,
    onToggleServer: () -> Unit,
    onClearAllNotes: () -> Unit,
    onCopyUrl: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (running) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(if (running) pulseAlpha else 1f)
                            .clip(CircleShape)
                            .background(if (running) Color(0xFF10B981) else Color(0xFF64748B))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (running) "$localIp:8080" else "Offline",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (running) {
                        OutlinedButton(
                            onClick = onToggleQrExpanded,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (qrExpanded) "Hide QR" else "Show QR",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Button(
                        onClick = onToggleServer,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (running) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (running) "Stop" else "Start",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(visible = running && qrExpanded && qrBitmap != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "DropNote QR Code",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(165.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = onCopyUrl,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Keep Awake",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = keepAwake,
                                onCheckedChange = onToggleKeepAwake
                            )
                        }

                        if (hasNotes) {
                            TextButton(
                                onClick = onClearAllNotes,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Clear Board",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}