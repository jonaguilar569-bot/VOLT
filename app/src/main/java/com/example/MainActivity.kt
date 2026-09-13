package com.example

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private var permissionResultCallback: ((Boolean) -> Unit)? = null

    private val requestAudioPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionResultCallback?.invoke(granted)
        }

    private fun requestMusicPermission(
        callback: (Boolean) -> Unit
    ) {
        permissionResultCallback = callback

        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            callback(true)
        } else {
            requestAudioPermission.launch(audioPermission)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VoltColors.Background
                ) {
                    VoltApp(
                        onRequestMusicPermission = { callback ->
                            requestMusicPermission(callback)
                        }
                    )
                }
            }
        }
    }
}

private object VoltColors {
    val Background = Color(0xFF050607)
    val Surface = Color(0xFF101316)
    val SurfaceLight = Color(0xFF171B1F)
    val Border = Color(0xFF252B30)
    val White = Color(0xFFF1F1EF)
    val Gray = Color(0xFF92979B)
    val DarkGray = Color(0xFF5D6368)
    val Red = Color(0xFFE5092F)
    val RedSoft = Color(0xFF641021)
}

data class MusicSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri
)

data class MusicArtist(
    val name: String,
    val songCount: Int
)

data class MusicAlbum(
    val name: String,
    val artist: String,
    val songCount: Int,
    val representativeSongUri: Uri
)

@Composable
fun VoltApp(
    onRequestMusicPermission: ((Boolean) -> Unit) -> Unit
) {
    var currentScreen by remember { mutableStateOf("welcome") }
    var permissionGranted by remember { mutableStateOf(false) }

    when (currentScreen) {
        "welcome" -> {
            WelcomeScreen(
                onStartClick = {
                    onRequestMusicPermission { granted ->
                        permissionGranted = granted
                        currentScreen = "library"
                    }
                }
            )
        }

        "library" -> {
            LibraryScreen(
                permissionGranted = permissionGranted
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            painter = painterResource(
                id = R.drawable.volt_welcome_background
            ),
            contentDescription = "Inicio de VOLT",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        /*
         * Zona táctil del botón COMENZAR.
         */
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .clickable {
                    onStartClick()
                }
        )
    }
}

@Composable
fun LibraryScreen(
    permissionGranted: Boolean
) {
    val context = LocalContext.current

    var songs by remember { mutableStateOf<List<MusicSong>>(emptyList()) }
    var artists by remember { mutableStateOf<List<MusicArtist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<MusicAlbum>>(emptyList()) }

    var searchText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Artistas") }
    var selectedSong by remember { mutableStateOf<MusicSong?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            isLoading = true

            val library = loadMusicLibrary(context)

            songs = library.songs
            artists = library.artists
            albums = library.albums

            isLoading = false
        } else {
            isLoading = false
        }
    }

    val filteredSongs = songs.filter {
        it.title.contains(searchText, ignoreCase = true) ||
                it.artist.contains(searchText, ignoreCase = true) ||
                it.album.contains(searchText, ignoreCase = true)
    }

    val filteredArtists = artists.filter {
        it.name.contains(searchText, ignoreCase = true)
    }

    val filteredAlbums = albums.filter {
        it.name.contains(searchText, ignoreCase = true) ||
                it.artist.contains(searchText, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoltColors.Background)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Biblioteca",
                    color = VoltColors.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = VoltColors.White,
                        modifier = Modifier.size(27.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LibraryTab(
                    title = "Canciones",
                    selected = selectedTab == "Canciones",
                    onClick = { selectedTab = "Canciones" }
                )

                LibraryTab(
                    title = "Artistas",
                    selected = selectedTab == "Artistas",
                    onClick = { selectedTab = "Artistas" }
                )

                LibraryTab(
                    title = "Álbumes",
                    selected = selectedTab == "Álbumes",
                    onClick = { selectedTab = "Álbumes" }
                )

                LibraryTab(
                    title = "Playlists",
                    selected = selectedTab == "Playlists",
                    onClick = { selectedTab = "Playlists" }
                )
            }

            Spacer(modifier = Modifier.height(15.dp))

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Buscar en tu biblioteca...",
                        color = VoltColors.Gray,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = VoltColors.Gray
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VoltColors.Red,
                    unfocusedBorderColor = VoltColors.Border,
                    focusedContainerColor = VoltColors.Surface,
                    unfocusedContainerColor = VoltColors.Surface,
                    cursorColor = VoltColors.Red,
                    focusedTextColor = VoltColors.White,
                    unfocusedTextColor = VoltColors.White
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            when {
                isLoading -> {
                    LibraryMessage(
                        title = "Cargando tu música...",
                        message = "Estamos leyendo los archivos de audio del teléfono."
                    )
                }

                !permissionGranted -> {
                    LibraryMessage(
                        title = "Permiso necesario",
                        message = "VOLT necesita permiso para leer la música almacenada en tu teléfono."
                    )
                }

                songs.isEmpty() -> {
                    LibraryMessage(
                        title = "No encontramos música",
                        message = "El dispositivo de prueba no tiene archivos de audio reconocidos como música."
                    )
                }

                selectedTab == "Canciones" -> {
                    SongsList(
                        songs = filteredSongs,
                        onSongClick = { song ->
                            selectedSong = song
                        }
                    )
                }

                selectedTab == "Artistas" -> {
                    ArtistsList(
                        artists = filteredArtists
                    )
                }

                selectedTab == "Álbumes" -> {
                    AlbumsList(
                        albums = filteredAlbums
                    )
                }

                selectedTab == "Playlists" -> {
                    LibraryMessage(
                        title = "Playlists",
                        message = "Todavía no hay playlists creadas. Esta sección permitirá organizar tus canciones en listas personalizadas."
                    )
                }
            }
        }

        MiniPlayer(
            selectedSong = selectedSong
        )

        BottomNavigationBar()
    }
}

