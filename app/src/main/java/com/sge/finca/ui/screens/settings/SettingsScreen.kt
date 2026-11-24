package com.sge.finca.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.sge.finca.platform.biometric.BiometricAvailability
import com.sge.finca.ui.screens.auth.findFragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showProfileEditor by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var selectedPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        selectedPhotoUri = uri?.toString()
    }

    LaunchedEffect(showProfileEditor) {
        if (showProfileEditor) {
            draftName = state.userName
            selectedPhotoUri = null
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { text ->
            snackbarHostState.showSnackbar(text)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                ListItem(
                    headlineContent = {
                        Text(state.userName.ifBlank { "Perfil" })
                    },
                    supportingContent = {
                        Text(state.userEmail.ifBlank { "Nombre y correo electrónico" })
                    },
                    leadingContent = {
                        ProfileAvatar(
                            model = state.photoUrl.takeIf { it.isNotBlank() },
                            size = 52.dp,
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Editar perfil",
                        )
                    },
                    modifier = Modifier.clickable {
                        showProfileEditor = true
                    },
                )

                ListItem(
                    headlineContent = { Text("Inicio rápido con huella") },
                    supportingContent = {
                        Text(biometricDescription(state.biometricAvailability))
                    },
                    leadingContent = {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = state.biometricEnabled,
                            enabled = state.biometricEnabled ||
                                    state.biometricAvailability != BiometricAvailability.NO_HARDWARE,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    context.findFragmentActivity()
                                        ?.let { viewModel.onBiometricEnableRequested(it) }
                                } else {
                                    viewModel.onBiometricDisabled()
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    if (showProfileEditor) {
        val previewModel: Any? = selectedPhotoUri
            ?.let(Uri::parse)
            ?: state.photoUrl.takeIf { it.isNotBlank() }

        AlertDialog(
            onDismissRequest = {
                if (!state.isSavingProfile) {
                    showProfileEditor = false
                }
            },
            title = {
                Text("Editar perfil")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.clickable {
                            if (!state.isSavingProfile) {
                                photoPicker.launch("image/*")
                            }
                        },
                    ) {
                        ProfileAvatar(
                            model = previewModel,
                            size = 112.dp,
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = 4.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Seleccionar foto",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = "Toca la foto para elegir una imagen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.size(2.dp))

                    OutlinedTextField(
                        value = draftName,
                        onValueChange = {
                            if (it.length <= 60) draftName = it
                        },
                        label = { Text("Nombre") },
                        singleLine = true,
                        enabled = !state.isSavingProfile,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.userEmail,
                        onValueChange = {},
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isSavingProfile && draftName.trim().length >= 2,
                    onClick = {
                        viewModel.saveProfile(
                            name = draftName,
                            selectedPhotoUri = selectedPhotoUri,
                        ) {
                            showProfileEditor = false
                            selectedPhotoUri = null
                        }
                    },
                ) {
                    if (state.isSavingProfile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Guardar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isSavingProfile,
                    onClick = {
                        showProfileEditor = false
                        selectedPhotoUri = null
                    },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun ProfileAvatar(
    model: Any?,
    size: Dp,
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        if (model == null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = "Foto de perfil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(size * 0.35f),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                error = {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(size * 0.55f),
                        )
                    }
                },
            )
        }
    }
}

private fun biometricDescription(availability: BiometricAvailability): String =
    when (availability) {
        BiometricAvailability.AVAILABLE -> "Entra con tu huella al abrir la app"
        BiometricAvailability.NO_HARDWARE -> "Este dispositivo no tiene sensor de huella"
        BiometricAvailability.NONE_ENROLLED -> "No hay huellas registradas en el dispositivo"
        BiometricAvailability.UNAVAILABLE -> "No disponible en este momento"
    }