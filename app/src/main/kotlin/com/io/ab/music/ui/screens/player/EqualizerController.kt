package com.io.ab.music.ui.screens.player

import android.media.audiofx.Equalizer
import android.util.Log

/**
 * Wrapper around Android's [Equalizer] AudioEffect.
 *
 * Usage: create once per audio session, call [setBandLevel] when sliders change,
 * call [release] when done (ViewModel.onCleared).
 */
class EqualizerController(audioSessionId: Int) {

    private var equalizer: Equalizer? = null

    /** Band frequency center labels (mHz → Hz string) */
    val bandLabels: List<String>
    val bandCount: Int
    val bandLevelRange: IntRange   // in millibels

    init {
        var eq: Equalizer? = null
        var labels  = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
        var count   = 5
        var minLvl  = -1000
        var maxLvl  =  1000

        try {
            eq = Equalizer(0, audioSessionId)
            eq.enabled = true
            count   = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            minLvl  = range[0].toInt()
            maxLvl  = range[1].toInt()
            labels  = (0 until count).map { band ->
                val freq = eq.getCenterFreq(band.toShort()) / 1000   // mHz → Hz
                when {
                    freq >= 1000 -> "${freq / 1000}kHz"
                    else         -> "${freq}Hz"
                }
            }
        } catch (e: Exception) {
            Log.w("EQ", "AudioEffect unavailable: ${e.message}")
        }

        equalizer    = eq
        bandLabels   = labels
        bandCount    = count
        bandLevelRange = minLvl..maxLvl
    }

    /** @param bandIndex 0-based band index
     *  @param levelDb   gain in dB (e.g. -10..10); converted to millibels internally. */
    fun setBandLevel(bandIndex: Int, levelDb: Float) {
        val eq = equalizer ?: return
        val mb = (levelDb * 100).toInt()
            .coerceIn(bandLevelRange)
            .toShort()
        try { eq.setBandLevel(bandIndex.toShort(), mb) }
        catch (e: Exception) { Log.e("EQ", "setBandLevel failed: ${e.message}") }
    }

    fun getBandLevelDb(bandIndex: Int): Float {
        return try {
            equalizer?.getBandLevel(bandIndex.toShort())?.toFloat()?.div(100f) ?: 0f
        } catch (e: Exception) { 0f }
    }

    fun applyPreset(preset: EqPreset) {
        val gains = when (preset) {
            EqPreset.FLAT  -> FloatArray(bandCount) { 0f }
            EqPreset.BASS  -> floatArrayOf(8f, 5f, 0f, -2f, -2f).padOrTrim(bandCount)
            EqPreset.VOCAL -> floatArrayOf(-3f, 0f, 4f, 5f, 2f).padOrTrim(bandCount)
            EqPreset.TREBLE -> floatArrayOf(-2f, -1f, 0f, 4f, 6f).padOrTrim(bandCount)
            EqPreset.CLUB  -> floatArrayOf(2f, 5f, 6f, 3f, 0f).padOrTrim(bandCount)
        }
        gains.forEachIndexed { i, db -> setBandLevel(i, db) }
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        equalizer = null
    }

    private fun FloatArray.padOrTrim(size: Int): FloatArray =
        FloatArray(size) { getOrElse(it) { 0f } }
}

enum class EqPreset { FLAT, BASS, VOCAL, TREBLE, CLUB }
