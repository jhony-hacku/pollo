package com.sge.finca.platform.contacts

import android.content.Context
import android.provider.ContactsContract
import com.sge.finca.domain.model.DeviceContact
import com.sge.finca.domain.model.PhoneNumbers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lee la agenda del teléfono con ContentResolver (#20). Requiere READ_CONTACTS: la pantalla lo
 * pide justo antes de llamar a [readAll].
 *
 * Una consulta por tipo de dato (teléfonos y correos), agrupando por contacto. Los cursores se
 * cierran con use{} aunque algo falle.
 */
@Singleton
class DeviceContactsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun readAll(): List<DeviceContact> = withContext(Dispatchers.IO) {
        val names = mutableMapOf<String, String>()
        val phones = mutableMapOf<String, MutableList<String>>()
        val emails = mutableMapOf<String, String>()
        val resolver = context.contentResolver

        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol) ?: continue
                val number = cursor.getString(numberCol)?.trim().orEmpty()
                cursor.getString(nameCol)?.trim()?.takeIf { it.isNotEmpty() }?.let { names[id] = it }
                if (number.isEmpty()) continue
                val list = phones.getOrPut(id) { mutableListOf() }
                // El mismo número puede estar guardado dos veces con distinto formato.
                if (list.none { PhoneNumbers.sameNumber(it, number) }) list += number
            }
        }

        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
            val addressCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol) ?: continue
                val address = cursor.getString(addressCol)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                cursor.getString(nameCol)?.trim()?.takeIf { it.isNotEmpty() }?.let { names.putIfAbsent(id, it) }
                emails.putIfAbsent(id, address)
            }
        }

        names.map { (id, name) ->
            DeviceContact(id = id, name = name, phones = phones[id].orEmpty(), email = emails[id])
        }
            // Sin teléfono ni correo no sirve en el directorio.
            .filter { it.phones.isNotEmpty() || it.email != null }
            .sortedBy { it.name.lowercase() }
    }
}
