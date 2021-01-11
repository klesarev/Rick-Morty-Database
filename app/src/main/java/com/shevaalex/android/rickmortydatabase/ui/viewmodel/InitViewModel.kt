package com.shevaalex.android.rickmortydatabase.ui.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.*
import com.shevaalex.android.rickmortydatabase.auth.AuthManager
import com.shevaalex.android.rickmortydatabase.models.AuthToken
import com.shevaalex.android.rickmortydatabase.repository.init.InitRepository
import com.shevaalex.android.rickmortydatabase.utils.Constants
import com.shevaalex.android.rickmortydatabase.utils.Constants.Companion.AUTH_TOKEN_REFRESH_TIME
import com.shevaalex.android.rickmortydatabase.utils.Constants.Companion.KEY_ACTIVITY_MAIN_DB_SYNCED_TIMESTAMP
import com.shevaalex.android.rickmortydatabase.utils.NetworkAndTokenMediatorLiveData
import com.shevaalex.android.rickmortydatabase.utils.currentTimeHours
import com.shevaalex.android.rickmortydatabase.utils.currentTimeMinutes
import com.shevaalex.android.rickmortydatabase.utils.networking.Message
import com.shevaalex.android.rickmortydatabase.utils.networking.StateResource
import com.shevaalex.android.rickmortydatabase.utils.networking.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.flow.collect


class InitViewModel
@Inject
constructor(
        private val initRepository: InitRepository,
        private val sharedPref: SharedPreferences,
        private val authManager: AuthManager
) : ViewModel() {

    private val isNetworkAvailable = MutableLiveData<Boolean>()

    private val authIdToken: MutableLiveData<AuthToken> = MutableLiveData()

    private val mediatorLiveData = NetworkAndTokenMediatorLiveData(isNetworkAvailable, authIdToken)

    //emits dummy livedata with network error (no internet connection)
    private val noInternetError: LiveData<StateResource> = liveData {
        emit(StateResource(Status.Error, Message.NoInternet))
    }

    //emits dummy livedata with loading status
    private val loadingStatus: LiveData<StateResource> = liveData {
        emit(StateResource(Status.Loading))
    }

    init {
        viewModelScope.launch {
            /*
            waits 1sec and sets isNetworkAvailable to false (starting an app in a flight mode
            or with internet switched off doesn't trigger ConnectivityManager.NetworkCallback()
            in ConnectionLiveData to post any value
            */
            delay(1000)
            if (isNetworkAvailable.value == null) {
                isNetworkAvailable.value = false
            }
            // set auth token
            authManager.token.collect {
                Timber.v("collecting token in viewmodel, token: %s", it?.token?.takeLast(7))
                authIdToken.value = it
            }
        }
    }

    fun init(): LiveData<StateResource> = Transformations.switchMap(mediatorLiveData) {
        val isNetworkAvailable = it.first
        val token = it.second
        if (isNetworkAvailable) {
            Timber.i("CONNECTED")
            token?.let { authToken ->
                //if token has expired - refetch and emit loadingStatus
                if (hasTokenExpired(authToken)) {
                    Timber.e("init() call, token has expired")
                    refetchAuthToken()
                    loadingStatus
                } else {
                    initRepository.getDbStateResource(authToken.token)
                }
            }?: run{
                //if token is null - refetch and emit loadingStatus
                Timber.e("init() call, token is null")
                refetchAuthToken()
                loadingStatus
            }
        } else {
            Timber.e("DISCONNECTED")
            noInternetError
        }
    }

    fun isNetworkAvailable(isConnected: Boolean) {
        if (isNetworkAvailable.value != isConnected) {
            isNetworkAvailable.value = isConnected
        }
    }

    /**
     * is called when observer receives db sync success status
     */
    fun notifyDbAllSuccess() {
        saveTimestampToSharedPrefs()
    }

    /**
     * @return true if currentTimeHrs - lastSynced is more than Const.DB_CHECK_PERIOD (hours)
     */
    fun isDbCheckNeeded(): Boolean {
        val lastSynced = sharedPref.getInt(KEY_ACTIVITY_MAIN_DB_SYNCED_TIMESTAMP, 0)
        val currentTimeHrs = currentTimeHours().toInt()
        Timber.d(
                "getLastTimeSynced, lastSync: %s, currentTimeHrs: %s, diff: %s, isDbCheckNeeded:%s",
                lastSynced,
                currentTimeHrs,
                currentTimeHrs - lastSynced,
                currentTimeHrs - lastSynced > Constants.DB_CHECK_PERIOD
        )
        return currentTimeHrs - lastSynced > Constants.DB_CHECK_PERIOD
    }

    /**
     * save the timestamp with the time when database was synced successfuly (only list size check)
     */
    private fun saveTimestampToSharedPrefs() {
        with(sharedPref.edit()) {
            val currentTimeHrs = currentTimeHours().toInt()
            Timber.d("saving to share prefs timestamp: %s", currentTimeHrs)
            putInt(KEY_ACTIVITY_MAIN_DB_SYNCED_TIMESTAMP, currentTimeHrs)
            apply()
        }
    }

    private fun hasTokenExpired(authToken: AuthToken): Boolean {
        val expired = currentTimeMinutes() - authToken.timestamp > AUTH_TOKEN_REFRESH_TIME
        Timber.v("hasTokenExpired: current time=[%s] token timestamp=[%s] diff=[%s]mins expired=%s",
                currentTimeMinutes(),
                authToken.timestamp,
                currentTimeMinutes() - authToken.timestamp,
                expired)
        return expired
    }

    private fun refetchAuthToken() {
        viewModelScope.launch {
            authManager.getNewToken()
        }
    }

}