package id.monpres.app.model

import com.google.firebase.firestore.DocumentSnapshot

data class UserPageResult(
    val data: List<MontirPresisiUser>,
    val lastSnapshot: DocumentSnapshot?
)