@Composable
fun LibraryTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable {
            onClick()
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = if (selected) VoltColors.White else VoltColors.Gray,
            fontSize = 13.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .width(if (selected) 48.dp else 0.dp)
                .height(2.dp)
                .background(VoltColors.Red)
        )
    }
}

@Composable
fun SongsList(
    songs: List<MusicSong>,
    onSongClick: (MusicSong) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(
            items = songs,
            key = { it.id }
        ) { song ->
            SongRow(
                song = song,
                onClick = {
                    onSongClick(song)
                }
            )
        }
    }
}

@Composable
fun SongRow(
    song: MusicSong,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            audioUri = song.uri,
            size = 54.dp
        )

        Spacer(modifier = Modifier.width(13.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                color = VoltColors.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${song.artist} · ${song.album}",
                color = VoltColors.Gray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Seleccionar canción",
            tint = VoltColors.Gray,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun ArtistsList(
    artists: List<MusicArtist>
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(
            items = artists,
            key = { it.name }
        ) { artist ->
            ArtistRow(artist = artist)
        }
    }
}

@Composable
fun ArtistRow(
    artist: MusicArtist
) {
    val firstLetter = artist.name
        .trim()
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "?"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            VoltColors.RedSoft,
                            VoltColors.SurfaceLight
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = VoltColors.Border,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstLetter,
                color = VoltColors.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = artist.name,
                color = VoltColors.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${artist.songCount} canciones",
                color = VoltColors.Gray,
                fontSize = 12.sp
            )
        }

        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = "Abrir artista",
            tint = VoltColors.Gray,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
fun AlbumsList(
    albums: List<MusicAlbum>
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(
            items = albums,
            key = { "${it.name}-${it.artist}" }
        ) { album ->
            AlbumRow(album = album)
        }
    }
}

