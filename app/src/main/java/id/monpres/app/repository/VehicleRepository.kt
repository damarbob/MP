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
import id.monpres.app.utils.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
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

    /**
     * Retrieves a flow of vehicle lists for the current user, providing real-time updates.
     *
     * This function implements a robust data synchronization strategy:
     * 1. **Real-time Firestore Updates:** It establishes a Flow that listens to real-time changes
     *    in the user's vehicles collection in Firestore. When Firestore data changes,
     *    this flow emits the new list of vehicles.
     * 2. **Room Database Cache:** Upon receiving data from Firestore, it updates the local Room
     *    database. This involves deleting all existing vehicles for the current user and
     *    inserting the new ones within a single database transaction to ensure atomicity.
     *    This strategy assumes a full replacement is acceptable; for more complex scenarios,
     *    a more sophisticated sync logic (e.g., upsert, selective delete) might be needed.
     * 3. **UI Observes Room:** The primary Flow returned to the UI observes the Room database.
     *    Room automatically emits new data whenever its underlying tables change (due to
     *    the Firestore updates). This ensures the UI always displays the latest cached data.
     * 4. **Error Handling:**
     *    - Firestore errors are caught and logged. They do not stop the Room observation,
     *      allowing the UI to continue functioning with cached data.
     *    - Room Flow errors are caught and emitted as `UiState.Error`.
     * 5. **Lifecycle and Dispatchers:**
     *    - Firestore operations and Room updates are performed on the `Dispatchers.IO` thread.
     *    - The `combine` operator is used to ensure the Firestore real-time listener
     *      (`firestoreRealtimeFlow`) remains active as long as the Room Flow is being collected
     *      by the UI.
     *
     * The Flow emits `UiState` to represent different states:
     * - `UiState.Loading`: Emitted when the flow starts.
     * - `UiState.Success`: Emitted with the list of vehicles from Room.
     * - `UiState.Error`: Emitted if an error occurs while observing Room.
     *
     * @return A [Flow] of [UiState] wrapping a list of [Vehicle] objects.
     */
    fun getVehiclesByUserIdFlow(): Flow<UiState<List<Vehicle>>> { // Return List<Vehicle>, not List<Vehicle?>
        val userId = getCurrentUserId()

        // 1. Set up a listener for real-time updates from Firestore.
        // This Flow should stay active and emit new lists whenever Firestore data changes.
        val firestoreRealtimeFlow =
            getVehiclesByUserIdFlowUseCase(userId) // Assuming this is now a real-time Flow
                .onEach { remoteVehicles ->
                    Log.d("VehicleRepository", "Fetched vehicles from Firestore: $remoteVehicles")
                    // Strategically update Room. Don't just delete and insert.
                    // You might need a more sophisticated sync logic (upsert, delete missing)
                    // For simplicity here, let's assume a full replace is acceptable if your use case allows.
                    vehicleDao.withTransaction { // Perform as a single transaction
                        vehicleDao.deleteAllVehiclesByUser(userId) // Delete only for this user
                        vehicleDao.insertVehicles(remoteVehicles)
                    }
                }
                .catch { e ->
                    // Handle Firestore errors, maybe emit an error state to the UI
                    // or log, but don't necessarily stop the Room observation.
                    Log.e("VehicleRepository", "Error: ${e.message}")
                    // Optionally, you could emit an error that the UI can show,
                    // while still relying on the Room cache.
                }
                .flowOn(Dispatchers.IO) // Perform Firestore operations and Room updates on IO dispatcher

        // 2. The main Flow observed by the UI will be directly from Room.
        // Room will automatically emit new lists when its data changes (due to Firestore updates).
        return vehicleDao.getVehiclesByUserFlow(userId) // This should return Flow<List<Vehicle>>
            .distinctUntilChanged()
            .map<List<Vehicle?>?, UiState<List<Vehicle>>> { vehicles -> // Assuming getVehiclesByUserFlow returns List<Vehicle?>
                UiState.Success(vehicles?.mapNotNull { it } ?: listOf())
            }
            .onStart { emit(UiState.Loading) }
            .catch { e ->
                // This catches errors from the Room Flow itself
                emit(UiState.Error(e))
            }
            .combine(firestoreRealtimeFlow.map { }.catch { }
                .onStart { emit(Unit) }) { roomData, _ ->
                // This combine is just to ensure the firestoreRealtimeFlow is collected
                // and running in the background as long as the Room flow is collected.
                // We only care about the roomData for emission.
                roomData
            }
            .flowOn(Dispatchers.IO) // Room operations often benefit from IO dispatcher too
    }

    /**
     * Retrieves a flow of vehicle lists for a specific user.
     *
     * This function first emits `UiState.Loading`. It then observes the local database
     * for vehicles belonging to the given `userId`. Concurrently, it attempts to fetch
     * the latest vehicles from the remote server and updates the local database with
     * the fetched data.
     *
     * The flow will emit `UiState.Success` with the list of vehicles from the local
     * database whenever the data changes. If an error occurs during the local database
     * observation, it will emit `UiState.Error`.
     *
     * Errors during the remote fetch are logged but do not interrupt the flow of
     * local data, ensuring the UI can still display cached information.
     *
     * @return A [Flow] of [UiState] wrapping a list of [Vehicle] objects.
     *         The list will not contain null [Vehicle] objects.
     */
    fun getVehiclesByUserId(): Flow<UiState<List<Vehicle>>> { // Note: Vehicle, not Vehicle? if local never stores nulls in the list
        val userId = getCurrentUserId()
        return vehicleDao.getVehiclesByUserFlow(userId) // Assume this DAO method returns Flow<List<Vehicle>>
            .distinctUntilChanged() // Keep this
            .map<List<Vehicle>, UiState<List<Vehicle>>> { vehicles -> // Map the DB Flow to UiState
                UiState.Success(vehicles)
            }
            .onStart {
                emit(UiState.Loading) // Emit Loading at the beginning
                try {
                    // Fetch from remote and update DAO in the background
                    val remoteVehicles = getVehiclesByUserIdUseCase(userId)
                    remoteVehicles?.let { vehicleDao.insertVehicles(it) }
                } catch (e: Exception) {
                    // Optionally emit an error here if remote fetch fails, but local data will still be flowing
                    // emit(UiState.Error("Failed to sync with remote: ${e.message}"))
                    Log.e("VehicleRepo", "Error fetching remote vehicles: ${e.message}")
                }
            }
            .catch {
                emit(UiState.Error(it))
            }
            .flowOn(Dispatchers.IO)
    }


    /**
     * Retrieves a vehicle by its ID.
     *
     * This function first attempts to fetch the vehicle from the local database.
     * If the vehicle is found locally, it emits a [UiState.Success] with the vehicle.
     * If the vehicle is not found locally, it attempts to fetch it from the remote data source.
     * If the vehicle is found remotely, it is inserted into the local database and then emitted
     * as a [UiState.Success].
     * If the vehicle is not found either locally or remotely, it emits a [UiState.Error].
     *
     * The flow emits [UiState.Loading] initially and [UiState.Error] if any exception occurs
     * during the process. All operations are performed on the IO dispatcher.
     *
     * @param vehicleId The ID of the vehicle to retrieve.
     * @return A [Flow] of [UiState] that emits the state of the vehicle retrieval operation.
     *         The [UiState] will contain a [Vehicle] object if found.
     */
    fun getVehicleById(vehicleId: String): Flow<UiState<Vehicle>> {
        return flow {
            emit(UiState.Loading)

            val localVehicle = vehicleDao.getVehicleById(vehicleId) // Fetch once

            if (localVehicle != null) {
                emit(UiState.Success(localVehicle)) // Use the fetched localVehicle
            } else {
                val remoteVehicle = getVehicleByIdUseCase(vehicleId)
                remoteVehicle?.let { vehicle ->
                    vehicleDao.insertVehicle(vehicle)
                    emit(UiState.Success(vehicle))
                } ?: emit(UiState.Error(NullPointerException())) // Emit null if remote is also null
            }
        }.catch {
            emit(UiState.Error(it))
        }.flowOn(Dispatchers.IO)
    }


    /**
     * Inserts a new vehicle into the repository.
     *
     * This function first attempts to save the vehicle data to the remote data source (Firebase).
     * After a successful remote insertion, it updates the local database to mark the vehicle as synced.
     * The process is wrapped in a Flow that emits [UiState] to represent the current state:
     * - [UiState.Loading]: When the insertion process starts.
     * - [UiState.Success]: When the vehicle is successfully inserted both remotely and locally. The success state contains the inserted [Vehicle] object.
     * - [UiState.Error]: If any error occurs during the process.
     *
     * The user ID of the current authenticated user is automatically added to the vehicle before insertion.
     * All operations are performed on the IO dispatcher.
     *
     * @param vehicle The [Vehicle] object to be inserted. The `userId` field will be automatically populated.
     * @return A [Flow] of [UiState] indicating the status of the insertion operation,
     *         emitting the inserted [Vehicle] on success.
     * @throws UserNotAuthenticatedException if no user is currently authenticated.
     */
    fun insertVehicle(vehicle: Vehicle): Flow<UiState<Vehicle>> {
        return flow {
            emit(UiState.Loading)
            val vehicleWithUser = vehicle.copy(userId = getCurrentUserId())

            // Try to save to remote first
            insertVehicleUseCase(vehicleWithUser) // Send the vehicle intended for remote

            // Update local to mark as synced
            vehicleDao.updateVehicle(vehicleWithUser)
            emit(UiState.Success(vehicleWithUser))

        }.catch {
            it.printStackTrace()
            emit(UiState.Error(it))
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Updates an existing vehicle both in the remote data source and the local database.
     *
     * This function follows a "remote first" approach:
     * 1. Emits [UiState.Loading] to indicate the start of the operation.
     * 2. Attempts to update the vehicle data in the remote data source using [updateVehicleUseCase].
     * 3. Updates the vehicle data in the local Room database via [vehicleDao].
     * 4. Emits [UiState.Success] with the updated vehicle if both operations are successful.
     *
     * If any error occurs during the process, it catches the exception, prints the stack trace,
     * and emits [UiState.Error] with the caught throwable.
     *
     * The entire flow operates on the [Dispatchers.IO] context.
     *
     * @param vehicle The [Vehicle] object containing the updated information.
     * @return A [Flow] of [UiState] that emits the current state of the update operation,
     *         culminating in either a [UiState.Success] with the updated [Vehicle] or a [UiState.Error].
     */
    fun updateVehicle(vehicle: Vehicle): Flow<UiState<Vehicle>> = flow {
        emit(UiState.Loading)

        // Remote first
        updateVehicleUseCase(vehicle)
        vehicleDao.updateVehicle(vehicle)
        emit(UiState.Success(vehicle))

    }.catch {
        it.printStackTrace()
        emit(UiState.Error(it))
    }.flowOn(Dispatchers.IO)

    /**
     * Deletes a vehicle by its ID.
     *
     * This function first attempts to delete the vehicle from the remote data source.
     * If successful, it then deletes the vehicle from the local database.
     *
     * The function emits [UiState] to represent the different states of the operation:
     * - [UiState.Loading]: When the operation starts.
     * - [UiState.Success]: If the vehicle is successfully deleted. The value will be the deleted [Vehicle].
     * - [UiState.Error]: If the vehicle is not found locally, or if an error occurs during the remote or local deletion process.
     *
     * @param vehicleId The ID of the vehicle to delete.
     * @return A [Flow] of [UiState] that emits the state of the deletion operation.
     *         The [UiState.Success] will contain the deleted [Vehicle], or `null` if the vehicle was not found (though current implementation emits Error for not found).
     *         The [UiState.Error] will contain the exception that occurred.
     */
    fun deleteVehicle(vehicleId: String): Flow<UiState<Vehicle?>> { // Return Vehicle? as it might not exist
        return flow {
            emit(UiState.Loading)
            val userVehicles = vehicleDao.getVehiclesByUser(getCurrentUserId())
            val vehicle = userVehicles.find { it.id == vehicleId }

            if (vehicle == null) {
                emit(UiState.Error(NullPointerException())) // Or Success(null) if that's preferred
                return@flow
            }

            // Remote first
            deleteVehicleUseCase(vehicleId)
            vehicleDao.deleteVehicle(vehicle)
            emit(UiState.Success(vehicle)) // Vehicle was successfully deleted
        }
            .catch { // More specific exception handling if possible
                Log.e("VehicleRepo", "Failed to delete vehicle $vehicleId remotely: ${it.message}")
                // Decide what to emit here. Error or Success with the updated local state?
                // Emitting Error might be confusing if the local update succeeded.
                // Perhaps a specific UiState for "Pending Deletion"
                emit(UiState.Error(it)) // Or a custom state
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Deletes a list of vehicles by their IDs.
     *
     * This function first retrieves all vehicles belonging to the current user from the local database.
     * It then filters this list to find the vehicles that match the provided `vehicleIds`.
     * These matching vehicles are then deleted from the remote data source (e.g., Firestore) first,
     * followed by deletion from the local database.
     *
     * The function emits [UiState] to represent the state of the operation:
     * - [UiState.Loading]: Emitted at the start of the operation.
     * - [UiState.Success]: Emitted when the vehicles are successfully deleted, containing the list of deleted vehicles.
     * - [UiState.Error]: Emitted if an error occurs during the deletion process.
     *
     * All operations are performed on the [Dispatchers.IO] thread.
     *
     * @param vehicleIds A list of vehicle IDs (Strings) to be deleted.
     * @return A [Flow] that emits [UiState] representing the progress and result of the deletion.
     *         The [UiState.Success] will contain the list of [Vehicle] objects that were deleted.
     */
    fun deleteVehicles(vehicleIds: List<String>): Flow<UiState<List<Vehicle>>> {
        return flow {
            emit(UiState.Loading)

            // Get the user's vehicles first
            val userVehicles = vehicleDao.getVehiclesByUser(getCurrentUserId())

            // Filter out the vehicles to delete
            val vehiclesForDelete = userVehicles.filter { it.id in vehicleIds }

            // Remote first
            deleteBulkDataByIdsUseCase(Vehicle.COLLECTION, vehiclesForDelete.map { it.id })

            // Delete local
            vehicleDao.deleteVehiclesByIds(vehiclesForDelete.map { it.id })
            emit(UiState.Success(vehiclesForDelete))
        }
            .onStart {
                emit(UiState.Loading)
            }
            .catch {
                it.printStackTrace()
                emit(UiState.Error(it))
            }
            .flowOn(Dispatchers.IO)
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