package com.example.test_dialer.ui.recents.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.SamsungSmsBlue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CubeFaceData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun InteractiveCubeSection(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rotationYAnim = remember { Animatable(0f) }

    val cubeSizeDp = 110.dp
    val cubeSizePx = with(density) { cubeSizeDp.toPx() }
    val radiusPx = cubeSizePx / 2f

    val faces = listOf(
        CubeFaceData("Быстрый набор", "Телефон", Icons.Default.Phone, SamsungGreen),
        CubeFaceData("Сообщения", "SMS", Icons.AutoMirrored.Filled.Message, SamsungSmsBlue),
        CubeFaceData("Избранные", "Контакты", Icons.Default.Star, Color(0xFF8E24AA)),
        CubeFaceData("Инфо", "Поддержка", Icons.Default.Info, Color(0xFFFF8A65))
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(cubeSizeDp)
                .clip(RoundedCornerShape(22.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val current = rotationYAnim.value
                                val target = (current / 90f).roundToInt() * 90f
                                rotationYAnim.animateTo(
                                    targetValue = target,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                val current = rotationYAnim.value
                                val target = (current / 90f).roundToInt() * 90f
                                rotationYAnim.animateTo(target)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newRot = rotationYAnim.value + (dragAmount * 0.7f)
                                rotationYAnim.snapTo(newRot)
                            }
                        }
                    )
                }
        ) {
            val rotY = rotationYAnim.value

            faces.forEachIndexed { index, face ->
                val baseAngle = index * 90f
                var faceAngle = (rotY + baseAngle) % 360f
                if (faceAngle > 180f) faceAngle -= 360f
                if (faceAngle < -180f) faceAngle += 360f

                // Draw face if visible (-90°..+90°)
                if (faceAngle in -89.9f..89.9f) {
                    val rad = Math.toRadians(faceAngle.toDouble())
                    val translationXVal = (radiusPx * Math.sin(rad)).toFloat()

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.rotationY = faceAngle
                                this.translationX = translationXVal
                                this.cameraDistance = 14f * density.density
                                this.transformOrigin = TransformOrigin(0.5f, 0.5f)
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = face.color,
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = face.icon,
                                    contentDescription = face.title,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = face.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = face.subtitle,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
