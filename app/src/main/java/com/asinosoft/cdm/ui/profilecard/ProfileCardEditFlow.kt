package com.asinosoft.cdm.ui.profilecard

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.asinosoft.cdm.R
import com.asinosoft.cdm.data.repository.ProfileCard
import com.asinosoft.cdm.data.repository.ProfileCardRepository
import com.asinosoft.cdm.ui.theme.MissedRed
import com.asinosoft.cdm.ui.theme.SamsungGreen
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

enum class ProfileMediaTarget { CARD, IMAGE }

private sealed interface FlowStep {
    data object Source : FlowStep
    data object Loading : FlowStep
    /** [base] is the editor to return to when the photo was picked from inside it. */
    data class ChooseTargets(
        val media: ProfileCard,
        val preview: ImageBitmap,
        val focus: ProfileMediaTarget,
        val base: Edit? = null
    ) : FlowStep
    /** Editor pages: the card (when [card] != null) and the circular picture (when [avatar] != null). */
    data class Edit(
        val card: ProfileCard?,
        val cardPreview: ImageBitmap?,
        val avatar: ProfileCard?,
        val avatarPreview: ImageBitmap?,
        val initialPage: ProfileMediaTarget,
        val avatarIsNew: Boolean,
        /** Freshly imported draft files; deleted unless the card ends up using one. */
        val newMediaPaths: List<String>
    ) : FlowStep
}

/** Wraps an existing contact photo so the picture page can pan/zoom it like imported media. */
private fun ImageBitmap.asAvatarCard() = ProfileCard(mediaPath = "", isVideo = false, mediaWidth = width, mediaHeight = height)

private enum class EditorSheet { TEXT, EFFECT }

private const val EDITOR_MAX_SIDE = 2048

private val TEXT_COLORS = listOf(
    ProfileCard.DEFAULT_TEXT_COLOR,
    ProfileCard.DARK_TEXT_COLOR,
    0xFFFFD54F.toInt(),
    0xFF80D8FF.toInt(),
    0xFFFF8A80.toInt(),
    0xFFB9F6CA.toInt()
)

/**
 * Samsung-like flow started from the "Profile card" / "Picture" tiles:
 * pick or capture media, choose where to apply a picture, then adjust it in the editor.
 */
