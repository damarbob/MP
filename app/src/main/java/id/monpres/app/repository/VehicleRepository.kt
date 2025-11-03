package id.monpres.app.repository

import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import id.monpres.app.dao.VehicleDao
import id.monpres.app.model.Vehicle
import id.monpres.app.usecase.DeleteBulkDataByIdsUseCase
import id.monpres.app.usecase.DeleteVehicleUseCase
import id.monpres.app.usecase.GetVehicleByIdUseCase
import id.monpres.app.usecase.GetVehiclesByUserIdFlowUseCase
import id.monpres.app.usecase.GetVehiclesByUserIdUseCase
import id.monpres.app.usecase.InsertVehicleUseCase
import id.monpres.app.usecase.UpdateVehicleUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val vehicleDao: VehicleDao,
    private val getVehiclesByUserIdFlowUseCase: GetVehiclesByUserIdFlowUseCase,
    private val getVehiclesByUserIdUseCase: GetVehiclesByUserIdUseCase,
    private val getVehicleByIdUseCase: GetVehicleByIdUseCase,
    private val insertVehicleUseCase: InsertVehicleUseCase,
    private val updateVehicleUseCase: UpdateVehicleUseCase,
    private val deleteVehicleUseCase: DeleteVehicleUseCase,
    private val deleteBulkDataByIdsUseCase: DeleteBulkDataByIdsUseCase,
) : Repository<Vehicle>() {

    companion object {
        private const val TAG = "VehicleRepository"
    }

    /**
     * Retrieves a flow of vehicles for the current user, implementing an offline-first strategy.
     *
     * This function returns a Flow that directly observes the local Room database.
     * In the background, it also triggers a real-time listener to Firestore to keep the
     * local cache synchronized.
     *
     * @param scope The CoroutineScope (e.g., viewModelScope) to launch the sync process in.
     * @return A [Flow] of a list of [Vehicle] objects from the local cache.
     *         Exceptions from the remote sync process are logged but do not interrupt this flow.
     */
    fun getVehiclesByUserIdFlow(scope: CoroutineScope): Flow<List<Vehicle>> {
        val userId = getCurrentUserId()

        // Trigger the background sync process. This is a fire-and-forget coroutine.
        // It will stay alive as long as the viewModelScope that calls this function is alive.
        scope.launch {
            syncRemoteToLocal()
        }

        // The UI observes the local database directly. Room ensures this flow emits
        // new data whenever the underlying table is changed by the sync process.
        return vehicleDao.getVehiclesByUserFlow(userId)
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    /**
     * Helper function to establish a real-time listener from Firestore and sync data to Room.
     */
    private suspend fun syncRemoteToLocal() {
        val userId = getCurrentUserId()
        getVehiclesByUserIdFlowUseCase(getCurrentUserId())
            .onEach { remoteVehicles ->
                Log.d(TAG, "Sync: Received ${remoteVehicles.size} vehicles from Firestore.")
                vehicleDao.withTransaction {
                    // A more sophisticated sync can be done here, e.g., using diffs.
                    // For simplicity, this replaces all user vehicles with the remote list.
                    vehicleDao.deleteAllVehiclesByUser(userId)
                    vehicleDao.insertVehicles(remoteVehicles)
                }
            }
            .catch { e ->
                // Log Firestore errors. The UI continues to function with cached data.
                Log.e(TAG, "Sync failed: Firestore error: ${e.message}", e)
            }
            .collect() // This is a terminal operator that keeps the flow alive.
    }

    /**
     * Retrieves a single vehicle by its ID.
     * Implements a cache-first-then-network strategy.
     *
     * @param vehicleId The ID of the vehicle to retrieve.
     * @return A nullable [Vehicle]. Returns null if not found in cache or remote.
     */
    suspend fun getVehicleById(vehicleId: String): Vehicle? {
        // 1. Try to get from local cache first.
        val localVehicle = vehicleDao.getVehicleById(vehicleId)
        if (localVehicle != null) {
            Log.d(TAG, "getVehicleById: Found '$vehicleId' in local cache.")
            return localVehicle
        }

        // 2. If not in cache, fetch from remote.
        Log.d(TAG, "getVehicleById: Not in cache, fetching '$vehicleId' from remote.")
        return try {
            val remoteVehicle = getVehicleByIdUseCase(vehicleId)
            remoteVehicle?.let {
                // 3. If found remotely, update the cache and return it.
                vehicleDao.insertVehicle(it)
            }
            remoteVehicle
        } catch (e: Exception) {
            Log.e(TAG, "getVehicleById: Error fetching from remote", e)
            null // Return null on remote fetch error
        }
    }


    /**
     * Inserts a new vehicle. Writes to remote ONLY.
     * The running Firestore listener in `syncRemoteToLocal` will automatically handle
     * updating the local Room cache.
     * Throws an exception on failure.
     *
     * @param vehicle The [Vehicle] to insert.
     * @return The inserted [Vehicle], now including the userId.
     */
    suspend fun insertVehicle(vehicle: Vehicle): Vehicle {
        val vehicleWithUser = vehicle.copy(userId = getCurrentUserId())
        // Remote first
        insertVehicleUseCase(vehicleWithUser)
        return vehicleWithUser
    }

    /**
     * Updates an existing vehicle. Writes to remote ONLY.
     * The listener in `syncRemoteToLocal` will update the local Room cache.
     * Throws an exception on failure.
     *
     * @param vehicle The [Vehicle] with updated info.
     * @return The updated [Vehicle].
     */
    suspend fun updateVehicle(vehicle: Vehicle): Vehicle {
        // Remote first
        updateVehicleUseCase(vehicle)
        return vehicle
    }

    /**
     * Deletes a vehicle by its ID. Deletes from remote only.
     * Throws an exception on failure.
     *
     * @param vehicleId The ID of the vehicle to delete.
     */
    suspend fun deleteVehicle(vehicleId: String) {
        val vehicle = vehicleDao.getVehicleById(vehicleId) ?: return
        // Remote first
        deleteVehicleUseCase(vehicleId)
    }

    /**
     * Deletes multiple vehicles by their IDs. Deletes from remote only.
     * Throws an exception on failure.
     *
     * @param vehicleIds A list of vehicle IDs to delete.
     */
    suspend fun deleteVehicles(vehicleIds: List<String>) {
        // Remote first
        deleteBulkDataByIdsUseCase(Vehicle.COLLECTION, vehicleIds) // Assuming collection name
        // If remote succeeds, delete from local cache
        vehicleDao.deleteVehiclesByIds(vehicleIds)
    }

    /**
     * Retrieves the UID of the currently authenticated Firebase user.
     *
     * @return The current user's UID as a String.
     * @throws UserNotAuthenticatedException if no user is currently authenticated.
     */
    private fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid
            ?: throw UserNotAuthenticatedException()
    }

    override fun onStart() {
        TODO("Not yet implemented")
    }

    override fun onDestroy() {
        TODO("Not yet implemented")
    }

    override fun createRecord(record: Vehicle) {
        TODO("Not yet implemented")
    }

    override fun onRecordAdded(record: Vehicle) {
        TODO("Not yet implemented")
    }

    override fun onRecordDeleted(record: Vehicle) {
        TODO("Not yet implemented")
    }

    override fun onRecordCleared() {
        TODO("Not yet implemented")
    }
}