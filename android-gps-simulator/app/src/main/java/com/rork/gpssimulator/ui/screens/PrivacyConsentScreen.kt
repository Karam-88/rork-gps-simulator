package com.rork.gpssimulator.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rork.gpssimulator.i18n.AppLanguage
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Full offline policy, clear separate choice on optional Analytics.
 * The GPS simulator works when the user declines. No forced scrolling,
 * pre-ticked consent or hidden reject control.
 */
@Composable
fun PrivacyConsentScreen(language: AppLanguage, onAgree: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    val copy = remember(language) { consentCopy(language) }
    val policyHtml = remember {
        context.assets.open("privacy-policy.html").use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }
    }
    val navy = Color(0xFF061626)
    val white = Color.White
    val blue = Color(0xFF087CF7)

    CompositionLocalProvider(
        LocalLayoutDirection provides if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        Column(
            Modifier.fillMaxSize().background(navy).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                copy.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = white,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (language == AppLanguage.ARABIC) TextAlign.Right else TextAlign.Start,
            )
            Text(copy.introduction, fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFFCFD9E6))
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        setBackgroundColor(android.graphics.Color.WHITE)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return true
                                if (uri.scheme == "https" || uri.scheme == "mailto") {
                                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
                                }
                                return true
                            }
                        }
                        loadDataWithBaseURL(null, policyHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color(0xFF406187), RoundedCornerShape(12.dp)),
            )
            Text(
                copy.choice,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFFDAE7F6),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAgree,
                    modifier = Modifier.weight(1f).height(52.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blue, contentColor = white),
                ) {
                    Text(copy.agree, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                }
                Button(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f).height(52.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = white, contentColor = navy),
                ) {
                    Text(copy.decline, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

private data class ConsentCopy(
    val title: String,
    val introduction: String,
    val choice: String,
    val agree: String,
    val decline: String,
)

private fun consentCopy(language: AppLanguage): ConsentCopy = when (language) {
    AppLanguage.ARABIC -> ConsentCopy(
        "سياسة الخصوصية",
        "يمكنك قراءة السياسة الكاملة أدناه. نطلب موافقتك الاختيارية على إحصاءات استخدام Firebase Analytics.",
        "الموافقة تفعّل إحصاءات الاستخدام، والرفض لا يؤثر على ميزات GPS أو الخرائط. يمكنك تغيير قرارك من الإعدادات.",
        "أوافق", "أرفض",
    )
    AppLanguage.GERMAN -> ConsentCopy(
        "Datenschutzerklärung",
        "Unten findest du die vollständige Datenschutzerklärung. Wir bitten dich um deine freiwillige Einwilligung in Firebase Analytics.",
        "Bei Zustimmung werden Nutzungsstatistiken erhoben. Auch bei Ablehnung funktionieren GPS und Karten uneingeschränkt. Du kannst dies in den Einstellungen ändern.",
        "Zustimmen", "Ablehnen",
    )
    AppLanguage.FRENCH -> ConsentCopy(
        "Politique de confidentialité",
        "Consultez ci-dessous la politique complète. Nous vous demandons votre accord facultatif pour Firebase Analytics.",
        "Le refus ne limite pas les fonctions GPS et cartes. Vous pouvez modifier votre choix dans les paramètres.",
        "Accepter", "Refuser",
    )
    AppLanguage.SPANISH -> ConsentCopy(
        "Política de privacidad",
        "Lee la política completa a continuación. Solicitamos tu consentimiento opcional para Firebase Analytics.",
        "Si rechazas, las funciones GPS y los mapas siguen disponibles. Puedes cambiarlo en Ajustes.",
        "Aceptar", "Rechazar",
    )
    AppLanguage.PORTUGUESE_BRAZIL -> ConsentCopy(
        "Política de privacidade",
        "Leia a política completa abaixo. Solicitamos seu consentimento opcional para o Firebase Analytics.",
        "Recusar não limita as funções de GPS e mapa. Você pode alterar sua escolha nas configurações.",
        "Aceitar", "Recusar",
    )
    AppLanguage.TURKISH -> ConsentCopy(
        "Gizlilik Politikası",
        "Tam gizlilik politikasını aşağıda okuyabilirsiniz. Firebase Analytics için isteğe bağlı izninizi istiyoruz.",
        "Reddetmeniz GPS veya harita özelliklerini kısıtlamaz. Seçiminizi ayarlardan değiştirebilirsiniz.",
        "Kabul et", "Reddet",
    )
    AppLanguage.RUSSIAN -> ConsentCopy(
        "Политика конфиденциальности",
        "Ниже приведена полная политика. Мы запрашиваем необязательное согласие на Firebase Analytics.",
        "Отказ не ограничивает GPS и карты. Изменить выбор можно в настройках.",
        "Согласен", "Отказаться",
    )
    AppLanguage.HINDI -> ConsentCopy(
        "गोपनीयता नीति",
        "पूरी गोपनीयता नीति नीचे दी गई है। हम Firebase Analytics के लिए आपकी वैकल्पिक सहमति माँगते हैं।",
        "मना करने पर GPS या मैप की सुविधाएँ बंद नहीं होंगी। बाद में सेटिंग में बदलाव कर सकते हैं।",
        "सहमत", "अस्वीकार",
    )
    AppLanguage.INDONESIAN -> ConsentCopy(
        "Kebijakan Privasi",
        "Baca kebijakan lengkap di bawah. Kami meminta persetujuan opsional Anda untuk Firebase Analytics.",
        "Menolak tidak membatasi fitur GPS atau peta. Anda dapat mengubah pilihan di Pengaturan.",
        "Setuju", "Tolak",
    )
    AppLanguage.JAPANESE -> ConsentCopy(
        "プライバシーポリシー",
        "以下の全文をご確認ください。Firebase Analytics の利用について任意の同意をお願いします。",
        "同意しなくても GPS と地図を利用できます。設定からいつでも変更できます。",
        "同意する", "拒否する",
    )
    AppLanguage.CHINESE_SIMPLIFIED -> ConsentCopy(
        "隐私政策",
        "请阅读下方完整政策。我们希望获得您对 Firebase Analytics 的自愿同意。",
        "拒绝不会影响 GPS 或地图功能，您可以在设置中更改选择。",
        "同意", "拒绝",
    )
    AppLanguage.ENGLISH -> ConsentCopy(
        "Privacy Policy",
        "Read the complete policy below. We ask for your optional consent to Firebase Analytics usage statistics.",
        "Accepting enables usage analytics. Declining does not limit GPS or maps. You can change your choice in Settings.",
        "Agree", "Decline",
    )
}
