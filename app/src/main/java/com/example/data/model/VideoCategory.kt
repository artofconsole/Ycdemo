package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CherryRed
import com.example.ui.theme.CherryRedContainer
import com.example.ui.theme.CoralPink
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenContainer
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SkyBlueContainer
import com.example.ui.theme.SunshineYellow
import com.example.ui.theme.SunshineYellowContainer

enum class VideoCategory(
    val id: String,
    val labelTr: String,
    val icon: ImageVector,
    val accentColor: Color,
    val containerColor: Color
) {
    ALL(
        id = "all",
        labelTr = "Tümü",
        icon = Icons.Rounded.AutoAwesome,
        accentColor = CherryRed,
        containerColor = CherryRedContainer
    ),
    FAVORITES(
        id = "favorites",
        labelTr = "Favoriler",
        icon = Icons.Rounded.Favorite,
        accentColor = CoralPink,
        containerColor = CherryRedContainer
    ),
    CARTOON(
        id = "cartoon",
        labelTr = "Çizgi Film",
        icon = Icons.Rounded.Tv,
        accentColor = SkyBlue,
        containerColor = SkyBlueContainer
    ),
    MUSIC(
        id = "music",
        labelTr = "Müzik & Şarkı",
        icon = Icons.Rounded.MusicNote,
        accentColor = MintGreen,
        containerColor = MintGreenContainer
    ),
    EDUCATIONAL(
        id = "educational",
        labelTr = "Eğitici",
        icon = Icons.Rounded.School,
        accentColor = SunshineYellow,
        containerColor = SunshineYellowContainer
    ),
    GAMES(
        id = "games",
        labelTr = "Oyun",
        icon = Icons.Rounded.SportsEsports,
        accentColor = CherryRed,
        containerColor = CherryRedContainer
    );

    companion object {
        val assignableCategories: List<VideoCategory> = listOf(
            CARTOON,
            MUSIC,
            EDUCATIONAL,
            GAMES
        )

        fun fromId(raw: String?): VideoCategory {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: CARTOON
        }
    }
}
