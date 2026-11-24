package com.sge.finca.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.sge.finca.domain.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var draft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfilePhoto(
                            photoUrl = state.participantPhotoUrl,
                            name = state.title,
                            size = 42,
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = state.title.ifBlank { "Conversación" },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Miembro de la finca",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.messages.isEmpty() -> {
                        Text(
                            text = "Escribe el primer mensaje",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            reverseLayout = true,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            items(
                                items = state.messages.asReversed(),
                                key = { it.id },
                            ) { message ->
                                MessageBubble(
                                    message = message,
                                    mine = message.senderUid == state.myUid,
                                    participantName = state.title,
                                    participantPhotoUrl = state.participantPhotoUrl,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it.take(ChatMessage.MAX_LENGTH)
                    },
                    placeholder = {
                        Text("Escribe un mensaje")
                    },
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = {
                        viewModel.onSend(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    participantName: String,
    participantPhotoUrl: String,
) {
    if (mine) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            MessageSurface(
                message = message,
                mine = true,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            ProfilePhoto(
                photoUrl = participantPhotoUrl,
                name = participantName,
                size = 34,
            )

            Spacer(modifier = Modifier.width(8.dp))

            MessageSurface(
                message = message,
                mine = false,
            )
        }
    }
}

@Composable
private fun MessageSurface(
    message: ChatMessage,
    mine: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 300.dp),
        shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (mine) 18.dp else 4.dp,
            bottomEnd = if (mine) 4.dp else 18.dp,
        ),
        color = if (mine) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 9.dp,
            ),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = formatChatTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun ProfilePhoto(
    photoUrl: String,
    name: String,
    size: Int,
) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        if (photoUrl.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size((size * 0.58f).dp),
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = "Foto de $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size((size * 0.45f).dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size((size * 0.58f).dp),
                        )
                    }
                },
            )
        }
    }
}

/** Hoy: solo la hora; otros días: fecha y hora. */
internal fun formatChatTime(timestamp: Long): String {
    val locale = Locale.forLanguageTag("es-CO")
    val today = Calendar.getInstance()
    val messageDate = Calendar.getInstance().apply {
        timeInMillis = timestamp
    }

    val sameDay =
        today.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR)

    val pattern = if (sameDay) {
        "HH:mm"
    } else {
        "dd/MM HH:mm"
    }

    return SimpleDateFormat(pattern, locale).format(Date(timestamp))
}