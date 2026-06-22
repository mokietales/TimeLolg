package com.mokie.timelogdemo.ui.starmap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.data.TrackDao
import com.mokie.timelogdemo.data.TrackTree
import com.mokie.timelogdemo.data.observeTrackTree
import com.mokie.timelogdemo.ui.TrackingSession
import com.mokie.timelogdemo.ui.components.EmptyState
import com.mokie.timelogdemo.ui.components.TabularNumFeature
import com.mokie.timelogdemo.ui.theme.trackColor
import com.mokie.timelogdemo.ui.util.TimeFormat
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/** Scale the graph opens at, before animating to the fitted size. */
private const val INTRO_SCALE = 0.45f

/**
 * Full-screen "Star map": an Obsidian-style network of the Track DAG. Each
 * track is a node sized by its rolled-up time; each parent→child inheritance
 * is an edge. Layout settles with a small force-directed simulation; the user
 * can pan, pinch-zoom, and tap a node to open its detail.
 */
@Composable
fun StarMapScreen(
    session: TrackingSession,
    trackDao: TrackDao,
    sessionDao: SessionDao,
    onOpenTrackDetail: (Long) -> Unit,
    onBack: () -> Unit
) {
    val tree by remember(trackDao, sessionDao) {
        observeTrackTree(trackDao, sessionDao)
    }.collectAsState(initial = null)
    val current = tree

    var selected by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "星图",
            subtitle = current?.let { "${it.tracks.size} 个主题 · ${it.edgeCountOrNull()}" },
            onBack = onBack
        )

        when {
            current == null -> Spacer(Modifier.fillMaxSize())
            current.tracks.isEmpty() -> EmptyState(
                title = "还没有主题",
                subtitle = "在「现在」页创建主题后即可查看关系网络。"
            )
            else -> GraphCanvas(
                tree = current,
                onTapNode = { id -> selected = id },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    val sel = selected
    if (sel != null && current != null && current.byId.containsKey(sel)) {
        NodeInfoDialog(
            tree = current,
            trackId = sel,
            session = session,
            onDismiss = { selected = null },
            onOpenDetail = {
                selected = null
                onOpenTrackDetail(sel)
            },
            onStart = {
                val name = current.byId[sel]?.name ?: return@NodeInfoDialog
                session.start(TrackingSession.TrackRef(sel, name))
                selected = null
                onBack()
            }
        )
    }
}

@Composable
private fun NodeInfoDialog(
    tree: TrackTree,
    trackId: Long,
    session: TrackingSession,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onStart: () -> Unit
) {
    val name = tree.byId[trackId]?.name ?: "—"
    val totalMs = tree.totalMs[trackId] ?: 0L
    val descCount = tree.descendantCount[trackId] ?: 0
    val parents = tree.parentsOf[trackId].orEmpty().mapNotNull { tree.byId[it]?.name }
    val children = tree.childrenOf[trackId].orEmpty().mapNotNull { tree.byId[it]?.name }
    val running = session.isActive

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(trackColor(trackId))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "累计时长",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (totalMs > 0L) TimeFormat.shortDuration(totalMs) else "暂无记录",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(14.dp))

                StatLine(label = "子主题", value = descCount.toString())
                if (parents.isNotEmpty()) {
                    StatLine(
                        label = "父主题",
                        value = parents.joinToString(", ")
                    )
                }
                if (children.isNotEmpty()) {
                    StatLine(
                        label = "子项",
                        value = children.joinToString(", ")
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Primary action: start a session on this track.
                Surface(
                    onClick = { if (!running) onStart() },
                    enabled = !running,
                    shape = RoundedCornerShape(14.dp),
                    color = if (running)
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircleOutline,
                            contentDescription = null,
                            tint = if (running)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "开始计时",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (running)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                if (running) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "已有进行中的记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "查看详情",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenDetail)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "关闭",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(86.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun TrackTree.edgeCountOrNull(): String {
    val edges = childrenOf.values.sumOf { it.size }
    return "$edges 条关联"
}

/** Minimal force-directed layout state over a fixed node/edge set. */
private class GraphSim(
    val n: Int,
    val ids: LongArray,
    val edges: List<IntArray>,
    val radii: FloatArray
) {
    val x = FloatArray(n)
    val y = FloatArray(n)
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)

    init {
        val r = 200.0
        for (i in 0 until n) {
            val ang = 2.0 * Math.PI * i / max(1, n)
            x[i] = (r * cos(ang)).toFloat() + (i % 3 - 1) * 6f
            y[i] = (r * sin(ang)).toFloat() + (i % 5 - 2) * 6f
        }
    }

    fun step(alpha: Float) {
        val kr = 14000f   // repulsion
        val len = 110f    // spring rest length
        val ks = 0.06f    // spring stiffness
        val g = 0.025f    // gravity to center
        val fx = FloatArray(n)
        val fy = FloatArray(n)

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                var dx = x[i] - x[j]
                var dy = y[i] - y[j]
                var d2 = dx * dx + dy * dy
                if (d2 < 0.01f) {
                    dx = (Math.random().toFloat() - 0.5f)
                    dy = (Math.random().toFloat() - 0.5f)
                    d2 = dx * dx + dy * dy + 0.01f
                }
                val d = sqrt(d2)
                val f = kr / d2
                val ux = dx / d
                val uy = dy / d
                fx[i] += ux * f; fy[i] += uy * f
                fx[j] -= ux * f; fy[j] -= uy * f
            }
        }

        for (e in edges) {
            val a = e[0]; val b = e[1]
            val dx = x[b] - x[a]
            val dy = y[b] - y[a]
            val d = max(sqrt(dx * dx + dy * dy), 0.01f)
            val f = ks * (d - len)
            val ux = dx / d; val uy = dy / d
            fx[a] += ux * f; fy[a] += uy * f
            fx[b] -= ux * f; fy[b] -= uy * f
        }

        for (i in 0 until n) {
            fx[i] -= x[i] * g
            fy[i] -= y[i] * g
            vx[i] = (vx[i] + fx[i] * alpha) * 0.85f
            vy[i] = (vy[i] + fy[i] * alpha) * 0.85f
            val sp = sqrt(vx[i] * vx[i] + vy[i] * vy[i])
            val cap = 40f
            if (sp > cap) { vx[i] *= cap / sp; vy[i] *= cap / sp }
            x[i] += vx[i]
            y[i] += vy[i]
        }
    }
}

