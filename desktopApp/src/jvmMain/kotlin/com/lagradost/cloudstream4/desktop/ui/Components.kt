package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream4.desktop.res.Res
import com.lagradost.cloudstream4.desktop.res.ic_baseline_keyboard_arrow_left_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_keyboard_arrow_right_24
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Colors from the Android app (res/values/colors.xml) that Material has no slot for. */
object CsColors {
    val typeBg = Color(0xFF3700B3)
    val typeText = Color(0xFFBEC8FF)
    val dubBg = Color(0xFF3B65F5)
    val dubText = Color(0xFF121950)
    val subBg = Color(0xFFF53B66)
    val subText = Color(0xFF571711)
    val search = Color(0xFF303135)
    val shadow = Color(0xFF111111)
}

@Composable
fun CsIcon(
    res: DrawableResource,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    contentDescription: String? = null,
) {
    Icon(painterResource(res), contentDescription, modifier.size(size), tint = tint)
}

@Composable
fun RemoteImage(
    url: String?,
    headers: Map<String, String>?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = MaterialTheme.colorScheme.surface,
) {
    if (url.isNullOrBlank()) {
        Box(modifier.background(placeholder))
        return
    }
    val context = LocalPlatformContext.current
    val request = remember(url, headers) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .apply {
                if (!headers.isNullOrEmpty()) {
                    httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (k, v) -> set(k, v) } }.build())
                }
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.background(placeholder),
    )
}

/** Small colored label, like Android's TypeButton / DubButton / SubButton styles. */
@Composable
fun Badge(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

private fun SearchResponse.dubStatuses(): Set<DubStatus> =
    (this as? AnimeSearchResponse)?.dubStatus.orEmpty()

/** Card from home_result_grid.xml: poster, title over a bottom shadow, badges. */
@Composable
fun PosterCard(
    item: SearchResponse,
    horizontal: Boolean = false,
    width: Dp = if (horizontal) 230.dp else 128.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.05f else 1f)
    val shape = RoundedCornerShape(10.dp)

    Box(
        Modifier
            .width(width)
            .height(if (horizontal) width * 9f / 16f else width * 180f / 114f)
            .scale(scale)
            .clip(shape)
            .then(if (hovered) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground), shape) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        RemoteImage(item.posterUrl, item.posterHeaders, item.name, Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(60.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, CsColors.shadow.copy(alpha = 0.95f))))
        )
        Text(
            item.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp)
        )
        item.quality?.let {
            Badge(it.name.uppercase(), CsColors.typeBg, CsColors.typeText, Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        val dubs = item.dubStatuses()
        Column(Modifier.align(Alignment.TopEnd).padding(5.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (DubStatus.Dubbed in dubs) Badge("Dub", CsColors.dubBg, CsColors.dubText)
            if (DubStatus.Subbed in dubs) Badge("Sub", CsColors.subBg, CsColors.subText)
            item.score?.let { Badge("★ $it", Color.Black.copy(alpha = 0.7f), Color.White) }
        }
    }
}

/** Row from homepage_parent.xml: bold title with an arrow, horizontal posters. */
@Composable
fun PosterRow(
    title: String,
    items: List<SearchResponse>,
    horizontal: Boolean = false,
    onClick: (SearchResponse) -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).hoverable(interaction)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 10.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            CsIcon(Res.drawable.ic_baseline_keyboard_arrow_right_24, size = 22.dp)
        }
        Box {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(items, key = { i, it -> "$i|${it.url}" }) { _, item ->
                    PosterCard(item, horizontal) { onClick(item) }
                }
            }
            // Netflix-like side arrows, shown on hover
            if (hovered && state.canScrollBackward) Box(Modifier.align(Alignment.CenterStart)) {
                RowArrow(Res.drawable.ic_baseline_keyboard_arrow_left_24) {
                    scope.launch { state.animateScrollToItem(maxOf(0, state.firstVisibleItemIndex - 5)) }
                }
            }
            if (hovered && state.canScrollForward) Box(Modifier.align(Alignment.CenterEnd)) {
                RowArrow(Res.drawable.ic_baseline_keyboard_arrow_right_24) {
                    scope.launch { state.animateScrollToItem(state.firstVisibleItemIndex + 5) }
                }
            }
        }
    }
}

@Composable
private fun RowArrow(icon: DrawableResource, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 6.dp).size(44.dp).clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) { CsIcon(icon, size = 30.dp, tint = Color.White) }
}

@Composable
fun CenteredLoading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun MessageBox(title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            action?.invoke()
        }
    }
}

/** Outlined tag chip like result_tag.xml. */
@Composable
fun Tag(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val fg = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
        contentColor = fg,
        shape = RoundedCornerShape(50),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier
    ) {
        Text(text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
fun TagRow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** CloudStream "WhiteButton": white pill with dark text and an optional icon. */
@Composable
fun CsWhiteButton(text: String, icon: DrawableResource? = null, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    CsButtonBase(text, icon, MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.surface, null, modifier, enabled, onClick)
}

/** CloudStream "BlackButton": dark pill with a light outline. */
@Composable
fun CsBlackButton(text: String, icon: DrawableResource? = null, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    CsButtonBase(
        text, icon, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground,
        BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)), modifier, enabled, onClick
    )
}

@Composable
private fun CsButtonBase(
    text: String, icon: DrawableResource?, bg: Color, fg: Color, border: BorderStroke?,
    modifier: Modifier, enabled: Boolean, onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(
        color = if (hovered) bg.copy(alpha = 0.85f) else bg,
        contentColor = fg,
        shape = RoundedCornerShape(8.dp),
        border = border,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            if (icon != null) CsIcon(icon, size = 20.dp, tint = fg)
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = fg, maxLines = 1)
        }
    }
}

/** Icon + small label under it (home_preview_info / bookmark style). */
@Composable
fun IconLabelButton(icon: DrawableResource, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CsIcon(icon, size = 24.dp, tint = tint)
        Text(label, color = tint, fontSize = 12.sp)
    }
}

/** Round icon button (player and toolbars). */
@Composable
fun RoundIconButton(icon: DrawableResource, size: Dp = 44.dp, iconSize: Dp = 26.dp, tint: Color = Color.White, background: Color = Color.Transparent, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) { CsIcon(icon, size = iconSize, tint = tint) }
}
