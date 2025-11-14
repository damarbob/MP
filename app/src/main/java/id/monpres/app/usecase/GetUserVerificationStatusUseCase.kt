package id.monpres.app.usecase

import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetUserVerificationStatusUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    /**
     * Returns a Flow that emits the user's current admin verification status.
     * It maps a null user or null status to PENDING, as this is the default state.
     */
    operator fun invoke(): Flow<UserVerificationStatus> {
        return userRepository.userRecord.map { user ->
            // If user is null, we can't know the status, but for logic flow,
            // we treat it as PENDING until the user record is loaded.
            // If user.verificationStatus is null, it's also treated as PENDING.
            user?.verificationStatus ?: UserVerificationStatus.PENDING
        }
    }
}