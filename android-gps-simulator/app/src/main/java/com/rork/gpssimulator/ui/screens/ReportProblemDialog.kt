package com.rork.gpssimulator.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.i18n.AppLanguage

private data class ReportText(
    val title: String,
    val hint: String,
    val description: String,
    val send: String,
    val cancel: String,
    val missingEmail: String,
    val subject: String,
)

private fun textFor(language: AppLanguage): ReportText = when (language) {
    AppLanguage.ARABIC -> ReportText("الإبلاغ عن مشكلة", "صف المشكلة التي واجهتك", "لن يتم إرفاق موقعك أو سجل المواقع. يمكنك مراجعة البريد قبل إرساله.", "فتح البريد", "إلغاء", "لم يتم العثور على تطبيق بريد إلكتروني", "تقرير مشكلة في GPS Simulator")
    AppLanguage.GERMAN -> ReportText("Problem melden", "Beschreibe das Problem", "Standortdaten werden nicht angehängt. Prüfe die E-Mail vor dem Senden.", "E-Mail öffnen", "Abbrechen", "Keine E-Mail-App gefunden", "GPS Simulator: Problembericht")
    AppLanguage.FRENCH -> ReportText("Signaler un problème", "Décrivez le problème", "Aucune donnée de localisation n’est jointe. Vérifiez l’e-mail avant l’envoi.", "Ouvrir l’e-mail", "Annuler", "Aucune application de messagerie trouvée", "GPS Simulator : signalement")
    AppLanguage.SPANISH -> ReportText("Informar de un problema", "Describe el problema", "No se adjuntan datos de ubicación. Revisa el correo antes de enviarlo.", "Abrir correo", "Cancelar", "No se encontró una aplicación de correo", "GPS Simulator: informe de problema")
    AppLanguage.PORTUGUESE_BRAZIL -> ReportText("Relatar um problema", "Descreva o problema", "Nenhum dado de localização será anexado. Revise o e-mail antes de enviar.", "Abrir e-mail", "Cancelar", "Nenhum aplicativo de e-mail encontrado", "GPS Simulator: relato de problema")
    AppLanguage.TURKISH -> ReportText("Sorun bildir", "Sorunu açıklayın", "Konum verileri eklenmez. Göndermeden önce e-postayı inceleyin.", "E-postayı aç", "İptal", "E-posta uygulaması bulunamadı", "GPS Simulator sorun bildirimi")
    AppLanguage.RUSSIAN -> ReportText("Сообщить о проблеме", "Опишите проблему", "Данные о местоположении не прикрепляются. Проверьте письмо перед отправкой.", "Открыть почту", "Отмена", "Почтовое приложение не найдено", "GPS Simulator: сообщение о проблеме")
    AppLanguage.HINDI -> ReportText("समस्या रिपोर्ट करें", "समस्या बताएँ", "लोकेशन डेटा संलग्न नहीं होगा। भेजने से पहले ईमेल जाँचें।", "ईमेल खोलें", "रद्द करें", "ईमेल ऐप नहीं मिला", "GPS Simulator समस्या रिपोर्ट")
    AppLanguage.INDONESIAN -> ReportText("Laporkan masalah", "Jelaskan masalahnya", "Data lokasi tidak dilampirkan. Tinjau email sebelum mengirim.", "Buka email", "Batal", "Aplikasi email tidak ditemukan", "GPS Simulator: laporan masalah")
    AppLanguage.JAPANESE -> ReportText("問題を報告", "問題の内容を入力", "位置情報は添付されません。送信前にメールを確認できます。", "メールを開く", "キャンセル", "メールアプリが見つかりません", "GPS Simulator：問題の報告")
    AppLanguage.CHINESE_SIMPLIFIED -> ReportText("报告问题", "描述您遇到的问题", "不会附加位置数据。您可以在发送前检查邮件。", "打开邮件", "取消", "未找到邮件应用", "GPS Simulator 问题反馈")
    AppLanguage.ENGLISH -> ReportText("Report a problem", "Describe what went wrong", "No location data is attached. You can review the email before sending.", "Open email", "Cancel", "No email app found", "GPS Simulator problem report")
}

/** Draft-only reporting: never sends anything without user interaction. */
@Composable
fun ReportProblemDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val copy = remember(language) { textFor(language) }
    var description by remember { mutableStateOf("") }
    var mailError by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalLayoutDirection provides if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(copy.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 2000) description = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 220.dp),
                    placeholder = { Text(copy.hint) },
                    minLines = 3,
                    maxLines = 7,
                )
                Text(copy.description)
                if (mailError) Text(copy.missingEmail, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                enabled = true,
                onClick = {
                    val version = runCatching {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "unknown"
                    val body = buildString {
                        append(description.trim().ifBlank { "No description provided." })
                        append("\n\n--- Device information (provided with your review) ---\n")
                        append("App version: ").append(version).append('\n')
                        append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                        append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                    }
                    // ACTION_SEND with MIME type preserves the body in Gmail.
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "message/rfc822"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("gps.simulator.support@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, copy.subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    try {
                        context.startActivity(intent)
                        onDismiss()
                    } catch (_: ActivityNotFoundException) {
                        mailError = true
                    } catch (_: SecurityException) {
                        mailError = true
                    }
                },
            ) { Text(copy.send) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(copy.cancel) } },
    )
    }
}
