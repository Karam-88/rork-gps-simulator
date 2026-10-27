package com.rork.gpssimulator.i18n

/** Voluntary review request copy. Kept separate from the large UI table
 * so every supported locale has complete, consistent dialog text. */
data class ReviewPromptCopy(
    val title: String,
    val body: String,
    val rate: String,
    val later: String,
    val noAds: String,
    val free: String,
)

fun reviewPromptCopy(language: AppLanguage): ReviewPromptCopy = when (language) {
    AppLanguage.ENGLISH -> ReviewPromptCopy(
        "Enjoying GPS Simulator?",
        "GPS Simulator is free and ad-free. Your review helps us improve it. Thank you for your support!",
        "Rate app", "Maybe later",
        "No ads", "100% free",
    )
    AppLanguage.ARABIC -> ReviewPromptCopy(
        "هل أعجبك GPS Simulator؟",
        "تطبيق GPS Simulator مجاني وبدون إعلانات. تقييمك يساعدنا على تحسين التطبيق وتطويره. شكرًا لدعمك!",
        "تقييم التطبيق", "ربما لاحقًا",
        "بدون إعلانات", "مجاني 100%",
    )
    AppLanguage.GERMAN -> ReviewPromptCopy(
        "Gefällt dir GPS Simulator?",
        "GPS Simulator ist kostenlos und werbefrei. Mit deiner Bewertung hilfst du uns, die App weiter zu verbessern. Vielen Dank!",
        "App bewerten", "Vielleicht später",
        "Keine Werbung", "100 % kostenlos",
    )
    AppLanguage.FRENCH -> ReviewPromptCopy(
        "Vous aimez GPS Simulator ?",
        "GPS Simulator est gratuit et sans publicité. Votre avis nous aide à améliorer l’application. Merci de votre soutien !",
        "Évaluer l’application", "Plus tard",
        "Sans publicité", "100 % gratuit",
    )
    AppLanguage.SPANISH -> ReviewPromptCopy(
        "¿Te gusta GPS Simulator?",
        "GPS Simulator es gratis y no tiene anuncios. Tu reseña nos ayuda a mejorar la aplicación. ¡Gracias por tu apoyo!",
        "Valorar la aplicación", "Quizás más tarde",
        "Sin anuncios", "100 % gratis",
    )
    AppLanguage.PORTUGUESE_BRAZIL -> ReviewPromptCopy(
        "Está gostando do GPS Simulator?",
        "O GPS Simulator é gratuito e sem anúncios. Sua avaliação nos ajuda a melhorar o aplicativo. Obrigado pelo apoio!",
        "Avaliar aplicativo", "Talvez mais tarde",
        "Sem anúncios", "100% gratuito",
    )
    AppLanguage.TURKISH -> ReviewPromptCopy(
        "GPS Simulator'ı beğendiniz mi?",
        "GPS Simulator ücretsizdir ve reklam içermez. Değerlendirmeniz uygulamayı geliştirmemize yardımcı olur. Desteğiniz için teşekkürler!",
        "Uygulamayı değerlendir", "Belki sonra",
        "Reklamsız", "%100 ücretsiz",
    )
    AppLanguage.RUSSIAN -> ReviewPromptCopy(
        "Нравится GPS Simulator?",
        "GPS Simulator — бесплатное приложение без рекламы. Ваш отзыв помогает нам его улучшать. Спасибо за поддержку!",
        "Оценить приложение", "Может быть позже",
        "Без рекламы", "100% бесплатно",
    )
    AppLanguage.HINDI -> ReviewPromptCopy(
        "क्या आपको GPS Simulator पसंद है?",
        "GPS Simulator मुफ़्त और विज्ञापन रहित है। आपकी समीक्षा ऐप को बेहतर बनाने में मदद करती है। आपके समर्थन के लिए धन्यवाद!",
        "ऐप को रेट करें", "शायद बाद में",
        "विज्ञापन रहित", "100% मुफ़्त",
    )
    AppLanguage.INDONESIAN -> ReviewPromptCopy(
        "Suka dengan GPS Simulator?",
        "GPS Simulator gratis dan tanpa iklan. Ulasan Anda membantu kami meningkatkan aplikasi ini. Terima kasih atas dukungan Anda!",
        "Beri rating", "Mungkin nanti",
        "Tanpa iklan", "100% gratis",
    )
    AppLanguage.JAPANESE -> ReviewPromptCopy(
        "GPS Simulator は気に入りましたか？",
        "GPS Simulator は広告なしで無料で使えます。レビューはアプリの改善に役立ちます。ご協力ありがとうございます！",
        "アプリを評価", "後で",
        "広告なし", "完全無料",
    )
    AppLanguage.CHINESE_SIMPLIFIED -> ReviewPromptCopy(
        "喜欢 GPS Simulator 吗？",
        "GPS Simulator 完全免费且无广告。您的评价有助于我们改进应用。感谢您的支持！",
        "评价应用", "稍后再说",
        "无广告", "完全免费",
    )
}