@Composable
fun ProfileCardEditFlow(
    tile: ProfileMediaTarget?,
    currentCard: ProfileCard?,
    currentAvatar: ImageBitmap?,
    contactName: String,
    onCardChanged: (ProfileCard?) -> Unit,
    onAvatarChanged: (Bitmap) -> Unit,
    onAvatarRemoved: () -> Unit,
    onClose: () -> Unit,
    /** When set, a photo picked for the empty "Picture" tile is returned square-cropped instead of opening the editor. */
    onImagePicked: ((Bitmap) -> Unit)? = null,
    /** Open the editor on [tile] even when it is empty (showing its "+") instead of the source picker. */
    openEditorWhenEmpty: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowSize = LocalWindowInfo.current.containerSize
    val screenAspect = if (windowSize.height > 0) windowSize.width.toFloat() / windowSize.height else 9f / 19.5f

    var step by remember { mutableStateOf<FlowStep?>(null) }
    var captureUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraVideo by remember { mutableStateOf<Boolean?>(null) }

    fun close() {
        step = null
        onClose()
    }

    /** Opens the editor with the new media on [targets] and whatever is already set on the other page. */
    fun openEditor(initialPage: ProfileMediaTarget, newMedia: ProfileCard? = null, newPreview: ImageBitmap? = null, targets: List<ProfileMediaTarget> = emptyList()) {
        step = FlowStep.Loading
        scope.launch {
            val cardFromNew = newMedia != null && ProfileMediaTarget.CARD in targets
            val avatarFromNew = newMedia != null && ProfileMediaTarget.IMAGE in targets
            val card = if (cardFromNew) newMedia else currentCard
            val cardPreview = if (cardFromNew) newPreview else currentCard?.let {
                ProfileCardRepository.loadBitmap(it, EDITOR_MAX_SIDE)?.asImageBitmap()
            }
            step = FlowStep.Edit(
                card = card,
                cardPreview = cardPreview,
                avatar = newMedia?.takeIf { avatarFromNew }?.copy(scale = 1f, offsetX = 0f, offsetY = 0f)
                    ?: currentAvatar?.asAvatarCard(),
                avatarPreview = if (avatarFromNew) newPreview else currentAvatar,
                initialPage = initialPage,
                avatarIsNew = avatarFromNew,
                newMediaPaths = listOfNotNull(newMedia?.mediaPath)
            )
        }
    }

    /** Editor snapshot to return to while media for its empty page is being picked. */
    var addBase by remember { mutableStateOf<FlowStep.Edit?>(null) }
    var addTarget by remember { mutableStateOf<ProfileMediaTarget?>(null) }

    fun cancelPick() {
        val base = addBase
        addBase = null
        addTarget = null
        if (base != null) step = base else close()
    }

    fun applyToEditor(
        base: FlowStep.Edit,
        targets: List<ProfileMediaTarget>,
        page: ProfileMediaTarget,
        media: ProfileCard,
        preview: ImageBitmap
    ) {
        var edit = base
        if (ProfileMediaTarget.CARD in targets) edit = edit.copy(card = media, cardPreview = preview)
        if (ProfileMediaTarget.IMAGE in targets) edit = edit.copy(
            avatar = media.copy(scale = 1f, offsetX = 0f, offsetY = 0f),
            avatarPreview = preview,
            avatarIsNew = true
        )
        step = edit.copy(initialPage = page, newMediaPaths = edit.newMediaPaths + media.mediaPath)
    }

    LaunchedEffect(tile) {
        val hasContent = when (tile) {
            ProfileMediaTarget.CARD -> currentCard != null
            ProfileMediaTarget.IMAGE -> currentAvatar != null
            null -> false
        }
        when {
            tile == null -> step = null
            hasContent || openEditorWhenEmpty -> openEditor(initialPage = tile)
            else -> step = FlowStep.Source
        }
    }

    fun handlePicked(uri: Uri?, isVideo: Boolean) {
        if (uri == null) return cancelPick()
        step = FlowStep.Loading
        scope.launch {
            val media = ProfileCardRepository.importMedia(context, uri, isVideo)
            if (uri == captureUri) {
                capturePath?.let { File(it).delete() }
                captureUri = null
                capturePath = null
            }
            val preview = media?.let { ProfileCardRepository.loadBitmap(it, EDITOR_MAX_SIDE)?.asImageBitmap() }
            val base = addBase
            val target = addTarget
            when {
                media == null || preview == null -> {
                    Toast.makeText(context, R.string.error_load_photo, Toast.LENGTH_SHORT).show()
                    cancelPick()
                }
                base != null && target != null -> {
                    addBase = null
                    addTarget = null
                    if (isVideo) {
                        applyToEditor(base, listOf(ProfileMediaTarget.CARD), ProfileMediaTarget.CARD, media, preview)
                    } else {
                        step = FlowStep.ChooseTargets(media, preview, focus = target, base = base)
                    }
                }
                onImagePicked != null && !isVideo && tile == ProfileMediaTarget.IMAGE -> {
                    val square = cropSquare(
                        preview.asAndroidBitmap(),
                        media.copy(scale = 1f, offsetX = 0f, offsetY = 0f)
                    )
                    File(media.mediaPath).delete()
                    close()
                    onImagePicked(square)
                }
                isVideo -> openEditor(ProfileMediaTarget.CARD, media, preview, listOf(ProfileMediaTarget.CARD))
                else -> step = FlowStep.ChooseTargets(media, preview, focus = tile ?: ProfileMediaTarget.CARD)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        handlePicked(uri, uri != null && ProfileCardRepository.isVideo(context, uri))
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        handlePicked(captureUri.takeIf { saved }, isVideo = false)
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        handlePicked(captureUri.takeIf { saved }, isVideo = true)
    }

    fun launchCamera(video: Boolean) {
        val file = ProfileCardRepository.newCaptureFile(context, if (video) "mp4" else "jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureUri = uri
        capturePath = file.absolutePath
        if (video) videoLauncher.launch(uri) else photoLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val video = pendingCameraVideo
        pendingCameraVideo = null
        if (granted && video != null) {
            launchCamera(video)
        } else {
            Toast.makeText(context, R.string.profile_camera_denied, Toast.LENGTH_SHORT).show()
            cancelPick()
        }
    }

    fun requestCamera(video: Boolean) {
        step = null
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera(video)
        } else {
            pendingCameraVideo = video
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when (val current = step) {
        FlowStep.Source -> {
            // The circular picture can't be a video
            val imageOnly = (addTarget ?: tile) == ProfileMediaTarget.IMAGE
            MediaSourceSheet(
                allowVideo = !imageOnly,
                onGallery = {
                    step = null
                    galleryLauncher.launch(
                        PickVisualMediaRequest(
                            if (imageOnly) ActivityResultContracts.PickVisualMedia.ImageOnly
                            else ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        )
                    )
                },
                onTakePhoto = { requestCamera(video = false) },
                onRecordVideo = { requestCamera(video = true) },
                onDismiss = ::cancelPick
            )
        }

        FlowStep.Loading -> Dialog(onDismissRequest = {}) {
            CircularProgressIndicator(color = SamsungGreen)
        }

        is FlowStep.ChooseTargets -> TargetSheet(
            preview = current.preview,
            initialTargets = if (current.base != null) setOf(current.focus) else ProfileMediaTarget.entries.toSet(),
            onConfirm = { targets ->
                val page = current.focus.takeIf { it in targets } ?: targets.first()
                val base = current.base
                if (base != null) {
                    applyToEditor(base, targets, page, current.media, current.preview)
                } else {
                    openEditor(page, current.media, current.preview, targets)
                }
            },
            onDismiss = {
                File(current.media.mediaPath).delete()
                val base = current.base
                if (base != null) step = base else close()
            }
        )

        is FlowStep.Edit -> ProfileCardEditor(
            edit = current,
            contactName = contactName,
            screenAspect = screenAspect,
            onDone = { card, avatar ->
                if (card != null && card != currentCard) onCardChanged(card)
                avatar?.let(onAvatarChanged)
                current.newMediaPaths.filter { it != card?.mediaPath }.forEach { File(it).delete() }
                close()
            },
            onDelete = { target ->
                when (target) {
                    ProfileMediaTarget.CARD -> onCardChanged(null)
                    ProfileMediaTarget.IMAGE -> onAvatarRemoved()
                }
                current.newMediaPaths.forEach { File(it).delete() }
                close()
            },
            onAdd = { target, snapshot ->
                addBase = snapshot
                addTarget = target
                step = FlowStep.Source
            },
            onDismiss = ::close
        )

        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaSourceSheet(
    allowVideo: Boolean,
    onGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRecordVideo: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            SourceRow(Icons.Default.PhotoLibrary, stringResource(R.string.profile_source_gallery), onClick = onGallery)
            SourceRow(Icons.Default.PhotoCamera, stringResource(R.string.profile_source_photo), onClick = onTakePhoto)
            if (allowVideo) {
                SourceRow(Icons.Default.Videocam, stringResource(R.string.profile_source_video), onClick = onRecordVideo)
            }
        }
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSheet(
    preview: ImageBitmap,
    initialTargets: Set<ProfileMediaTarget>,
    onConfirm: (List<ProfileMediaTarget>) -> Unit,
    onDismiss: () -> Unit
) {
    var forCard by remember { mutableStateOf(ProfileMediaTarget.CARD in initialTargets) }
    var forImage by remember { mutableStateOf(ProfileMediaTarget.IMAGE in initialTargets) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.profile_target_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TargetOption(preview, RoundedCornerShape(16.dp), stringResource(R.string.profile_card_title), forCard) {
                    forCard = !forCard
                }
                TargetOption(preview, CircleShape, stringResource(R.string.profile_image_title), forImage) {
                    forImage = !forImage
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                enabled = forCard || forImage,
                onClick = {
                    onConfirm(
                        listOfNotNull(
                            ProfileMediaTarget.CARD.takeIf { forCard },
                            ProfileMediaTarget.IMAGE.takeIf { forImage }
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.action_done), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TargetOption(
    preview: ImageBitmap,
    shape: Shape,
    label: String,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(8.dp)
    ) {
        Image(
            bitmap = preview,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(shape)
        )
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    BorderStroke(1.5.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline),
                    CircleShape
                )
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileCardEditor(
    edit: FlowStep.Edit,
    contactName: String,
    screenAspect: Float,
    onDone: (card: ProfileCard?, avatar: Bitmap?) -> Unit,
    onDelete: (ProfileMediaTarget) -> Unit,
    onAdd: (target: ProfileMediaTarget, snapshot: FlowStep.Edit) -> Unit,
    onDismiss: () -> Unit
) {
    val targets = ProfileMediaTarget.entries
    // An empty page shows the "+" button, so its state only needs a placeholder value.
    val placeholder = edit.card ?: edit.avatar
        ?: ProfileCard(mediaPath = "", isVideo = false, mediaWidth = 1, mediaHeight = 1)
    val cardState = remember(edit) { mutableStateOf(edit.card ?: placeholder) }
    val avatarState = remember(edit) { mutableStateOf(edit.avatar ?: placeholder) }
    fun hasContent(target: ProfileMediaTarget) = when (target) {
        ProfileMediaTarget.CARD -> edit.card != null
        ProfileMediaTarget.IMAGE -> edit.avatar != null
    }
    fun snapshot() = edit.copy(
        card = edit.card?.let { cardState.value },
        avatar = edit.avatar?.let { avatarState.value }
    )
    val pagerState = rememberPagerState(initialPage = targets.indexOf(edit.initialPage).coerceAtLeast(0)) { targets.size }
    val preview = if (targets.getOrNull(pagerState.currentPage) == ProfileMediaTarget.IMAGE) {
        edit.avatarPreview ?: edit.cardPreview
    } else {
        edit.cardPreview ?: edit.avatarPreview
    }
    var sheet by remember { mutableStateOf<EditorSheet?>(null) }
    val currentTarget = targets[pagerState.currentPage.coerceIn(targets.indices)]
    val scope = rememberCoroutineScope()
    /** Non-null while the trim panel is open; second = 0 until the duration is known. */
    var trimRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var trimPlaying by remember { mutableStateOf(false) }
    var videoPositionMs by remember { mutableLongStateOf(0L) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(48.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(
                            if (currentTarget == ProfileMediaTarget.CARD) R.string.profile_card_title
                            else R.string.profile_image_title
                        ),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )
                    TextButton(
                        onClick = {
                            val avatarChanged = edit.avatar != null &&
                                    (edit.avatarIsNew || avatarState.value != edit.avatar)
                            val avatar = if (avatarChanged) {
                                edit.avatarPreview?.let { cropSquare(it.asAndroidBitmap(), avatarState.value) }
                            } else null
                            onDone(cardState.value.takeIf { edit.card != null }, avatar)
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.action_done),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(Modifier.size(48.dp)) {
                        var menuOpen by remember { mutableStateOf(false) }
                        var confirmTarget by remember { mutableStateOf<ProfileMediaTarget?>(null) }
                        if (hasContent(currentTarget)) {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
                            }
                        }
                        OneUiPopupMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            items = listOf(
                                stringResource(
                                    if (currentTarget == ProfileMediaTarget.CARD) R.string.profile_card_delete
                                    else R.string.profile_image_delete
                                ) to { confirmTarget = currentTarget }
                            )
                        )
                        confirmTarget?.let { target ->
                            OneUiConfirmDialog(
                                title = stringResource(
                                    if (target == ProfileMediaTarget.CARD) R.string.profile_card_delete_confirm
                                    else R.string.profile_image_delete_confirm
                                ),
                                confirmText = stringResource(R.string.action_delete),
                                onConfirm = {
                                    confirmTarget = null
                                    onDelete(target)
                                },
                                onDismiss = { confirmTarget = null }
                            )
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = trimRange == null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    val target = targets[page]
                    when {
                        !hasContent(target) -> AddMediaPage(
                            target = target,
                            screenAspect = screenAspect,
                            onClick = { onAdd(target, snapshot()) }
                        )
                        target == ProfileMediaTarget.CARD -> CardPage(
                            state = cardState,
                            preview = edit.cardPreview,
                            contactName = contactName,
                            screenAspect = screenAspect,
                            videoRange = trimRange ?: (cardState.value.trimStartMs to cardState.value.trimEndMs),
                            videoPlaying = trimRange == null || trimPlaying,
                            onVideoPosition = if (trimRange != null) ({ videoPositionMs = it }) else null
                        )
                        else -> ImagePage(avatarState, edit.avatarPreview)
                    }
                }

                val range = trimRange
                if (range != null) {
                    VideoTrimPanel(
                        path = cardState.value.mediaPath,
                        range = range,
                        positionMs = videoPositionMs,
                        playing = trimPlaying,
                        onPlayingChange = { trimPlaying = it },
                        onRangeChange = { trimRange = it },
                        onCancel = { trimRange = null },
                        onDone = {
                            cardState.value = cardState.value.copy(trimStartMs = range.first, trimEndMs = range.second)
                            trimRange = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                } else {
                    // Dots and the action bar also accept horizontal swipes to switch pages.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(targets.size) {
                                var total = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { total = 0f },
                                    onDragEnd = {
                                        if (abs(total) > 48.dp.toPx()) {
                                            val next = pagerState.currentPage + if (total < 0) 1 else -1
                                            scope.launch { pagerState.animateScrollToPage(next.coerceIn(targets.indices)) }
                                        }
                                    }
                                ) { change, dx ->
                                    change.consume()
                                    total += dx
                                }
                            }
                    ) {
                        if (targets.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                targets.indices.forEach { index ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                                            .padding(10.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Color.White.copy(alpha = if (index == pagerState.currentPage) 1f else 0.4f)
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (currentTarget == ProfileMediaTarget.CARD && hasContent(currentTarget)) {
                                EditorAction(Icons.Default.TextFields, stringResource(R.string.profile_text)) {
                                    sheet = EditorSheet.TEXT
                                }
                                EditorAction(Icons.Default.Contrast, stringResource(R.string.profile_effect)) {
                                    sheet = EditorSheet.EFFECT
                                }
                                if (cardState.value.isVideo) {
                                    EditorAction(Icons.Default.ContentCut, stringResource(R.string.profile_trim)) {
                                        trimPlaying = false
                                        trimRange = cardState.value.trimStartMs to cardState.value.trimEndMs
                                    }
                                }
                            }
                            if (hasContent(currentTarget)) {
                                EditorAction(Icons.Default.PhotoLibrary, stringResource(R.string.profile_replace)) {
                                    onAdd(currentTarget, snapshot())
                                }
                            }
                        }
                    }
                }
            }
        }

        when (sheet) {
            EditorSheet.TEXT -> TextSheet(cardState) { sheet = null }
            EditorSheet.EFFECT -> EffectSheet(cardState) { sheet = null }
            null -> Unit
        }
    }
}

/** Empty page with a "+" that starts picking media for [target]; keeps the page's frame shape. */
@Composable
private fun AddMediaPage(target: ProfileMediaTarget, screenAspect: Float, onClick: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val shape = if (target == ProfileMediaTarget.CARD) RoundedCornerShape(28.dp) else CircleShape
        val frame = if (target == ProfileMediaTarget.CARD) {
            Modifier.aspectRatio(screenAspect, matchHeightConstraintsFirst = true)
        } else {
            Modifier.size(minOf(maxWidth, maxHeight) * 0.82f)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = frame
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)), shape)
                .clickable(onClick = onClick)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(
                        if (target == ProfileMediaTarget.CARD) R.string.profile_card_title else R.string.profile_image_title
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
private fun CardPage(
    state: MutableState<ProfileCard>,
    preview: ImageBitmap?,
    contactName: String,
    screenAspect: Float,
    videoRange: Pair<Long, Long>,
    videoPlaying: Boolean,
    onVideoPosition: ((Long) -> Unit)?
) {
    val card = state.value
    val windowHeight = LocalWindowInfo.current.containerSize.height.coerceAtLeast(1)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            Modifier
                .aspectRatio(screenAspect, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(28.dp))
                .then(if (card.isVideo) Modifier else Modifier.profileCardGestures(state, screenAspect))
        ) {
            val ratio = constraints.maxHeight / windowHeight.toFloat()
            ProfileCardMedia(
                card = card,
                bitmap = preview,
                modifier = Modifier.fillMaxSize(),
                videoRange = videoRange,
                videoPlaying = videoPlaying,
                onVideoPosition = onVideoPosition
            )
            CardPreviewOverlay(contactName, card, ratio)
        }
    }
}

/** Scaled-down copy of the call screen layout so the preview matches what the call will show. */
@Composable
private fun CardPreviewOverlay(contactName: String, card: ProfileCard, ratio: Float) {
    val textColor = Color(card.textColor)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = (110 * ratio).dp, bottom = (70 * ratio).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = contactName,
            color = textColor,
            fontSize = (card.textSizeSp * ratio).sp,
            lineHeight = (card.textSizeSp * ratio * 1.2f).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = profileTextShadow(textColor)),
            modifier = Modifier.padding(horizontal = (24 * ratio).dp)
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (48 * ratio).dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PreviewCallButton(Icons.Default.Call, SamsungGreen, ratio)
            PreviewCallButton(Icons.Default.CallEnd, MissedRed, ratio)
        }
    }
}

@Composable
private fun PreviewCallButton(icon: ImageVector, color: Color, ratio: Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size((72 * ratio).dp)
            .clip(CircleShape)
            .background(color)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((32 * ratio).dp))
    }
}

@Composable
private fun ImagePage(state: MutableState<ProfileCard>, preview: ImageBitmap?) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight) * 0.82f
        Box(
            Modifier
                .size(side)
                .clip(CircleShape)
                .profileCardGestures(state, 1f)
        ) {
            ProfileCardMedia(state.value, preview, Modifier.fillMaxSize(), dimmed = false)
        }
    }
}

