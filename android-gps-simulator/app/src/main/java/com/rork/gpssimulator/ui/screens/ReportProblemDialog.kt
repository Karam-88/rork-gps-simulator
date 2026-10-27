package com.rork.gpssimulator.ui.screens

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.problemReportCopy

/** Keeps an in-flight submission alive across dialog recomposition and activity rotation. */
internal class ProblemReportViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    var description by mutableStateOf(savedState.get<String>("description").orEmpty())
        private set
    var sending by mutableStateOf(false)
        private set
    var sent by mutableStateOf(savedState.get<Boolean>("sent") ?: false)
        private set
    var failed by mutableStateOf(false)
        private set
    var invalid by mutableStateOf(false)
        private set

    fun edit(value: String) {
        if (sending || sent) return
        // Count Unicode code points, so emoji are not cut in half at the limit.
        description = value.substring(0, value.offsetByCodePoints(0, value.codePointCount(0, value.length).coerceAtMost(500)))
        savedState["description"] = description
        invalid = false
        failed = false
    }

    fun reset() {
        if (sending) return
        sent = false
        savedState["sent"] = false
        edit("")
    }

    fun send(version: String) {
        if (sending || sent) return
        val message = description.trim()
        if (message.isBlank() || message.codePointCount(0, message.length) > 500) {
            invalid = true
            return
        }
        sending = true
        failed = false
        fun fail() { sending = false; failed = true }
        fun submit(uid: String) {
            // Preserve the existing collection, anonymous authentication and rules-compatible schema.
            // The free-form dialog uses the existing "Other issue" category (5).
            val report = hashMapOf<String, Any>(
                "userId" to uid,
                "issueType" to 5,
                "description" to message,
                "appVersion" to version,
                "androidVersion" to Build.VERSION.RELEASE,
                "deviceModel" to (Build.MANUFACTURER + " " + Build.MODEL),
                "createdAt" to FieldValue.serverTimestamp(),
            )
            FirebaseFirestore.getInstance().collection("problem_reports").add(report)
                .addOnSuccessListener {
                    sending = false
                    sent = true
                    savedState["sent"] = true
                }
                .addOnFailureListener { fail() }
        }
        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null) submit(user.uid)
            else auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid == null) fail() else {
                        try { submit(uid) } catch (_: Exception) { fail() }
                    }
                }
                .addOnFailureListener { fail() }
        } catch (_: Exception) { fail() }
    }
}

