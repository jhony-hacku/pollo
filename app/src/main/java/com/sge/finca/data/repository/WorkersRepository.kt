package com.sge.finca.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sge.finca.data.remote.FirestorePaths
import com.sge.finca.data.remote.toGrants
import com.sge.finca.domain.model.Permission
import com.sge.finca.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class WorkerSummary(
    val uid: String,
    val name: String,
    val email: String,
    val photoUrl: String,
    val isActive: Boolean,
    val joinedAt: Long,
    val grants: Set<Permission> = emptySet(),
)

@Singleton
class WorkersRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionRepository: SessionRepository,
) {
    fun observeWorkers(farmId: String): Flow<List<WorkerSummary>> = callbackFlow {
        val registration = firestore
            .collection("${FirestorePaths.farm(farmId)}/${FirestorePaths.MEMBERS}")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val workers = snapshot?.documents.orEmpty()
                    .filter { it.getString("role") == "WORKER" }
                    .map { it.toMemberSummary() }
                    .sortedBy { it.name.lowercase() }

                trySend(workers)
            }

        awaitClose { registration.remove() }
    }

    fun observeOwner(farmId: String): Flow<WorkerSummary?> = callbackFlow {
        val registration = firestore
            .collection("${FirestorePaths.farm(farmId)}/${FirestorePaths.MEMBERS}")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val owner = snapshot?.documents.orEmpty()
                    .firstOrNull { it.getString("role") == "OWNER" }
                    ?.toMemberSummary()

                trySend(owner)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Otorga o quita a un trabajador un permiso configurable.
     */
    suspend fun setGrant(
        farmId: String,
        workerUid: String,
        permission: Permission,
        granted: Boolean,
    ) {
        sessionRepository.requirePermission(farmId, Permission.MANAGE_WORKERS)

        val change = if (granted) {
            FieldValue.arrayUnion(permission.name)
        } else {
            FieldValue.arrayRemove(permission.name)
        }

        firestore
            .document(FirestorePaths.member(farmId, workerUid))
            .update("grants", change)
            .await()
    }

    private fun DocumentSnapshot.toMemberSummary(): WorkerSummary {
        return WorkerSummary(
            uid = id,
            name = getString("displayName")
                ?: getString("name")
                ?: "",
            email = getString("email").orEmpty(),
            photoUrl = getString("photoUrl").orEmpty(),
            isActive = getBoolean("isActive") ?: true,
            joinedAt = getLong("joinedAt") ?: 0L,
            grants = toGrants(),
        )
    }
}