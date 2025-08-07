package id.monpres.app.repository

import android.security.keystore.UserNotAuthenticatedException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.OrderService
import id.monpres.app.usecase.ObserveCollectionByUserIdUseCase
import id.monpres.app.utils.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class OrderServiceRepository @Inject constructor(
    firestore: FirebaseFirestore,
    val firebaseAuth: FirebaseAuth
) {
    val observeCollectionByUserIdUseCase: ObserveCollectionByUserIdUseCase =
        ObserveCollectionByUserIdUseCase(firestore)

    fun observeOrderServiceByUserId(): Flow<UiState<List<OrderService>>> =
        observeCollectionByUserIdUseCase(
            getCurrentUserId(),
            "orderServices", OrderService::class.java
        )
            .distinctUntilChanged()
            .map<List<OrderService?>?, UiState<List<OrderService>>> { orderServices ->
                UiState.Success(orderServices?.mapNotNull { it } ?: listOf())
            }
            .onStart { emit(UiState.Loading) }
            .catch { e ->
                emit(UiState.Error(e))
            }
            .flowOn(Dispatchers.IO)

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
}