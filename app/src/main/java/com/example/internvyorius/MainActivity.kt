package com.example.internvyorius

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.internvyorius.ui.theme.InternVyoriusTheme
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout

    private var isRecording = mutableStateOf(false)
    private var lastUrl: String = ""
    private var recordedVideos = mutableStateListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        libVLC = LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames"))
        mediaPlayer = MediaPlayer(libVLC)

        setContent {
            InternVyoriusTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StreamUI()
                }
            }
        }
    }

    @Composable
    fun StreamUI() {
        var url by remember { mutableStateOf(TextFieldValue("rtsp://192.168.1.2:5540/ch0")) }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            dir?.listFiles()?.filter { it.extension == "mp4" }?.forEach {
                if (!recordedVideos.contains(it)) {
                    recordedVideos.add(it)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("RTSP URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            AndroidView(
                modifier = Modifier
                    .height(250.dp)
                    .fillMaxWidth(),
                factory = {
                    VLCVideoLayout(it).also { layout ->
                        videoLayout = layout
                        mediaPlayer.attachViews(videoLayout, null, false, false)
                    }
                }
            )

            if (isRecording.value) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "Recording",
                        tint = Color.Red
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recording...", color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                lastUrl = url.text
                restartStream(lastUrl)
                isRecording.value = false
            }) {
                Text("Play Stream")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                lastUrl = url.text
                if (!isRecording.value) {
                    restartStream(lastUrl, record = true)
                } else {
                    restartStream(lastUrl)
                }
                isRecording.value = !isRecording.value
            }) {
                Text(if (!isRecording.value) "Start Recording" else "Stop Recording")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Recorded Videos", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn {
                    items(recordedVideos) { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = video.name, modifier = Modifier.weight(1f))
                            Row {
                                Button(onClick = {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        video
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "video/*")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(intent)
                                }) {
                                    Text("Play")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    if (video.delete()) {
                                        recordedVideos.remove(video)
                                    }
                                }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun restartStream(rtspUrl: String, record: Boolean = false) {
        stopStream()
        if (record) {
            startRecording(rtspUrl)
        } else {
            playStream(rtspUrl)
        }
    }

    private fun playStream(rtspUrl: String) {
        try {
            val media = Media(libVLC, Uri.parse(rtspUrl))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=150")
            mediaPlayer.media = media
            media.release()
            mediaPlayer.attachViews(videoLayout, null, false, false)
            mediaPlayer.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startRecording(streamUrl: String) {
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val fileName = "record_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
            val outputFile = File(dir, fileName)

            val media = Media(libVLC, Uri.parse(streamUrl))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=150")
            media.addOption(":sout=#duplicate{dst=display,dst=file{dst=${outputFile.absolutePath}}}")
            media.addOption(":sout-keep")

            mediaPlayer.media = media
            media.release()
            mediaPlayer.attachViews(videoLayout, null, false, false)
            mediaPlayer.play()

            recordedVideos.add(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopStream() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.detachViews()
            mediaPlayer.media?.release()
            mediaPlayer.setEventListener(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(this, permissions, 1)
        }
    }

    override fun onStop() {
        super.onStop()
        stopStream()
    }

    override fun onDestroy() {
        stopStream()
        mediaPlayer.release()
        libVLC.release()
        super.onDestroy()
    }
}
