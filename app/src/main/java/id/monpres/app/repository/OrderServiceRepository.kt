package id.monpres.app.repository

import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import id.monpres.app.model.OrderService
import id.monpres.app.usecase.GetDataByUserIdUseCase
import id.monpres.app.usecase.ObserveCollectionByIdUseCase
import id.monpres.app.usecase.ObserveCollectionByUserIdUseCase
import id.monpres.app.utils.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderServiceRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val observeCollectionByUserIdUseCase: ObserveCollectionByUserIdUseCase,
    private val observeCollectionByIdUseCase: ObserveCollectionByIdUseCase,
    private val getDataByUserIdUseCase: GetDataByUserIdUseCase,

    ) : Repository<OrderService>() {
    companion object {
        const val TAG = "OrderServiceRepository"
    }

    fun observeOrderServicesByUserId(): Flow<UiState<List<OrderService>>> =
        observeCollectionByUserIdUseCase(
            getCurrentUserId(),
            OrderService.COLLECTION, OrderService::class.java
        )
            .distinctUntilChanged()
            .map<List<OrderService?>?, UiState<List<OrderService>>> { orderServices ->
                UiState.Success(orderServices?.mapNotNull { it } ?: throw NullPointerException())
            }
            .onStart { emit(UiState.Loading) }
            .catch { e ->
                emit(UiState.Error(e))
            }
            .flowOn(Dispatchers.IO)

    suspend fun observeOrderServiceById(id: String): Flow<UiState<OrderService>> {
        val orderServices = getOrderServiceByUserId()
        Log.d(TAG, "OrderServices: $orderServices")
        Log.d(TAG, "id: $id")
        Log.d(TAG, "userId: ${getCurrentUserId()}")
        val isOwned = orderServices?.any {
            it.id == id
            it.userId == getCurrentUserId()
        } ?: false
        return if (orderServices?.isEmpty() == true)
            flowOf(UiState.Error(NullPointerException()))
        else if (!isOwned)
            flowOf(UiState.Error(Exception()))
        else
            observeCollectionByIdUseCase(
                id,
                OrderService.COLLECTION, OrderService::class.java
            )
                .distinctUntilChanged()
                .map<OrderService?, UiState<OrderService>> { orderService ->
                    UiState.Success(
                        orderService ?: throw NullPointerException()
                    )
                }
                .onStart { emit(UiState.Loading) }
                .catch { e ->
                    emit(UiState.Error(e))
                }
                .flowOn(Dispatchers.IO)
    }

    suspend fun getOrderServiceByUserId() = getDataByUserIdUseCase(
        getCurrentUserId(),
        OrderService.COLLECTION,
        OrderService::class.java
    )

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

    override fun createRecord(record: OrderService) {
        TODO("Not yet implemented")
    }

    override fun onRecordAdded(record: OrderService) {
        TODO("Not yet implemented")
    }

    override fun onRecordDeleted(record: OrderService) {
        TODO("Not yet implemented")
    }

    override fun onRecordCleared() {
        TODO("Not yet implemented")
    }
}