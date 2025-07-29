package id.monpres.app.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Repository base template.
 * @param <T> repository type
</T> */
abstract class Repository<T> {
    private val records = ArrayList<T>()
    private val recordsCount = MutableLiveData(0)
    /**
     * Get index of the last record position from the repository
     * @return int of last record position index
     */
    /**
     * Set the last inserted record index
     * @param index int index
     */
    var lastRecordIndex = 0

    /**
     * Start repository, default implementation is registering to [EventBus].
     */
    abstract fun onStart()

    /**
     * End repository, default implementation is unregistering the [EventBus]
     */
    abstract fun onDestroy()

    /**
     * Obtain the records from repository in form of [ArrayList]
     * @return [List] of [T]
     */
    fun getRecords(): MutableList<T> {
        return records
    }

    /**
     * Add a record to repository
     * @param record [T] instance
     */
    fun addRecord(record: T) {
        lastRecordIndex++
        records.add(record)
        updateRecordsCount()
        onRecordAdded(record)
    }

    abstract fun createRecord(record: T)

    /**
     * Delete a record to repository
     * @param record [T] instance
     */
    fun deleteRecord(record: T) {
        records.remove(record)
        updateRecordsCount()
        onRecordDeleted(record)
    }

    /**
     * Clear all records from repository
     */
    fun clearRecord() {
        records.clear()
        updateRecordsCount()
        onRecordCleared()
    }

    /**
     * Clear all records and replace a new one
     * @param records [ArrayList] of new records
     * @param invokeCallback whether to show callback using the new addRecord() method or using original [ArrayList] addAll() method instead
     */
    fun setRecords(records: List<T>, invokeCallback: Boolean?) {
        getRecords().clear()
        if (invokeCallback == null || !invokeCallback) {
            getRecords().addAll(records) // Will NOT trigger any events
            return
        }

        // New method will use addRecord instead
        for (record in records) {
            addRecord(record) // Will also trigger onRecordAdded
        }
    }

    abstract fun onRecordAdded(record: T)
    abstract fun onRecordDeleted(record: T)
    abstract fun onRecordCleared()
    fun getRecordByIndex(i: Int): T? {
        return if (getRecordsCount() > 0) records[i] else null
    }

    /**
     * Get the total records count in the repository
     * @return int of total records count
     */
    fun getRecordsCount(): Int {
        return records.size
    }

    /**
     * Updates the count observable value to match current records count
     */
    private fun updateRecordsCount() {
        recordsCount.postValue(getRecordsCount())
    }

    val recordsCountObservable: LiveData<Int>
        /**
         * Get the observable of records count
         * @return [LiveData] observable
         */
        get() = recordsCount

    companion object {
        private val TAG = Repository::class.java.simpleName
    }
}