package com.example.youtubedownloader.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.youtubedownloader.CookieHelper
import com.example.youtubedownloader.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginSheet(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit
) {
    val context = LocalContext.current
    var pageTitle by remember { mutableStateOf("Loading…") }
    var isLoading by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Sign in to YouTube",
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "Fixes bot detection on some networks",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoggedIn) {
                    Button(
                        onClick = {
                            CookieHelper.saveCookiesFromWebView(context)
                            onLoggedIn()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessGreen
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Done", fontSize = 13.sp, color = DarkNavy)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted)
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth(),
                color = CyanPrimary,
                trackColor = CardDark
            )
        }

        Surface(
            modifier = Modifier.padding(horizontal = 8.dp),
            color = CardDark,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                pageTitle,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val webView = this
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ) = false

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            pageTitle = view?.title ?: ""
                            val cookies = CookieManager.getInstance()
                                .getCookie("https://www.youtube.com")
                            isLoggedIn = cookies != null && (
                                    cookies.contains("SID=") ||
                                            cookies.contains("LOGIN_INFO") ||
                                            cookies.contains("SSID="))
                            CookieManager.getInstance().flush()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            isLoading = newProgress < 100
                        }
                    }
                    loadUrl("https://accounts.google.com/ServiceLogin?continue=https://www.youtube.com")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        )
    }
}