@Composable
private fun GraphCanvas(
    tree: TrackTree,
    onTapNode: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val edgeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val bg = MaterialTheme.colorScheme.background

    // Structural signature: rebuild + re-run the sim only when nodes/edges change.
    val signature = remember(tree) {
        tree.tracks.joinToString(",") { it.id.toString() } + "|" +
            tree.childrenOf.entries.joinToString(";") { (k, v) ->
                "$k>${v.joinToString("/")}"
            }
    }

    val sim = remember(signature) {
        val ids = tree.tracks.map { it.id }
        val n = ids.size
        val index = ids.withIndex().associate { (i, id) -> id to i }
        val maxTotal = (tree.tracks.maxOfOrNull { tree.totalMs[it.id] ?: 0L } ?: 0L)
            .coerceAtLeast(1L)
        val radii = FloatArray(n) { i ->
            val t = tree.totalMs[ids[i]] ?: 0L
            val frac = sqrt(t.toDouble() / maxTotal.toDouble()).toFloat()
            6f + frac * 16f
        }
        val edges = mutableListOf<IntArray>()
        tree.childrenOf.forEach { (p, cs) ->
            val pi = index[p] ?: return@forEach
            cs.forEach { c -> index[c]?.let { ci -> edges.add(intArrayOf(pi, ci)) } }
        }
        GraphSim(n, ids.toLongArray(), edges, radii).also { s ->
            // Pre-settle synchronously so the layout is already spread out on the
            // first frame — no multi-second wait before the zoom-in can start.
            var alpha = 1f
            while (alpha > 0.03f) {
                s.step(alpha)
                alpha *= 0.985f
            }
        }
    }

    val isRoot = remember(signature) {
        val rootIds = tree.roots.map { it.id }.toSet()
        BooleanArray(sim.n) { sim.ids[it] in rootIds }
    }
    val names = remember(signature) {
        Array(sim.n) {
            val raw = tree.byId[sim.ids[it]]?.name ?: "—"
            if (raw.length > 16) raw.take(15) + "…" else raw
        }
    }
    // Categorical identity colour per node, so the network reads like a map
    // rather than a field of identical indigo dots.
    val nodeColors = (0 until sim.n).map { trackColor(sim.ids[it]) }

    val scope = rememberCoroutineScope()
    var canvasSize by remember(signature) { mutableStateOf(IntSize.Zero) }

    // Auto-fit view: animated scale + the world center we keep on screen.
    val scaleAnim = remember(signature) { Animatable(INTRO_SCALE) }
    var worldCenter by remember(signature) { mutableStateOf(Offset.Zero) }
    // Once the user pans/zooms, stop auto-centering and track their offset directly.
    var userAdjustedView by remember(signature) { mutableStateOf(false) }
    var manualOffset by remember(signature) { mutableStateOf(Offset.Zero) }

    // Layout is pre-settled, so zoom in as soon as the canvas has been measured.
    LaunchedEffect(canvasSize, signature) {
        if (canvasSize == IntSize.Zero || sim.n == 0 || userAdjustedView) return@LaunchedEffect
        val (fitScale, center) = computeFitToScreen(
            sim = sim,
            canvasSize = canvasSize,
            fillFraction = 0.7f
        )
        worldCenter = center
        scaleAnim.snapTo(INTRO_SCALE)
        scaleAnim.animateTo(
            targetValue = fitScale,
            animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
        )
    }

    fun effectiveOffset(s: Float): Offset =
        if (userAdjustedView) manualOffset
        else Offset(-worldCenter.x * s, -worldCenter.y * s)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .background(bg)
            .pointerInput(signature) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (!userAdjustedView) {
                        manualOffset = effectiveOffset(scaleAnim.value)
                        userAdjustedView = true
                    }
                    val newScale = (scaleAnim.value * zoom).coerceIn(0.3f, 4f)
                    scope.launch { scaleAnim.snapTo(newScale) }
                    manualOffset += pan
                }
            }
            .pointerInput(sim) {
                detectTapGestures { tap ->
                    val s = scaleAnim.value
                    val eff = effectiveOffset(s)
                    val cx = size.width / 2f + eff.x
                    val cy = size.height / 2f + eff.y
                    val wx = (tap.x - cx) / s
                    val wy = (tap.y - cy) / s
                    var best = -1
                    var bestD = Float.MAX_VALUE
                    for (i in 0 until sim.n) {
                        val dx = sim.x[i] - wx
                        val dy = sim.y[i] - wy
                        val d = dx * dx + dy * dy
                        val hit = (sim.radii[i] + 10f)
                        if (d < hit * hit && d < bestD) { bestD = d; best = i }
                    }
                    if (best >= 0) onTapNode(sim.ids[best])
                }
            }
    ) {
        val scale = scaleAnim.value // reading the animated scale invalidates the draw
        val eff = effectiveOffset(scale)
        val cx = size.width / 2f + eff.x
        val cy = size.height / 2f + eff.y

        // Edges first, under the nodes — trimmed to node rims so lines stay visible.
        val edgeStroke = (2.8f * scale).coerceIn(2f, 4.5f)
        sim.edges.forEach { e ->
            val a = e[0]
            val b = e[1]
            val ax = cx + sim.x[a] * scale
            val ay = cy + sim.y[a] * scale
            val bx = cx + sim.x[b] * scale
            val by = cy + sim.y[b] * scale
            val ra = sim.radii[a] * scale
            val rb = sim.radii[b] * scale
            val (start, end) = trimmedLine(ax, ay, ra, bx, by, rb) ?: return@forEach
            drawLine(
                color = edgeColor,
                start = start,
                end = end,
                strokeWidth = edgeStroke,
                cap = StrokeCap.Round
            )
        }

        // Nodes — full colour for roots, slightly dimmed for leaves.
        for (i in 0 until sim.n) {
            drawCircle(
                color = if (isRoot[i]) nodeColors[i] else nodeColors[i].copy(alpha = 0.62f),
                radius = sim.radii[i] * scale,
                center = Offset(cx + sim.x[i] * scale, cy + sim.y[i] * scale)
            )
        }

        // Labels sit just below each node, like a caption.
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = labelColor.toArgb()
                textSize = (12f * density * scale).coerceIn(10f * density, 15f * density)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val gap = 5f * density
            // Baseline offset so the text's cap-height starts right under the gap.
            val baselineFromTop = -paint.ascent()
            for (i in 0 until sim.n) {
                val px = cx + sim.x[i] * scale
                val nodeBottom = cy + sim.y[i] * scale + sim.radii[i] * scale
                val py = nodeBottom + gap + baselineFromTop
                canvas.nativeCanvas.drawText(names[i], px, py, paint)
            }
        }
    }
}

