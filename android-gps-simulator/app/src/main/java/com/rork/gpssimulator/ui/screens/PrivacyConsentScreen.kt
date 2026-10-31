package com.rork.gpssimulator.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * The GPS simulator works when the user declines. Both consent choices have
 * equal prominence and unlock together after the policy has been scrolled to
 * its end. No pre-ticked consent and no location values are collected here.
 */
@Composable
fun PrivacyConsentScreen(language: AppLanguage, onAgree: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    val copy = remember(language) { consentCopy(language) }
    var reachedPolicyEnd by remember(language) { mutableStateOf(false) }
    val policyHtml = remember(context, language) {
        val asset = if (language == AppLanguage.ARABIC) "privacy-policy-ar.html" else "privacy-policy.html"
        val original = context.assets.open(asset).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }
        // Embedded WebView uses the application's premium navy palette, not a
        // bright white webpage. The full published English policy remains intact.
        original.replace("</head>", EMBEDDED_POLICY_STYLE + "</head>")
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
            key(language) {
                AndroidView(
                    factory = { ctx ->
                        var finishedLoading = false
                        val tolerancePx = (20 * ctx.resources.displayMetrics.density).toInt()
                        val policyView = object : WebView(ctx) {
                            fun checkBottom() {
                                // WebView scroll range and extent are in matching physical units.
                                // Never unlock during loading or merely because the view is at top.
                                if (hasReachedPrivacyPolicyEnd(
                                        finishedLoading,
                                        scrollY,
                                        computeVerticalScrollExtent(),
                                        computeVerticalScrollRange(),
                                        tolerancePx,
                                    )
                                ) {
                                    reachedPolicyEnd = true
                                }
                            }

                            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                                super.onScrollChanged(l, t, oldl, oldt)
                                checkBottom()
                            }
                        }
                        policyView.apply {
                            settings.javaScriptEnabled = false
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            isVerticalScrollBarEnabled = true
                            setBackgroundColor(android.graphics.Color.rgb(10, 27, 44))
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    finishedLoading = true
                                    policyView.post { policyView.checkBottom() }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val uri = request?.url ?: return true
                                    if (uri.scheme == "https" || uri.scheme == "mailto") {
                                        try {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        } catch (_: Exception) {
                                            // No external app available; remain on the policy.
                                        }
                                    }
                                    return true
                                }
                            }
                            loadDataWithBaseURL(null, policyHtml, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .border(1.dp, Color(0xFF406187), RoundedCornerShape(12.dp)),
                )
            }
            Text(
                text = scrollInstruction(language, reachedPolicyEnd),
                fontSize = 12.sp,
                color = if (reachedPolicyEnd) Color(0xFF98DFBE) else Color(0xFFB5C9DE),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
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
                    onClick = { if (reachedPolicyEnd) onAgree() },
                    enabled = reachedPolicyEnd,
                    modifier = Modifier.weight(1f).height(52.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = blue, contentColor = white,
                        disabledContainerColor = Color(0xFF34475B),
                        disabledContentColor = Color(0xFFB8C5D1),
                    ),
                ) {
                    Text(copy.agree, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                }
                Button(
                    onClick = { if (reachedPolicyEnd) onDecline() },
                    enabled = reachedPolicyEnd,
                    modifier = Modifier.weight(1f).height(52.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E354C), contentColor = white,
                        disabledContainerColor = Color(0xFF34475B),
                        disabledContentColor = Color(0xFFB8C5D1),
                    ),
                ) {
                    Text(copy.decline, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}


/**
 * Enforces read-to-end for both optional choices equally. A policy that fits
 * entirely in the viewport needs no scrolling (e.g. large accessibility text).
 * For scrollable content, an actual positive scroll offset is mandatory.
 */
internal fun hasReachedPrivacyPolicyEnd(
    pageLoaded: Boolean,
    scrollY: Int,
    viewportExtentPx: Int,
    scrollRangePx: Int,
    tolerancePx: Int,
): Boolean {
    if (!pageLoaded || viewportExtentPx <= 0 || scrollRangePx <= 0) return false
    if (scrollRangePx <= viewportExtentPx) return true
    return scrollY > 0 &&
        scrollY + viewportExtentPx >= scrollRangePx - tolerancePx.coerceAtLeast(0)
}

private fun scrollInstruction(language: AppLanguage, ready: Boolean): String = when (language) {
    AppLanguage.ARABIC -> if (ready) "وصلت إلى نهاية السياسة. اختر الموافقة أو الرفض." else "مرّر إلى نهاية سياسة الخصوصية لتفعيل الخيارين."
    AppLanguage.GERMAN -> if (ready) "Ende erreicht. Du kannst zustimmen oder ablehnen." else "Scrolle bis zum Ende, um beide Optionen freizuschalten."
    AppLanguage.FRENCH -> if (ready) "Fin atteinte. Choisissez accepter ou refuser." else "Faites défiler jusqu'en bas pour activer les deux choix."
    AppLanguage.SPANISH -> if (ready) "Has llegado al final. Elige aceptar o rechazar." else "Desplázate hasta el final para activar ambas opciones."
    AppLanguage.PORTUGUESE_BRAZIL -> if (ready) "Fim da política. Escolha aceitar ou recusar." else "Role até o fim para ativar as duas opções."
    AppLanguage.TURKISH -> if (ready) "Sonuna ulaştınız. Kabul veya reddet seçin." else "Her iki seçeneği açmak için sona kaydırın."
    AppLanguage.RUSSIAN -> if (ready) "Конец политики. Можно согласиться или отказаться." else "Прокрутите до конца, чтобы открыть оба варианта."
    AppLanguage.HINDI -> if (ready) "नीति पूरी हुई। स्वीकार या अस्वीकार चुनें।" else "दोनों विकल्प चालू करने के लिए अंत तक स्क्रॉल करें।"
    AppLanguage.INDONESIAN -> if (ready) "Sudah sampai akhir. Pilih setuju atau tolak." else "Gulir sampai akhir untuk mengaktifkan kedua pilihan."
    AppLanguage.JAPANESE -> if (ready) "最後まで確認しました。同意または拒否を選択してください。" else "最後までスクロールすると両方のボタンが有効になります。"
    AppLanguage.CHINESE_SIMPLIFIED -> if (ready) "已阅读到末尾。请选择同意或拒绝。" else "滚动至底部后即可启用两个选项。"
    AppLanguage.ENGLISH -> if (ready) "End reached. Choose Agree or Decline." else "Scroll to the bottom to enable both choices."
}

// Dark embedded rendering only; the public English policy HTML is unchanged.
private const val EMBEDDED_POLICY_STYLE = """
<style>
:root, html, body { color-scheme: dark !important; background: #0A1B2C !important; color: #E8F1FB !important; }
.wrap { padding: 12px 12px 36px !important; }
header { background: #122D45 !important; color: #F4F9FF !important; border: 1px solid #31516C !important; padding: 20px 18px !important; }
header p { color: #DAE6F2 !important; }
main { background: #0F263B !important; color: #E1EBF6 !important; border: 1px solid #2E4C67 !important; padding: 20px 18px !important; }
h2, .ar h2 { color: #9AD8FC !important; }
.note, .ar { background: #193849 !important; color: #E9F1F9 !important; border-color: #31846B !important; }
a { color: #8CD4FF !important; }
footer { color: #A4B6C9 !important; }
code { color: #A8E0FF !important; }
</style>
"""

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
