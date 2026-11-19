package com.openlauncher.app.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openlauncher.app.BuildConfig
import com.openlauncher.app.R
import com.openlauncher.app.service.MediaNotificationListenerService
import com.openlauncher.app.ui.theme.LocalCockpitColors

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    accent: Color,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentStep by rememberSaveable { mutableStateOf(0) }
    var locationGranted by remember { mutableStateOf(false) }
    var mediaGranted by remember { mutableStateOf(false) }

    val checkPermissions = {
        locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        mediaGranted = MediaNotificationListenerService.isNotificationListenerEnabled(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Trigger initial check
    LaunchedEffect(Unit) {
        checkPermissions()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = granted
        if (granted) {
            currentStep = 2 // Auto-advance to next step
        }
    }

    val colors = LocalCockpitColors.current
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
    ) {
        // Aesthetic glowing background orb
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.15f), Color.Transparent),
                        radius = 600f
                    )
                )
        )

        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Left branding pane ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .background(colors.dockSurface)
                    .padding(32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_puma_logo),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 2.sp,
                        fontSize = 15.sp
                    )
                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Step indicator list
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StepItem(0, stringResource(R.string.onboarding_step_intro), currentStep)
                    StepItem(1, stringResource(R.string.onboarding_step_location), currentStep)
                    StepItem(2, stringResource(R.string.onboarding_step_media), currentStep)
                    StepItem(3, stringResource(R.string.onboarding_step_ready), currentStep)
                }

                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = colors.textSecondary.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }

            // Vertical separator line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.hairlineBorder)
            )

            // ── Right content wizard ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .padding(48.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Active Step Content
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            fadeIn() + slideInHorizontally { it / 5 } togetherWith
                            fadeOut() + slideOutHorizontally { -it / 5 }
                        },
                        label = "step_transition"
                    ) { step ->
                        when (step) {
                            0 -> IntroStep(accent)
                            1 -> LocationStep(accent, locationGranted, onGrant = {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            })
                            2 -> MediaStep(accent, mediaGranted, onGrant = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                            })
                            3 -> FinalStep(accent, onSetDefault = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                }
                            })
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // UNIFIED WIZARD FOOTER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button (left aligned)
                    if (currentStep > 0) {
                        TextButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.onboarding_back), color = colors.textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    // Next / Finish button (right aligned)
                    val isPrimary = when (currentStep) {
                        0 -> true
                        1 -> locationGranted
                        2 -> mediaGranted
                        3 -> true
                        else -> true
                    }

                    val nextButtonLabel = stringResource(
                        when (currentStep) {
                            0 -> R.string.onboarding_get_started
                            1 -> if (locationGranted) R.string.onboarding_continue else R.string.onboarding_skip
                            2 -> if (mediaGranted) R.string.onboarding_continue else R.string.onboarding_skip
                            3 -> R.string.onboarding_finish
                            else -> R.string.onboarding_continue
                        }
                    )

                    val nextButtonIcon = if (currentStep == 3) Icons.Default.Check else Icons.Default.ArrowForward

                    if (isPrimary) {
                        Button(
                            onClick = {
                                if (currentStep < 3) {
                                    currentStep++
                                } else {
                                    onComplete()
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(nextButtonLabel, color = onAccent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(nextButtonIcon, null, tint = onAccent, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                currentStep++
                            },
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(nextButtonLabel, color = colors.textPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(nextButtonIcon, null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepItem(stepIndex: Int, title: String, currentStep: Int) {
    val colors = LocalCockpitColors.current
    val active = stepIndex == currentStep
    val completed = stepIndex < currentStep
    val tint = when {
        active -> MaterialTheme.colorScheme.primary
        completed -> Color(0xFF44AA44)
        else -> colors.hairlineBorder
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint)
            )
        }
        Text(
            text = title,
            fontSize = 11.sp,
            color = if (active) colors.textPrimary else colors.textSecondary,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun IntroStep(accent: Color) {
    val colors = LocalCockpitColors.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_intro_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            letterSpacing = 2.sp,
            fontSize = 20.sp
        )
        Text(
            text = stringResource(R.string.onboarding_intro_desc),
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BulletItem(Icons.Default.Navigation, stringResource(R.string.onboarding_bullet_nav_title), stringResource(R.string.onboarding_bullet_nav_desc))
            BulletItem(Icons.Default.Palette, stringResource(R.string.onboarding_bullet_dash_title), stringResource(R.string.onboarding_bullet_dash_desc))
            BulletItem(Icons.Default.MusicNote, stringResource(R.string.onboarding_bullet_media_title), stringResource(R.string.onboarding_bullet_media_desc))
        }
    }
}

@Composable
private fun LocationStep(accent: Color, isGranted: Boolean, onGrant: () -> Unit) {
    val colors = LocalCockpitColors.current
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val statusBg = if (isGranted) {
        Color(0xFF44AA44).copy(alpha = if (colors.isLight) 0.12f else 0.18f)
    } else {
        colors.dangerContent.copy(alpha = if (colors.isLight) 0.10f else 0.16f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_location_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            letterSpacing = 2.sp,
            fontSize = 20.sp
        )
        Text(
            text = stringResource(R.string.onboarding_location_desc),
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(statusBg)
                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(4.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF44AA44) else colors.dangerContent,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = stringResource(if (isGranted) R.string.onboarding_perm_granted else R.string.onboarding_perm_required),
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(if (isGranted) R.string.onboarding_location_active else R.string.onboarding_location_disabled),
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (!isGranted) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.LocationOn, null, tint = onAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_grant_access), color = onAccent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MediaStep(accent: Color, isGranted: Boolean, onGrant: () -> Unit) {
    val colors = LocalCockpitColors.current
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val statusBg = if (isGranted) {
        Color(0xFF44AA44).copy(alpha = if (colors.isLight) 0.12f else 0.18f)
    } else {
        colors.dangerContent.copy(alpha = if (colors.isLight) 0.10f else 0.16f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_media_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            letterSpacing = 2.sp,
            fontSize = 20.sp
        )
        Text(
            text = stringResource(R.string.onboarding_media_desc),
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(statusBg)
                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(4.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF44AA44) else colors.dangerContent,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = stringResource(if (isGranted) R.string.onboarding_media_granted else R.string.onboarding_media_required),
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(if (isGranted) R.string.onboarding_media_active else R.string.onboarding_media_disabled),
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (!isGranted) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.VolumeUp, null, tint = onAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_enable_media), color = onAccent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FinalStep(accent: Color, onSetDefault: () -> Unit) {
    val colors = LocalCockpitColors.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_ready_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            letterSpacing = 2.sp,
            fontSize = 20.sp
        )
        Text(
            text = stringResource(R.string.onboarding_ready_desc),
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSetDefault,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairlineBorder),
            modifier = Modifier.height(44.dp)
        ) {
            Icon(Icons.Default.Home, null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_set_default), color = colors.textPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BulletItem(icon: ImageVector, title: String, desc: String) {
    val colors = LocalCockpitColors.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Column {
            Text(title, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(desc, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
