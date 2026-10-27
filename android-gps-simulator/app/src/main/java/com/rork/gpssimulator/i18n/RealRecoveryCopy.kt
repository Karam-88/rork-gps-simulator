package com.rork.gpssimulator.i18n

internal enum class RealRecoveryStatus { ACQUIRING, RESTORED, PERMISSION, DISABLED, PROVIDER_ERROR, TIMEOUT, CLEANUP_ERROR }

internal fun realRecoveryMessage(language: AppLanguage, status: RealRecoveryStatus): String {
    val messages = when (language) {
        AppLanguage.ENGLISH -> listOf(
            "Acquiring a fresh real location…",
            "Real location restored.",
            "Allow location permission to restore your real location.",
            "Device location is disabled. Enable it to receive real fixes.",
            "Could not register real location updates. Check location permissions and try again.",
            "No fresh real fix yet. Still listening; GPS may be unavailable indoors.",
            "Mock provider cleanup failed. Real-location recovery could not start.",
        )
        AppLanguage.ARABIC -> listOf(
            "جارٍ الحصول على موقعك الحقيقي…",
            "تمت استعادة الموقع الحقيقي.",
            "اسمح بإذن الموقع لاستعادة موقعك الحقيقي.",
            "الموقع معطّل على الجهاز. فعّله لاستقبال الموقع الحقيقي.",
            "تعذر بدء تحديثات الموقع الحقيقي. تحقق من أذونات الموقع وحاول مجددًا.",
            "لم يصل موقع حقيقي حديث بعد. ما زلنا نحاول؛ قد لا تتوفر إشارة GPS داخل المباني.",
            "تعذرت إزالة مزوّد الموقع الوهمي. لم تبدأ استعادة الموقع الحقيقي.",
        )
        AppLanguage.GERMAN -> listOf(
            "Aktueller echter Standort wird ermittelt…",
            "Echter Standort wiederhergestellt.",
            "Erlaube den Standortzugriff, um deinen echten Standort zu ermitteln.",
            "Der Gerätestandort ist deaktiviert. Bitte aktiviere ihn.",
            "Standortaktualisierungen konnten nicht gestartet werden. Prüfe die Berechtigungen und versuche es erneut.",
            "Noch kein aktueller echter Standort. Die Suche läuft weiter; in Gebäuden fehlt möglicherweise das GPS-Signal.",
            "Der simulierte Anbieter konnte nicht entfernt werden. Standortwiederherstellung nicht gestartet.",
        )
        AppLanguage.FRENCH -> listOf(
            "Recherche de votre position réelle…",
            "Position réelle rétablie.",
            "Autorisez l’accès à la position pour rétablir votre position réelle.",
            "La localisation est désactivée. Activez-la sur votre appareil.",
            "Impossible de démarrer la localisation. Vérifiez les autorisations et réessayez.",
            "Pas encore de position réelle récente. La recherche continue ; le GPS peut être indisponible en intérieur.",
            "Impossible de supprimer le fournisseur simulé. La récupération n’a pas démarré.",
        )
        AppLanguage.SPANISH -> listOf(
            "Obteniendo tu ubicación real…",
            "Ubicación real restablecida.",
            "Permite el acceso a la ubicación para recuperar tu ubicación real.",
            "La ubicación del dispositivo está desactivada. Actívala.",
            "No se pudieron iniciar las actualizaciones. Comprueba los permisos e inténtalo de nuevo.",
            "Aún no hay una ubicación real reciente. Seguimos buscando; puede faltar señal GPS en interiores.",
            "No se pudo eliminar el proveedor simulado. No se inició la recuperación.",
        )
        AppLanguage.PORTUGUESE_BRAZIL -> listOf(
            "Obtendo sua localização real…",
            "Localização real restaurada.",
            "Permita o acesso à localização para restaurar sua localização real.",
            "A localização do dispositivo está desativada. Ative-a.",
            "Não foi possível iniciar as atualizações. Verifique as permissões e tente novamente.",
            "Ainda sem localização real recente. A busca continua; o GPS pode não funcionar em ambientes fechados.",
            "Não foi possível remover o provedor simulado. A recuperação não foi iniciada.",
        )
        AppLanguage.TURKISH -> listOf(
            "Gerçek konumunuz alınıyor…",
            "Gerçek konum geri yüklendi.",
            "Gerçek konumunuz için konum iznini verin.",
            "Cihaz konumu kapalı. Lütfen açın.",
            "Konum güncellemeleri başlatılamadı. İzinleri kontrol edip tekrar deneyin.",
            "Henüz güncel gerçek konum yok. Arama sürüyor; kapalı alanlarda GPS sinyali olmayabilir.",
            "Sahte konum sağlayıcısı kaldırılamadı. Kurtarma başlatılamadı.",
        )
        AppLanguage.RUSSIAN -> listOf(
            "Определяется реальное местоположение…",
            "Реальное местоположение восстановлено.",
            "Разрешите доступ к местоположению для восстановления реальных координат.",
            "Геолокация на устройстве отключена. Включите её.",
            "Не удалось запустить обновления. Проверьте разрешения и повторите попытку.",
            "Свежих реальных координат пока нет. Поиск продолжается; в помещении GPS может быть недоступен.",
            "Не удалось удалить тестовый источник. Восстановление не началось.",
        )
        AppLanguage.HINDI -> listOf(
            "वास्तविक स्थान प्राप्त किया जा रहा है…",
            "वास्तविक स्थान बहाल हो गया।",
            "वास्तविक स्थान बहाल करने के लिए स्थान की अनुमति दें।",
            "डिवाइस का स्थान बंद है। इसे चालू करें।",
            "स्थान अपडेट शुरू नहीं हो सके। अनुमतियाँ जाँचें और फिर कोशिश करें।",
            "अभी नया वास्तविक स्थान नहीं मिला। खोज जारी है; इमारत के अंदर GPS उपलब्ध नहीं हो सकता।",
            "नकली स्थान प्रदाता हटाया नहीं जा सका। बहाली शुरू नहीं हुई।",
        )
        AppLanguage.INDONESIAN -> listOf(
            "Mencari lokasi asli terbaru…",
            "Lokasi asli dipulihkan.",
            "Izinkan akses lokasi untuk memulihkan lokasi asli.",
            "Lokasi perangkat dinonaktifkan. Aktifkan untuk menerima lokasi asli.",
            "Pembaruan lokasi gagal dimulai. Periksa izin dan coba lagi.",
            "Belum ada lokasi asli terbaru. Pencarian berlanjut; GPS mungkin tidak tersedia di dalam ruangan.",
            "Penyedia lokasi simulasi gagal dihapus. Pemulihan belum dimulai.",
        )
        AppLanguage.JAPANESE -> listOf(
            "現在の実際の位置を取得中…",
            "実際の位置を復元しました。",
            "実際の位置を取得するには位置情報の権限を許可してください。",
            "端末の位置情報が無効です。有効にしてください。",
            "位置情報の更新を開始できませんでした。権限を確認して再試行してください。",
            "最新の実際の位置をまだ取得できていません。取得を継続します。屋内ではGPSが利用できない場合があります。",
            "模擬位置プロバイダを削除できず、位置の復元を開始できませんでした。",
        )
        AppLanguage.CHINESE_SIMPLIFIED -> listOf(
            "正在获取最新真实位置…",
            "真实位置已恢复。",
            "请授予位置权限以恢复真实位置。",
            "设备定位已关闭。请开启定位。",
            "无法启动位置更新。请检查权限后重试。",
            "尚未收到最新真实位置，仍在继续获取；室内可能没有 GPS 信号。",
            "无法移除模拟位置提供程序，未能开始恢复真实位置。",
        )
    }
    return messages[status.ordinal]
}