@Composable
fun ReportProblemDialog(language: AppLanguage, onDismiss: () -> Unit) {
    val state: ProblemReportViewModel = viewModel()
    val context = LocalContext.current
    val copy = remember(language) { problemReportCopy(language) }
    val dismiss = { if (!state.sending) { state.reset(); onDismiss() } }
    val secondary = Color(0xFFB7C2D5)
    // Close only after Firestore confirms success; keep the dialog open on failure.
    LaunchedEffect(state.sent) {
        if (state.sent) {
            state.reset()
            onDismiss()
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides
        if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            // Insets bound the scrollable card above the keyboard, including in landscape.
            Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp),
                contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF101720),
                    border = BorderStroke(1.dp, Color(0xFF397FFF)),
                    shadowElevation = 20.dp,
                ) {
                    Column(
                        Modifier.background(Brush.radialGradient(listOf(Color(0x402F6ACA), Color.Transparent)))
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            SupportHeadset(Modifier.align(Alignment.Center).size(64.dp))
                            OutlinedIconButton(onClick = dismiss, enabled = !state.sending,
                                modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
                                border = BorderStroke(1.dp, Color(0xFF677693))) {
                                Icon(Icons.Default.Close, copy.close, tint = Color.White)
                            }
                        }
                        Text(copy.title, color = Color.White, fontSize = 22.sp, lineHeight = 28.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().semantics { heading() })
                        Text(copy.description, color = secondary, fontSize = 14.sp, lineHeight = 20.sp,
                            textAlign = TextAlign.Center)
                        OutlinedTextField(
                            value = state.description, onValueChange = state::edit,
                            enabled = !state.sending && !state.sent,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(copy.hint, color = Color(0xFF9DAAC0)) },
                            minLines = 3, maxLines = 4, shape = RoundedCornerShape(20.dp),
                            isError = state.invalid,
                            supportingText = {
                                Text("${state.description.codePointCount(0, state.description.length)}/500",
                                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, color = secondary)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                disabledTextColor = secondary,
                                focusedContainerColor = Color(0xFF202B3A), unfocusedContainerColor = Color(0xFF202B3A),
                                disabledContainerColor = Color(0xFF202B3A),
                                focusedBorderColor = Color(0xFF5B9AFF), unfocusedBorderColor = Color(0xFF596B85),
                                cursorColor = Color(0xFF79B7FF),
                            ),
                        )
                        val status = when {
                            state.invalid -> copy.validation
                            state.failed -> copy.failure
                            state.sent -> copy.success
                            else -> null
                        }
                        status?.let {
                            Text(it, color = if (state.sent) Color(0xFF83E4B9) else Color(0xFFFFB4AB),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        val enabled = !state.sending && !state.sent
                        Button(
                            onClick = {
                                val version = runCatching {
                                    @Suppress("DEPRECATION")
                                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                }.getOrNull() ?: "unknown"
                                state.send(version)
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(28.dp))
                                .background(Brush.verticalGradient(if (enabled)
                                    listOf(Color(0xFF379DFF), Color(0xFF315BFF))
                                    else listOf(Color(0xFF30496C), Color(0xFF263953)))),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent, contentColor = Color.White,
                                disabledContentColor = secondary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            if (state.sending) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (state.sending) copy.sending else copy.send, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        OutlinedButton(onClick = dismiss, enabled = !state.sending,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            border = BorderStroke(1.dp, Color(0xFF64738C)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = secondary)) {
                            Text(if (state.sent) copy.close else copy.cancel, fontSize = 17.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Small resolution-independent illustration; gradients give the headset depth without a bitmap. */
@Composable
private fun SupportHeadset(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 120f
        fun point(x: Float, y: Float) = Offset(x * u, y * u)
        fun box(x: Float, y: Float, w: Float, h: Float, radius: Float, colors: List<Color>) {
            drawRoundRect(Brush.linearGradient(colors, point(x, y), point(x + w, y + h)),
                point(x, y), Size(w * u, h * u), CornerRadius(radius * u))
        }
        val blue = listOf(Color(0xFF77D5FF), Color(0xFF367FFF), Color(0xFF1935A1))
        drawCircle(Brush.radialGradient(listOf(Color(0x553178FF), Color.Transparent)), 60 * u)
        drawArc(Brush.linearGradient(blue), 180f, 180f, false,
            point(16f, 12f), Size(88 * u, 96 * u), style = Stroke(7 * u))
        drawArc(Color(0xFF76B7FF), 200f, 125f, false,
            point(16f, 10f), Size(88 * u, 96 * u), style = Stroke(1.5f * u))
        box(31f, 37f, 58f, 47f, 14f, blue)
        val tail = Path().apply { moveTo(42*u, 78*u); lineTo(42*u, 94*u); lineTo(59*u, 80*u); close() }
        drawPath(tail, Color(0xFF2F62DB))
        listOf(46f, 60f, 74f).forEach { x ->
            drawCircle(Color(0xFF163A9C), 4.8f*u, point(x+1, 62f))
            drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFAACFFF))), 4.4f*u, point(x, 60f))
        }
        box(9f, 50f, 14f, 35f, 7f, blue)
        box(20f, 47f, 9f, 42f, 4f, blue)
        box(97f, 50f, 14f, 35f, 7f, blue)
        box(91f, 47f, 9f, 42f, 4f, blue)
        drawArc(Color(0xFF4F80ED), 0f, 90f, false,
            point(20f, 68f), Size(72*u, 39*u), style = Stroke(3*u))
        box(49f, 101f, 19f, 7f, 3.5f, blue)
    }
}
