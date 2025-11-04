package id.monpres.app.state

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    object Empty : UiState<Nothing>()

//    fun isLoading() = this is Loading
//    fun isSuccess() = this is Success
//    fun isError() = this is Error
}