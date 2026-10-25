package com.rork.gpssimulator.i18n

/** Voluntary review request copy. Kept separate from the large UI table
 * so every supported locale has complete, consistent dialog text. */
data class ReviewPromptCopy(
    val title: String,
    val body: String,
    val rate: String,
    val later: String,
)

fun reviewPromptCopy(language: AppLanguage): ReviewPromptCopy = when (language) {
    AppLanguage.ENGLISH -> ReviewPromptCopy(
        "Enjoying GPS Simulator?",
        "GPS Simulator is free. Your review helps us improve it. Thank you for your support!",
        "Rate app", "Maybe later",
    )
    AppLanguage.ARABIC -> ReviewPromptCopy(
        "هل يعجبك GPS Simulator؟",
        "التطبيق مجاني، وتقييمك يساعدنا على تطويره وتحسينه. شكرًا لدعمك!",
        "تقييم التطبيق", "ربما لاحقًا",
    )
    AppLanguage.GERMAN -> ReviewPromptCopy(
        "Gefällt dir GPS Simulator?",
        "GPS Simulator ist kostenlos. Deine Bewertung hilft uns, die App zu verbessern. Vielen Dank!",
        "App bewerten", "Vielleicht später",
    )
    AppLanguage.FRENCH -> ReviewPromptCopy(
        "Vous aimez GPS Simulator ?",
        "GPS Simulator est gratuit. Votre avis nous aide à améliorer l’application. Merci de votre soutien !",
        "Évaluer l’application", "Plus tard",
    )
    AppLanguage.SPANISH -> ReviewPromptCopy(
        "¿Te gusta GPS Simulator?",
        "GPS Simulator es gratis. Tu reseña nos ayuda a mejorar la aplicación. ¡Gracias por tu apoyo!",
        "Valorar la aplicación", "Quizás más tarde",
    )
    AppLanguage.PORTUGUESE_BRAZIL -> ReviewPromptCopy(
        "Está gostando do GPS Simulator?",
        "O GPS Simulator é gratuito. Sua avaliação nos ajuda a melhorar o aplicativo. Obrigado pelo apoio!",
        "Avaliar aplicativo", "Talvez mais tarde",
    )
    AppLanguage.TURKISH -> ReviewPromptCopy(
        "GPS Simulator'ı beğendiniz mi?",
        "GPS Simulator ücretsizdir. Değerlendirmeniz uygulamayı geliştirmemize yardımcı olur. Desteğiniz için teşekkürler!",
        "Uygulamayı değerlendir", "Belki sonra",
    )
    AppLanguage.RUSSIAN -> ReviewPromptCopy(
        "Нравится GPS Simulator?",
        "GPS Simulator — бесплатное приложение. Ваш отзыв помогает нам его улучшать. Спасибо за поддержку!",
        "Оценить приложение", "Может быть позже",
    )
    AppLanguage.HINDI -> ReviewPromptCopy(
        "क्या आपको GPS Simulator पसंद है?",
        "GPS Simulator मुफ़्त है। आपकी समीक्षा ऐप को बेहतर बनाने में मदद करती है। आपके समर्थन के लिए धन्यवाद!",
        "ऐप को रेट करें", "शायद बाद में",
    )
    AppLanguage.INDONESIAN -> ReviewPromptCopy(
        "Suka dengan GPS Simulator?",
        "GPS Simulator gratis. Ulasan Anda membantu kami meningkatkan aplikasi ini. Terima kasih atas dukungan Anda!",
        "Beri rating", "Mungkin nanti",
    )
    AppLanguage.JAPANESE -> ReviewPromptCopy(
        "GPS Simulator は気に入りましたか？",
        "GPS Simulator は無料です。レビューはアプリの改善に役立ちます。ご協力ありがとうございます！",
        "アプリを評価", "後で",
    )
    AppLanguage.CHINESE_SIMPLIFIED -> ReviewPromptCopy(
        "喜欢 GPS Simulator 吗？",
        "GPS Simulator 是免费应用。您的评价有助于我们改进应用。感谢您的支持！",
        "评价应用", "稍后再说",
    )
}
