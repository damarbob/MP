package id.monpres.app.ui.baseservice

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.firestore
import com.mapbox.geojson.Point
import id.monpres.app.model.OrderService

abstract class BaseServiceViewModel : ViewModel() {
    protected val _selectedLocationPoint = MutableLiveData<Point?>(null)
    val selectedLocationPoint: LiveData<Point?> get() = _selectedLocationPoint
    protected val _userLocationPoint = MutableLiveData<Point?>(null)
    val userLocationPoint: LiveData<Point?> get() = _userLocationPoint
    protected val _placeOrderResult = MutableLiveData<Result<OrderService?>>()
    val placeOrderResult: LiveData<Result<OrderService?>> get() = _placeOrderResult

    fun setSelectedLocationPoint(point: Point) {
        _selectedLocationPoint.value = point
    }

    fun setUserLocationPoint(point: Point) {
        _userLocationPoint.value = point
    }

    open fun placeOrder(orderService: OrderService) {
        val firestore = com.google.firebase.Firebase.firestore

        val docRef = firestore.collection("orderServices").document()

        orderService.id = docRef.id

        docRef
            .set(orderService)
            .addOnCompleteListener { task ->
                if (task.isComplete) {
                    if (task.isSuccessful) {
                        // Handle success
                        _placeOrderResult.postValue(Result.success(orderService))
                    } else {
                        // Handle failure
                        _placeOrderResult.postValue(
                            Result.failure(
                                task.exception ?: Exception("Unknown error")
                            )
                        )
                    }
                }
            }
    }
}