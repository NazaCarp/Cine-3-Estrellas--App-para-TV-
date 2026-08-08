package com.cine3estrellas.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cine3estrellas.Movie
import com.cine3estrellas.mobile.Gold

@Composable
fun MobileMovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
        ) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = movie.title,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = " ${movie.vote_average ?: 0.0}",
                style = MaterialTheme.typography.labelSmall,
                color = Gold,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = movie.release_date?.take(4) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}
