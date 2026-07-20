package com.io.ab.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.io.ab.music.MainActivity
import com.io.ab.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Base class for all AB Player widgets.
 * Handles: pending intents, artwork loading, dynamic coloring.
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    abstract val layoutResId: Int

    // Subclasses bind their specific views after base setup
    abstract fun bindViews(
        ctx: Context,
        views: RemoteViews,
        state: WidgetStateManager.WidgetState,
        artBitmap: Bitmap?
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val state = WidgetStateManager.loadState(context)
        scope.launch {
            val art = loadArtworkBitmap(context, state.artworkUri)
            val finalState = if (art != null)
                WidgetStateManager.extractPalette(art, state) else state

            appWidgetIds.forEach { id ->
                val views = RemoteViews(context.packageName, layoutResId)
                applyCommonIntents(context, views)
                bindViews(context, views, finalState, art)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    // ── Common PendingIntents ────────────────────────────────────────────────

    protected fun applyCommonIntents(ctx: Context, views: RemoteViews) {
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, buildBroadcast(ctx, WidgetAction.ACTION_PLAY_PAUSE))
        safeSetIntent(views, R.id.widget_btn_next,     buildBroadcast(ctx, WidgetAction.ACTION_NEXT))
        safeSetIntent(views, R.id.widget_btn_prev,     buildBroadcast(ctx, WidgetAction.ACTION_PREV))
        safeSetIntent(views, R.id.widget_btn_favorite, buildBroadcast(ctx, WidgetAction.ACTION_FAVORITE))
        safeSetIntent(views, R.id.widget_btn_queue,    buildBroadcast(ctx, WidgetAction.ACTION_OPEN_QUEUE))
        // Tap album art → open app
        safeSetIntent(views, R.id.widget_album_art,    buildLaunchApp(ctx))
    }

    private fun safeSetIntent(views: RemoteViews, viewId: Int, pi: PendingIntent) {
        try { views.setOnClickPendingIntent(viewId, pi) } catch (_: Exception) {}
    }

    protected fun buildBroadcast(ctx: Context, action: String): PendingIntent {
        val intent = Intent(action).apply { setPackage(ctx.packageName) }
        val flags = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(ctx, action.hashCode(), intent, flags)
    }

    protected fun buildLaunchApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(ctx, 0, intent, flags)
    }

    // ── Artwork Loading ──────────────────────────────────────────────────────

    protected suspend fun loadArtworkBitmap(ctx: Context, uriStr: String?): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (uriStr == null) return@withContext null
                val uri = Uri.parse(uriStr)
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) { null }
        }

    protected fun roundedBitmap(src: Bitmap, cornerDp: Float, ctx: Context): Bitmap {
        val scale = ctx.resources.displayMetrics.density
        val r = cornerDp * scale
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope.let {
            try { it.coroutineContext[kotlinx.coroutines.Job]?.cancel() } catch (_: Exception) {} 
        }
    }
}
