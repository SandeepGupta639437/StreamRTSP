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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
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
    private var recordingPlayer: MediaPlayer? = null

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
                    Box {
                        Image(
                            painter = rememberAsyncImagePainter("https://images.unsplash.com/photo-1508779018996-9bdc3f3ed86d"),
                            contentDescription = "Background",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.1f
                        )
                        StreamUI()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
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
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp)
        ) {
            TopAppBar(
                title = { Text("RTSP Streamer", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.onSurface, shape = CircleShape),
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("RTSP URL") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        VLCVideoLayout(it).also { layout ->
                            videoLayout = layout
                            mediaPlayer.attachViews(videoLayout, null, false, false)
                        }
                    }
                )
            }

            if (isRecording.value) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "Recording",
                        tint = Color.Red
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recording in progress", color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        lastUrl = url.text
                        playStream(lastUrl)
                        isRecording.value = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Play Stream", color = MaterialTheme.colorScheme.onPrimary)
                }

                Button(
                    onClick = {
                        lastUrl = url.text
                        if (!isRecording.value) {
                            startRecording(lastUrl)
                        } else {
                            stopRecording()
                        }
                        isRecording.value = !isRecording.value
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isRecording.value) Color(0xFF388E3C) else Color(0xFFD32F2F)
                    )
                ) {
                    Text(if (!isRecording.value) "Start Recording" else "Stop", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Recorded Videos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recordedVideos) { video ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(video.name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)

                            Row {
                                IconButton(onClick = {
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
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                }

                                IconButton(onClick = {
                                    if (video.delete()) recordedVideos.remove(video)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playStream(rtspUrl: String) {
        try {
            stopStream()
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
            media.addOption(":sout=#file{dst=${outputFile.absolutePath}}")
            media.addOption(":sout-keep")

            recordingPlayer = MediaPlayer(libVLC).apply {
                this.media = media
                media.release()
                play()
            }

            recordedVideos.add(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            recordingPlayer?.stop()
            recordingPlayer?.release()
            recordingPlayer = null
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
        stopRecording()
        stopStream()
    }

    override fun onDestroy() {
        stopRecording()
        stopStream()
        mediaPlayer.release()
        libVLC.release()
        super.onDestroy()
    }
}