@Composable
fun AlbumRow(
    album: MusicAlbum
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            audioUri = album.representativeSongUri,
            size = 54.dp
        )

        Spacer(modifier = Modifier.width(13.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = album.name,
                color = VoltColors.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${album.artist} · ${album.songCount} canciones",
                color = VoltColors.Gray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.Album,
            contentDescription = "Álbum",
            tint = VoltColors.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun AlbumArt(
    audioUri: Uri,
    size: androidx.compose.ui.unit.Dp
) {
    var bitmap by remember(audioUri) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(audioUri) {
        bitmap = withContext(Dispatchers.IO) {
            loadEmbeddedAlbumArt(audioUri)
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(VoltColors.SurfaceLight),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Carátula",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Sin carátula",
                tint = VoltColors.Gray,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

fun loadEmbeddedAlbumArt(
    audioUri: Uri
): Bitmap? {
    val retriever = MediaMetadataRetriever()

    return try {
        retriever.setDataSource(
            AppContextHolder.context,
            audioUri
        )

        val embeddedPicture = retriever.embeddedPicture

        if (embeddedPicture != null) {
            android.graphics.BitmapFactory.decodeByteArray(
                embeddedPicture,
                0,
                embeddedPicture.size
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

object AppContextHolder {
    lateinit var context: Context
}

data class MusicLibrary(
    val songs: List<MusicSong>,
    val artists: List<MusicArtist>,
    val albums: List<MusicAlbum>
)

fun loadMusicLibrary(
    context: Context
): MusicLibrary {
    AppContextHolder.context = context.applicationContext

    val songs = mutableListOf<MusicSong>()

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.IS_MUSIC
    )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    )?.use { cursor ->

        val idColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Audio.Media._ID
        )

        val titleColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Audio.Media.TITLE
        )

        val artistColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Audio.Media.ARTIST
        )

        val albumColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Audio.Media.ALBUM
        )

        val durationColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Audio.Media.DURATION
        )

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)

            val rawTitle = cursor.getString(titleColumn)
            val rawArtist = cursor.getString(artistColumn)
            val rawAlbum = cursor.getString(albumColumn)
            val duration = cursor.getLong(durationColumn)

            val title = if (rawTitle.isNullOrBlank()) {
                "Canción desconocida"
            } else {
                rawTitle.trim()
            }

            val artist = if (
                rawArtist.isNullOrBlank() ||
                rawArtist.equals("<unknown>", ignoreCase = true)
            ) {
                "Artista desconocido"
            } else {
                rawArtist.trim()
            }

            val album = if (
                rawAlbum.isNullOrBlank() ||
                rawAlbum.equals("<unknown>", ignoreCase = true)
            ) {
                "Álbum desconocido"
            } else {
                rawAlbum.trim()
            }

            val songUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            )

            songs.add(
                MusicSong(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    uri = songUri
                )
            )
        }
    }

    val artists = songs
        .groupingBy { it.artist }
        .eachCount()
        .map { (name, count) ->
            MusicArtist(
                name = name,
                songCount = count
            )
        }
        .sortedBy {
            it.name.lowercase()
        }

    val albums = songs
        .groupBy {
            "${it.album}|||${it.artist}"
        }
        .map { (_, albumSongs) ->
            val firstSong = albumSongs.first()

            MusicAlbum(
                name = firstSong.album,
                artist = firstSong.artist,
                songCount = albumSongs.size,
                representativeSongUri = firstSong.uri
            )
        }
        .sortedBy {
            it.name.lowercase()
        }

    return MusicLibrary(
        songs = songs,
        artists = artists,
        albums = albums
    )
}

@Composable
fun LibraryMessage(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 45.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = VoltColors.Red,
            modifier = Modifier.size(38.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            color = VoltColors.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            color = VoltColors.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun MiniPlayer(
    selectedSong: MusicSong?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VoltColors.Surface)
            .border(
                width = 1.dp,
                color = VoltColors.Border,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedSong != null) {
            AlbumArt(
                audioUri = selectedSong.uri,
                size = 43.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(43.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(VoltColors.SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = VoltColors.Gray
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = selectedSong?.title ?: "Ninguna canción",
                color = VoltColors.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = selectedSong?.artist
                    ?: "Seleccioná una canción para reproducir",
                color = VoltColors.Gray,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = { }
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Reproducir",
                tint = VoltColors.Red
            )
        }
    }
}

@Composable
fun BottomNavigationBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoltColors.Background)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavigationItem(
            icon = Icons.Default.Home,
            label = "Inicio",
            selected = false
        )

        BottomNavigationItem(
            icon = Icons.Default.LibraryMusic,
            label = "Biblioteca",
            selected = true
        )

        BottomNavigationItem(
            icon = Icons.Default.Search,
            label = "Explorar",
            selected = false
        )

        BottomNavigationItem(
            icon = Icons.Default.List,
            label = "Estadísticas",
            selected = false
        )

        BottomNavigationItem(
            icon = Icons.Default.Person,
            label = "Más",
            selected = false
        )
    }
}

@Composable
fun BottomNavigationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) VoltColors.Red else VoltColors.Gray,
            modifier = Modifier.size(21.dp)
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label,
            color = if (selected) VoltColors.Red else VoltColors.Gray,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}