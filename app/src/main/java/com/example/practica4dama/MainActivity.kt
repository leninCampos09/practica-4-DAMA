package com.example.practica4dama

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var seekBar: SeekBar
    private lateinit var handler: Handler
    private var isPlaying = false
    private lateinit var fabPlay: FloatingActionButton
    private lateinit var btnPause: ImageButton
    private lateinit var tvCurrentSong: TextView
    private lateinit var tvAlbumTitle: TextView
    private lateinit var tvArtistName: TextView
    private lateinit var recyclerView: RecyclerView
    private var songs = mutableListOf<Song>()
    private var currentSongIndex = 0
    private val REQUEST_CODE_STORAGE_PERMISSIONS = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        fabPlay = findViewById(R.id.fabPlay)
        btnPause = findViewById(R.id.btnPause)
        seekBar = findViewById(R.id.seekBar)
        recyclerView = findViewById(R.id.recyclerView)
        tvCurrentSong = findViewById(R.id.tvCurrentSong)
        tvAlbumTitle = findViewById(R.id.tvAlbumTitle)
        tvArtistName = findViewById(R.id.tvArtistName)

        mediaPlayer = MediaPlayer()
        handler = Handler(Looper.getMainLooper())

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Verificar permisos antes de obtener canciones
        if (!checkStoragePermissions()) {
            requestStoragePermissions()
        } else {
            getSongsFromStorage()
        }

        fabPlay.setOnClickListener {
            if (!isPlaying) {
                if (songs.isNotEmpty()) {
                    playSong(songs[currentSongIndex])
                }
            } else {
                pauseSong()
            }
        }

        btnPause.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                pauseSong()
                btnPause.setImageResource(R.drawable.ic_play)  // Cambiar el ícono a "play"
            } else {
                resumeSong()
                btnPause.setImageResource(R.drawable.ic_pause)  // Cambiar el ícono a "pause"
            }
        }

        setupSeekBar()
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (mediaPlayer.isPlaying) {
                    seekBar.progress = mediaPlayer.currentPosition
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun playSong(song: Song) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(song.path)
            mediaPlayer.prepare()
            mediaPlayer.start()
            isPlaying = true
            fabPlay.setImageResource(R.drawable.ic_pause) // Cambiar el ícono del botón de reproducción

            // Mostrar el nombre de la canción, el nombre del artista y el álbum en el display
            tvCurrentSong.text = "${song.name} - ${song.artist}"
            tvAlbumTitle.text = "${song.album}"
            tvArtistName.text = "${song.artist}"

            seekBar.max = mediaPlayer.duration

            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                fabPlay.setImageResource(R.drawable.ic_play)
                if (currentSongIndex < songs.size - 1) {
                    currentSongIndex++
                    playSong(songs[currentSongIndex])
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al intentar reproducir la canción: ${e.message}")
            Toast.makeText(this, "Error al reproducir la canción", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pauseSong() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            fabPlay.setImageResource(R.drawable.ic_play) // Cambiar el ícono del fabPlay a "play"
            btnPause.setImageResource(R.drawable.ic_play)  // Cambiar el ícono de btnPause a "play"
        }
    }

    private fun resumeSong() {
        try {
            mediaPlayer.start()
            isPlaying = true
            fabPlay.setImageResource(R.drawable.ic_pause) // Cambiar el ícono del fabPlay a "pause"
            btnPause.setImageResource(R.drawable.ic_pause)  // Cambiar el ícono de btnPause a "pause"

            // Mostrar el nombre de la canción, el nombre del artista y el álbum en el display
            val currentSong = songs[currentSongIndex]
            tvCurrentSong.text = "${currentSong.name} - ${currentSong.artist}"
            tvAlbumTitle.text = "${currentSong.album}"
            tvArtistName.text = "${currentSong.artist}"

            // Actualizar el máximo del SeekBar y continuar desde la posición anterior
            seekBar.max = mediaPlayer.duration
            seekBar.progress = mediaPlayer.currentPosition

            // Si la canción ya está reproduciéndose, continuar actualizando la barra de progreso
            handler.postDelayed(object : Runnable {
                override fun run() {
                    if (mediaPlayer.isPlaying) {
                        seekBar.progress = mediaPlayer.currentPosition
                    }
                    handler.postDelayed(this, 1000)
                }
            }, 1000)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al intentar reanudar la canción: ${e.message}")
            Toast.makeText(this, "Error al reanudar la canción", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getSongsFromStorage() {
        // Uso de MediaStore para acceder a las canciones en el dispositivo
        val musicUri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME, // Nombre de la canción
            MediaStore.Audio.Media.DATA, // Ruta de la canción
            MediaStore.Audio.Media.ARTIST, // Nombre del artista
            MediaStore.Audio.Media.ALBUM // Nombre del álbum
        )

        val cursor: Cursor? = contentResolver.query(musicUri, projection, null, null, null)

        cursor?.let {
            if (it.moveToFirst()) {
                do {
                    val songPath = it.getString(it.getColumnIndex(MediaStore.Audio.Media.DATA))
                    val songName = it.getString(it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME))
                    val artistName = it.getString(it.getColumnIndex(MediaStore.Audio.Media.ARTIST)) ?: "Desconocido"
                    val albumName = it.getString(it.getColumnIndex(MediaStore.Audio.Media.ALBUM)) ?: "Desconocido"

                    // Agregar la canción con su nombre, artista y álbum a la lista
                    val song = Song(songName, artistName, albumName, songPath)
                    song.duration = getSongDuration(songPath)
                    songs.add(song)
                } while (it.moveToNext())
            }
            it.close()
        }

        if (songs.isNotEmpty()) {
            Log.d("MainActivity", "Canciones encontradas: $songs")

            // Configurar el adapter con la lista de canciones
            recyclerView.adapter = SongAdapter(songs) { song, index ->
                currentSongIndex = index
                playSong(song)
            }
        } else {
            Log.e("MainActivity", "No se encontraron canciones")
        }
    }

    private fun getSongDuration(path: String): String {
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(path)
        mediaPlayer.prepare()
        val duration = mediaPlayer.duration
        mediaPlayer.release()
        return formatDuration(duration)
    }

    private fun formatDuration(duration: Int): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_MEDIA_AUDIO), REQUEST_CODE_STORAGE_PERMISSIONS
            )
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_STORAGE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido, cargar canciones
                getSongsFromStorage()
            } else {
                // Permiso denegado
                Log.e("MainActivity", "Permiso de lectura de almacenamiento denegado")
                Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Adapter para mostrar canciones en el RecyclerView
    class SongAdapter(
        private val songList: List<Song>,
        private val onSongClick: (Song, Int) -> Unit
    ) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

        inner class SongViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val tvNumber: TextView = itemView.findViewById(R.id.tvNumber)  // TextView para número de pista
            val tvSongName: TextView = itemView.findViewById(R.id.tvTitle)
            val tvSongArtist: TextView = itemView.findViewById(R.id.tvArtist)
            val tvSongDuration: TextView = itemView.findViewById(R.id.tvDuration)

            fun bind(song: Song, index: Int) {
                // Establecer el número de pista (índice + 1)
                val trackNumber = (index + 1).toString()
                tvNumber.text = trackNumber  // +1 porque el índice comienza en 0

                // Ajustar el tamaño del texto del número de pista dependiendo del valor
                val textSize = when {
                    trackNumber.length > 3 -> 16f  // Para números mayores a 1000, aumenta el tamaño
                    trackNumber.length > 2 -> 16f  // Para números mayores a 100, aumenta el tamaño
                    else -> 16f  // Para números pequeños, tamaño normal
                }
                tvNumber.textSize = textSize

                // Establecer el nombre de la canción, el artista y la duración
                tvSongName.text = song.name
                tvSongArtist.text = song.artist
                tvSongDuration.text = song.duration

                // Establecer el clic sobre el ítem
                itemView.setOnClickListener {
                    onSongClick(song, index)
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SongViewHolder {
            val itemView = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song, parent, false)
            return SongViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            holder.bind(songList[position], position)
        }

        override fun getItemCount(): Int = songList.size
    }

    data class Song(
        val name: String,
        val artist: String,
        val album: String,
        val path: String,
        var duration: String = "00:00"
    )
}