/** Target scale and the graph's world-space center to keep on screen. */
private fun computeFitToScreen(
    sim: GraphSim,
    canvasSize: IntSize,
    fillFraction: Float
): Pair<Float, Offset> {
    if (sim.n == 0) return 1f to Offset.Zero

    val labelPad = 18f // graph units reserved below each node for its caption
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE

    for (i in 0 until sim.n) {
        val r = sim.radii[i]
        minX = min(minX, sim.x[i] - r)
        maxX = max(maxX, sim.x[i] + r)
        minY = min(minY, sim.y[i] - r)
        maxY = max(maxY, sim.y[i] + r + labelPad)
    }

    val graphW = (maxX - minX).coerceAtLeast(1f)
    val graphH = (maxY - minY).coerceAtLeast(1f)
    val center = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)

    val targetW = canvasSize.width * fillFraction
    val targetH = canvasSize.height * fillFraction
    val fitScale = min(targetW / graphW, targetH / graphH).coerceIn(0.3f, 4f)
    return fitScale to center
}

private fun trimmedLine(
    x1: Float, y1: Float, r1: Float,
    x2: Float, y2: Float, r2: Float
): Pair<Offset, Offset>? {
    val dx = x2 - x1
    val dy = y2 - y1
    val d = sqrt(dx * dx + dy * dy)
    if (d <= r1 + r2) return null
    val ux = dx / d
    val uy = dy / d
    return Offset(x1 + ux * r1, y1 + uy * r1) to Offset(x2 - ux * r2, y2 - uy * r2)
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.ArrowBack,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(8.dp)
                .size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
