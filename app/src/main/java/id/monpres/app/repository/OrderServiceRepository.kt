package id.monpres.app.repository

import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import id.monpres.app.model.OrderService
import id.monpres.app.usecase.ObserveCollectionByFieldUseCase
import id.monpres.app.usecase.ObserveCollectionByUserIdUseCase
import id.monpres.app.usecase.ObserveCollectionUseCase
import id.monpres.app.usecase.UpdateDataByIdUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderServiceRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val observeCollectionByUserIdUseCase: ObserveCollectionByUserIdUseCase,
    private val observeCollectionByFieldUseCase: ObserveCollectionByFieldUseCase,
    private val observeCollectionUseCase: ObserveCollectionUseCase,
    private val updateDataByIdUseCase: UpdateDataByIdUseCase
) : Repository<OrderService>() {
    companion object {
        val TAG = OrderServiceRepository::class.simpleName
    }

    /**
     * Observes all OrderServices in the collection without filtering by user.
     * Useful for admin views or public feeds.
     */
    fun observeOrderServices(): Flow<List<OrderService>> =
        observeCollectionUseCase(
            OrderService.COLLECTION,
            OrderService::class.java
        )
            .mapNotNull { orderServices ->
                // Clean up any nulls that might come from Firestore and cache locally
                setRecords(orderServices, false)
                orderServices
            }
            .distinctUntilChanged()
            .catch {
                Log.e(TAG, "Error observing all order services", it)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)

    // TODO: Use observeOrderServicesByPartnerId for consistency
    fun observeOrderServicesByUserId(): Flow<List<OrderService>> =
        observeCollectionByUserIdUseCase(
            getCurrentUserId(),
            OrderService.COLLECTION, OrderService::class.java
        )
            .mapNotNull { orderServices ->
                // Clean up any nulls that might come from Firestore
                setRecords(orderServices, false)
                orderServices
            }
            .distinctUntilChanged()
            .catch {
                Log.e(TAG, "Error observing order services", it)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO) // Run the collection and mapping on an IO thread

    fun observeOrderServicesByPartnerId(): Flow<List<OrderService>> =
        observeCollectionByFieldUseCase(
            OrderService.PARTNER_ID,
            getCurrentUserId(),
            OrderService.COLLECTION, OrderService::class.java
        )
            .mapNotNull { orderServices ->
                setRecords(orderServices, false)
                orderServices
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun getOrderServiceById(id: String): OrderService? = getRecords().find { it.id == id }

    /**
     * Best Practice: This is a one-shot write operation. It should be a simple suspend function.
     * It will throw an exception on failure, which the ViewModel will catch.
     */
    suspend fun updateOrderService(orderService: OrderService): OrderService {
        val id = orderService.id ?: throw IllegalArgumentException("OrderService ID cannot be null")
        updateDataByIdUseCase(id, OrderService.COLLECTION, orderService)
        return orderService
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

    }
}
