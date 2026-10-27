package com.rork.gpssimulator.ui.rating

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rork.gpssimulator.R
import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.reviewPromptCopy

@Composable
fun PremiumRatingDialog(language: AppLanguage, darkTheme: Boolean, onRate: () -> Unit, onLater: () -> Unit) {
    // Scope RTL to this dialog; do not change the app's established map/navigation layout.
    CompositionLocalProvider(LocalLayoutDirection provides
        if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            PremiumRatingContent(language, darkTheme, onRate, onLater)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PremiumRatingContent(language: AppLanguage, darkTheme: Boolean, onRate: () -> Unit, onLater: () -> Unit) {
    val copy = reviewPromptCopy(language)
    val configuration = LocalConfiguration.current
    val title = if (darkTheme) Color(0xFFF7F8FC) else Color(0xFF101A2D)
    val secondary = if (darkTheme) Color(0xFFBBCBE8) else Color(0xFF657187)
    val buttonColors = if (darkTheme) listOf(Color(0xFF6557F8), Color(0xFF4837E5))
        else listOf(Color(0xFF2985FF), Color(0xFF0063F5))
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp).widthIn(max = 400.dp)
            .fillMaxWidth().heightIn(max = (configuration.screenHeightDp - 48).coerceAtLeast(160).dp),
        shape = RoundedCornerShape(26.dp),
        color = if (darkTheme) Color(0xFF151F2E) else Color.White,
        border = if (darkTheme) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF50679F)) else null,
        shadowElevation = 16.dp,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (darkTheme) Box(Modifier.size(154.dp).background(
                    Brush.radialGradient(listOf(Color(0x30FFCC45), Color.Transparent))))
                // Decorative art: the title communicates purpose; TalkBack skips duplicate decoration.
                Image(painterResource(R.drawable.rating_map_star), contentDescription = null,
                    modifier = Modifier.size(if (configuration.screenHeightDp < 480) 116.dp else 156.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingBadge(copy.noAds, true, darkTheme)
                RatingBadge(copy.free, false, darkTheme)
            }
            Spacer(Modifier.height(20.dp))
            Text(copy.title, color = title, fontSize = 22.sp, lineHeight = 28.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() })
            Spacer(Modifier.height(12.dp))
            Text(copy.body, color = secondary, fontSize = 16.sp, lineHeight = 23.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRate,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(buttonColors)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(copy.rate, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(copy.later, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (darkTheme) Color(0xFFA3B9E8) else Color(0xFF0060E5))
            }
        }
    }
}

@Composable
private fun RatingBadge(text: String, shield: Boolean, dark: Boolean) {
    val foreground = when {
        shield && dark -> Color(0xFF8BF0CD)
        shield -> Color(0xFF127638)
        dark -> Color(0xFFFFE58A)
        else -> Color(0xFF835300)
    }
    val background = when {
        shield && dark -> Color(0xFF123D36)
        shield -> Color(0xFFE7FAEF)
        dark -> Color(0xFF403715)
        else -> Color(0xFFFFF4CC)
    }
    Surface(shape = RoundedCornerShape(50), color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (shield) Color(0xFF50CC91) else Color(0xFFF2C343))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (shield) Icons.Default.VerifiedUser else Icons.Default.CardGiftcard,
                contentDescription = null, tint = foreground, modifier = Modifier.size(22.dp))
            Text(text, color = foreground, fontSize = 13.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}
