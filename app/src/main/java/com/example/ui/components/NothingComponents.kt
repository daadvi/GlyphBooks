package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CmfOrange
import com.example.ui.theme.MatrixAmber
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.NothingRed

/**
 * Segmented progress bar inspired by Nothing CMF audio sliders and API usage meters.
 */
@Composable
fun SegmentedDotBar(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    totalSegments: Int = 18,
    activeColor: Color = MatrixGreen,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
    height: Dp = 6.dp
) {
    val clamped = progress.coerceIn(0f, 1f)
    val activeCount = (clamped * totalSegments).toInt()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSegments) {
            val isActive = i < activeCount
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isActive) activeColor else inactiveColor)
            )
        }
    }
}

/**
 * Technical Glyphs & HUD Header
 */
@Composable
fun NothingSectionHeader(
    tag: String,
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(NothingRed)
            )
            Text(
                text = tag.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "//",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * CMF Minimal Outlined Badge
 */
@Composable
fun NothingBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    backgroundColor: Color = Color.Transparent
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * Minimalist Nothing Card Container
 */
@Composable
fun NothingCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        content()
    }
}

/**
 * Tactile Button with Nothing/CMF Style
 */
@Composable
fun NothingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    testTag: String = "nothing_button"
) {
    val containerColor = if (isPrimary) CmfOrange else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isPrimary) Color.White else MaterialTheme.colorScheme.onSurface
    val borderStroke = if (isPrimary) null else MaterialTheme.colorScheme.outline

    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .testTag(testTag)
            .then(
                if (borderStroke != null) Modifier.border(1.dp, borderStroke, RoundedCornerShape(24.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

/**
 * Clean Text Input Field
 */
@Composable
fun NothingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    testTag: String = "nothing_input"
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            leadingIcon = if (leadingIcon != null) {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else null,
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )
    }
}
