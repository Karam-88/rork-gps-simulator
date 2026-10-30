package com.rork.gpssimulator.i18n

import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException

/** Avoid exposing raw DNS, HTTP and network exception details in route errors. */
fun localizedRouteError(error: Throwable, language: AppLanguage): String {
    val networkFailure = generateSequence(error) { it.cause }.any {
        it is UnknownHostException || it is ConnectException || it is SocketTimeoutException ||
            it.message.orEmpty().contains("Unable to resolve host", ignoreCase = true) ||
            it.message.orEmpty().contains("timeout", ignoreCase = true)
    }
    if (networkFailure) return when (language) {
        AppLanguage.ARABIC -> "لا يوجد اتصال بالإنترنت. تحقق من اتصالك ثم حاول حساب المسار مجددًا."
        AppLanguage.GERMAN -> "Keine Internetverbindung. Prüfe deine Verbindung und versuche die Route erneut zu berechnen."
        AppLanguage.FRENCH -> "Pas de connexion Internet. Vérifiez votre connexion et réessayez de calculer l’itinéraire."
        AppLanguage.SPANISH -> "Sin conexión a Internet. Comprueba la conexión y vuelve a calcular la ruta."
        AppLanguage.PORTUGUESE_BRAZIL -> "Sem conexão com a internet. Verifique a conexão e tente calcular a rota novamente."
        AppLanguage.TURKISH -> "İnternet bağlantısı yok. Bağlantınızı kontrol edip rotayı tekrar hesaplayın."
        AppLanguage.RUSSIAN -> "Нет подключения к интернету. Проверьте соединение и повторите расчёт маршрута."
        AppLanguage.HINDI -> "इंटरनेट कनेक्शन नहीं है। कनेक्शन जाँचें और मार्ग फिर से बनाएँ।"
        AppLanguage.INDONESIAN -> "Tidak ada koneksi internet. Periksa koneksi dan coba hitung rute lagi."
        AppLanguage.JAPANESE -> "インターネットに接続されていません。接続を確認してルートを再計算してください。"
        AppLanguage.CHINESE_SIMPLIFIED -> "没有网络连接。请检查连接并重新计算路线。"
        AppLanguage.ENGLISH -> "No internet connection. Check your connection and try calculating the route again."
        else -> "No internet connection. Check your connection and try calculating the route again."
    }
    val unsupported = error.message.orEmpty().let { it.contains("waypoint", true) || it.contains("road or path", true) }
    return when (language) {
        AppLanguage.ARABIC -> if (unsupported) "إحدى نقاط المسار غير مدعومة. عدّلها وحاول مجددًا." else "تعذر حساب المسار. تحقق من النقاط أو اتصال الخادم وحاول مجددًا."
        AppLanguage.GERMAN -> if (unsupported) "Ein Wegpunkt wird nicht unterstützt. Bitte ändere ihn." else "Die Route konnte nicht berechnet werden. Bitte versuche es erneut."
        else -> if (unsupported) "A waypoint is not supported. Adjust it and try again." else "Could not calculate the route. Please try again."
    }
}
