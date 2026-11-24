package com.sge.finca.ui.screens.contacts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.sge.finca.domain.model.FarmContact

/**
 * Directorio de contactos de la finca (#20). Crear a mano no necesita permisos; importar del
 * teléfono pide READ_CONTACTS solo al tocar "Importar", y negarlo no bloquea el resto.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var alreadyRequested by rememberSaveable { mutableStateOf(false) }
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FarmContact?>(null) }

    val contactsPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS) { granted ->
        if (granted) viewModel.onImportRequested() else viewModel.onPermissionDenied()
    }

    fun requestImport() {
        val status = contactsPermission.status
        when {
            status.isGranted -> viewModel.onImportRequested()
            status.shouldShowRationale -> showRationale = true
            alreadyRequested -> showSettings = true // denegado permanentemente
            else -> {
                alreadyRequested = true
                contactsPermission.launchPermissionRequest()
            }
        }
    }

    fun dial(phone: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
        } catch (e: ActivityNotFoundException) {
            viewModel.onNoDialerApp()
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(message)
        } finally {
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Directorio de Contactos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (state.canManage) {
                        IconButton(onClick = ::requestImport) {
                            Icon(Icons.Default.ImportContacts, contentDescription = "Importar del teléfono")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canManage) {
                FloatingActionButton(onClick = viewModel::onAddClick) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Agregar contacto")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.contacts.isEmpty() -> EmptyDirectory(
                    canManage = state.canManage,
                    onImport = ::requestImport,
                    onAdd = viewModel::onAddClick,
                )
                else -> {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Buscar por nombre, teléfono o nota") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.visibleContacts, key = { it.id }) { contact ->
                            ContactCard(
                                contact = contact,
                                onClick = { viewModel.onEditClick(contact) },
                                onCall = contact.phone?.let { phone -> { dial(phone) } },
                            )
                        }
                    }
                }
            }
        }
    }

    state.form?.let { form ->
        ContactFormDialog(
            form = form,
            isSaving = state.isSaving,
            onChange = viewModel::onFormChange,
            onSave = viewModel::onFormSave,
            onDismiss = viewModel::onFormDismiss,
            onDelete = form.id?.let { id ->
                { deleting = state.contacts.firstOrNull { it.id == id } }
            },
        )
    }

    state.importing?.let { importing ->
        ImportDialog(
            importing = importing,
            isSaving = state.isSaving,
            onQueryChange = viewModel::onImportQueryChange,
            onToggle = viewModel::onToggleCandidate,
            onImport = viewModel::onImportSelected,
            onDismiss = viewModel::onImportDismiss,
        )
    }

    deleting?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar contacto") },
            text = { Text("¿Eliminar a ${contact.name} del directorio de la finca? Se borra para todos los miembros.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.onDelete(contact)
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Permiso de contactos") },
            text = { Text("Para importar contactos de tu teléfono al directorio de la finca. También puedes agregarlos a mano.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    alreadyRequested = true
                    contactsPermission.launchPermissionRequest()
                }) { Text("Conceder permiso") }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Ahora no") } },
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Contactos desactivados") },
            text = { Text("El permiso de contactos está denegado. Actívalo en Ajustes para importar, o agrega los contactos a mano.") },
            confirmButton = {
                TextButton(onClick = {
                    showSettings = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                }) { Text("Abrir Ajustes") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun EmptyDirectory(canManage: Boolean, onImport: () -> Unit, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No hay contactos registrados", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Guarda aquí veterinarios, proveedores y compradores. Todos los miembros de la finca los ven.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canManage) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onImport) {
                Icon(Icons.Default.ImportContacts, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Importar del teléfono")
            }
            TextButton(onClick = onAdd) { Text("Agregar a mano") }
        }
    }
}

@Composable
private fun ContactCard(contact: FarmContact, onClick: () -> Unit, onCall: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                contact.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                contact.email?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                contact.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onCall != null) {
                IconButton(onClick = onCall) {
                    Icon(Icons.Default.Call, contentDescription = "Llamar a ${contact.name}", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ContactFormDialog(
    form: ContactForm,
    isSaving: Boolean,
    onChange: (ContactForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (form.id == null) "Nuevo contacto" else "Editar contacto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onChange(form.copy(name = it)) },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.phone,
                    onValueChange = { onChange(form.copy(phone = it)) },
                    label = { Text("Teléfono") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = form.email,
                    onValueChange = { onChange(form.copy(email = it)) },
                    label = { Text("Correo (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onChange(form.copy(notes = it)) },
                    label = { Text("Notas (p. ej. veterinario)") },
                )
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !isSaving) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, enabled = !isSaving) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun ImportDialog(
    importing: ImportState,
    isSaving: Boolean,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "Importar del teléfono",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
                when {
                    importing.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    importing.error != null -> Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                        Text(importing.error, color = MaterialTheme.colorScheme.error)
                    }
                    importing.candidates.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                        Text("No hay contactos con teléfono o correo en este celular.")
                    }
                    else -> {
                        OutlinedTextField(
                            value = importing.query,
                            onValueChange = onQueryChange,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text("Buscar") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(importing.visibleCandidates, key = { it.contact.id }) { candidate ->
                                val checked = candidate.contact.id in importing.selected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !candidate.duplicate) { onToggle(candidate.contact.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked || candidate.duplicate,
                                        onCheckedChange = { onToggle(candidate.contact.id) },
                                        enabled = !candidate.duplicate,
                                    )
                                    Column {
                                        Text(candidate.contact.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            if (candidate.duplicate) {
                                                "Ya está en el directorio"
                                            } else {
                                                candidate.contact.phones.firstOrNull() ?: candidate.contact.email.orEmpty()
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onImport, enabled = !isSaving && importing.selected.isNotEmpty()) {
                        Text("Importar seleccionados (${importing.selected.size})")
                    }
                }
            }
        }
    }
}
