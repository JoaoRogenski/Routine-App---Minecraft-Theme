package com.example.routineapp.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.routineapp.R
import com.example.routineapp.app

/** Button click sound; SoundPool keeps latency low on rapid taps. USAGE_GAME plays on the media volume, so silent mode doesn't mute it. */
class ClickSound(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val id = pool.load(context, R.raw.click, 1)

    fun play() { pool.play(id, 1f, 1f, 1, 0, 1f) }
}

/** Wraps [onClick] so it also plays the click sound. */
@Composable
fun withClick(onClick: () -> Unit): () -> Unit {
    val sound = LocalContext.current.app.clickSound
    return { sound.play(); onClick() }
}