@Composable
private fun EditorAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextSheet(state: MutableState<ProfileCard>, onDismiss: () -> Unit) {
    val card = state.value
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(stringResource(R.string.profile_text_color), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TEXT_COLORS.forEach { color ->
                    val selected = card.textColor == color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                BorderStroke(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) SamsungGreen else MaterialTheme.colorScheme.outline
                                ),
                                CircleShape
                            )
                            .clickable { state.value = state.value.copy(textColor = color) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.profile_text_size), fontWeight = FontWeight.Bold)
            Slider(
                value = card.textSizeSp,
                onValueChange = { state.value = state.value.copy(textSizeSp = it) },
                valueRange = 22f..52f,
                colors = SliderDefaults.colors(thumbColor = SamsungGreen, activeTrackColor = SamsungGreen)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectSheet(state: MutableState<ProfileCard>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(stringResource(R.string.profile_dim), fontWeight = FontWeight.Bold)
            Slider(
                value = state.value.dim,
                onValueChange = { state.value = state.value.copy(dim = it) },
                valueRange = 0f..0.7f,
                colors = SliderDefaults.colors(thumbColor = SamsungGreen, activeTrackColor = SamsungGreen)
            )
        }
    }
}

/** Samsung One UI style confirmation: rounded sheet, centered title, flat text buttons split by a divider. */
@Composable
private fun OneUiConfirmDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val textColor = if (dark) Color(0xFFFAFAFA) else Color(0xFF1A1A1A)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = if (dark) Color(0xFF252525) else Color.White,
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_cancel), fontSize = 16.sp, color = textColor)
                    }
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(textColor.copy(alpha = 0.15f))
                    )
                    TextButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text(
                            confirmText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935)
                        )
                    }
                }
            }
        }
    }
}

/** Samsung One UI style overflow popup: large rounded corners, flat items without icons. */
@Composable
private fun OneUiPopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<Pair<String, () -> Unit>>
) {
    val dark = isSystemInDarkTheme()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(x = (-8).dp, y = 4.dp),
        shape = RoundedCornerShape(26.dp),
        containerColor = if (dark) Color(0xFF252525) else Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .widthIn(min = 200.dp)
            .padding(vertical = 6.dp)
    ) {
        items.forEach { (label, action) ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        color = if (dark) Color(0xFFFAFAFA) else Color(0xFF1A1A1A)
                    )
                },
                onClick = {
                    onDismiss()
                    action()
                },
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.heightIn(min = 52.dp)
            )
        }
    }
}
