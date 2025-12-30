package id.monpres.app.model

import com.google.firebase.firestore.DocumentSnapshot

data class PagedOrderResult(
    val data: List<OrderService>,
    val lastSnapshot: DocumentSnapshot?
)
