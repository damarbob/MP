package id.monpres.app.module

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.monpres.app.dao.VehicleDao
import id.monpres.app.database.AppDatabase
import id.monpres.app.repository.VehicleRepository
import id.monpres.app.usecase.DeleteBulkDataByIdsUseCase
import id.monpres.app.usecase.DeleteVehicleUseCase
import id.monpres.app.usecase.GetOrderServicesUseCase
import id.monpres.app.usecase.GetVehicleByIdUseCase
import id.monpres.app.usecase.GetVehiclesByUserIdFlowUseCase
import id.monpres.app.usecase.GetVehiclesByUserIdUseCase
import id.monpres.app.usecase.InsertVehicleUseCase
import id.monpres.app.usecase.UpdateVehicleUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /* Room Database */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app_database"
        ).fallbackToDestructiveMigration(false).build()
    }

    /* ================== Firebase ================== */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return Firebase.firestore.apply {
            // Configure Firestore settings if needed
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    /* DAOs */
    @Provides
    fun provideVehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()

    /* Use Cases */
    @Provides
    @Singleton
    fun provideGetVehiclesByUserIdFlowUseCase(firestore: FirebaseFirestore): GetVehiclesByUserIdFlowUseCase =
        GetVehiclesByUserIdFlowUseCase(firestore)

    @Provides
    @Singleton
    fun provideGetVehiclesByUserIdUseCase(firestore: FirebaseFirestore): GetVehiclesByUserIdUseCase =
        GetVehiclesByUserIdUseCase(firestore)

    @Provides
    @Singleton
    fun provideGetVehicleByIdUseCase(firestore: FirebaseFirestore): GetVehicleByIdUseCase =
        GetVehicleByIdUseCase(firestore)

    @Provides
    @Singleton
    fun provideInsertVehicleUseCase(firestore: FirebaseFirestore): InsertVehicleUseCase =
        InsertVehicleUseCase(firestore)

    @Provides
    @Singleton
    fun provideUpdateVehicleUseCase(firestore: FirebaseFirestore): UpdateVehicleUseCase =
        UpdateVehicleUseCase(firestore)

    @Provides
    @Singleton
    fun provideDeleteVehicleUseCase(firestore: FirebaseFirestore): DeleteVehicleUseCase =
        DeleteVehicleUseCase(firestore)

    @Provides
    @Singleton
    fun provideDeleteBulkDataByIdsUseCase(firestore: FirebaseFirestore): DeleteBulkDataByIdsUseCase =
        DeleteBulkDataByIdsUseCase(firestore)

    @Provides
    @Singleton
    fun provideGetOrderServicesUseCase(firestore: FirebaseFirestore): GetOrderServicesUseCase =
        GetOrderServicesUseCase(firestore)

    /* Repositories */
    @Provides
    @Singleton
    fun provideVehicleRepository(
        firebaseAuth: FirebaseAuth,
        vehicleDao: VehicleDao,
        getVehiclesByUserIdFlowUseCase: GetVehiclesByUserIdFlowUseCase,
        getVehiclesByUserIdUseCase: GetVehiclesByUserIdUseCase,
        getVehicleByIdUseCase: GetVehicleByIdUseCase,
        insertVehicleUseCase: InsertVehicleUseCase,
        updateVehicleUseCase: UpdateVehicleUseCase,
        deleteVehicleUseCase: DeleteVehicleUseCase,
        deleteBulkDataByIdsUseCase: DeleteBulkDataByIdsUseCase
    ): VehicleRepository = VehicleRepository(firebaseAuth, vehicleDao, getVehiclesByUserIdFlowUseCase, getVehiclesByUserIdUseCase, getVehicleByIdUseCase, insertVehicleUseCase, updateVehicleUseCase, deleteVehicleUseCase, deleteBulkDataByIdsUseCase)
}