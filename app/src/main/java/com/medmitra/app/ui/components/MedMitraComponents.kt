package com.medmitra.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import com.medmitra.app.ui.theme.MedMitraColors
import com.medmitra.app.ui.localization.Text
import com.medmitra.app.ui.localization.AppText
import androidx.compose.ui.platform.LocalContext

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun FrostedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .shadow(4.dp, CardShape, ambientColor = Color(0x18264C3A), spotColor = Color(0x18264C3A))
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(Color(0xF7FFFFFF), Color(0xE8E7F5EC), Color(0xFFF4FAF6))))
            .border(BorderStroke(1.dp, Color(0xFFD1E5D9)), CardShape)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun PlainCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = if (onClick == null) modifier else modifier.clickable(role = Role.Button, onClick = onClick)
    Column(
        modifier = clickModifier
            .shadow(2.dp, CardShape, ambientColor = Color(0x14264C3A), spotColor = Color(0x14264C3A))
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(MedMitraColors.Surface, Color(0xFFF0F7F2))))
            .border(BorderStroke(1.dp, MedMitraColors.Divider.copy(alpha = .9f)), CardShape)
            .padding(13.dp),
        content = content
    )
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = MedMitraColors.MintDark, modifier: Modifier = Modifier, size: Int = 42) {
    Box(
        modifier = modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .10f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * .48).dp))
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MedMitraColors.Text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text(action, color = MedMitraColors.MintDark, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PageHeader(title: String, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = AppText.translate(context, "Back"), tint = MedMitraColors.Text)
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MedMitraColors.Text, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun PrimaryAction(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MedMitraColors.MintDark, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SoftPill(text: String, tint: Color = MedMitraColors.MintDark, background: Color = MedMitraColors.MintPale) {
    Text(
        text,
        color = tint,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
fun InfoRow(icon: ImageVector, title: String, detail: String, tint: Color = MedMitraColors.MintDark) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, size = 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MedMitraColors.Text)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MedMitraColors.TextSecondary)
        }
    }
}

@Composable
fun CenteredFeatureIcon(icon: ImageVector, tint: Color = MedMitraColors.MintDark) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(66.dp).clip(CircleShape).background(tint.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
    }
}
