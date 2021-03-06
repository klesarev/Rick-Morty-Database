package com.shevaalex.android.rickmortydatabase.repository.init

import android.content.SharedPreferences
import com.shevaalex.android.rickmortydatabase.models.episode.EpisodeEntity
import com.shevaalex.android.rickmortydatabase.source.local.EpisodeDao
import com.shevaalex.android.rickmortydatabase.source.remote.EpisodeApi
import com.shevaalex.android.rickmortydatabase.utils.Constants
import com.shevaalex.android.rickmortydatabase.utils.networking.ApiResult
import timber.log.Timber
import javax.inject.Inject

class EpisodeInitManagerImpl
@Inject
constructor(
        private val episodeDao: EpisodeDao,
        private val episodeApi: EpisodeApi,
        private val sharedPref: SharedPreferences
) : InitManager<EpisodeEntity> {

    override fun getSharedPrefsKey() = Constants.KEY_INIT_VM_EPISODES_FETCHED_TIMESTAMP

    override suspend fun getNetworkCountApiResult(token: String) =
            episodeApi.getEpisodeList(idToken = token, isShallow = true)

    override suspend fun getListFromNetwork(token: String): ApiResult<List<EpisodeEntity?>> {
        Timber.i("fetching episodes from the rest api...")
        return episodeApi.getEpisodeList(idToken = token)
    }

    override suspend fun getObjectCountDb() = episodeDao.episodesCount()

    override suspend fun filterNetworkList(networkList: List<EpisodeEntity>): List<EpisodeEntity> {
        val filteredList = networkList.filter {
            val episodeFromDb = episodeDao.getEpisodeByIdSuspend(it.id)
            it != episodeFromDb
        }
        Timber.i("refetched episodes filtered list size: ${filteredList.size}")
        return filteredList
    }

    override suspend fun saveNetworkListToDb(networkList: List<EpisodeEntity>) {
        if (networkList.isNotEmpty()) {
            Timber.i("saving episodes to DB: first episode id=[%d], last episode id=[%d]",
                    networkList[0].id,
                    networkList.last().id)
        } else Timber.e("saving episodes to DB: episode list is empty")
        episodeDao.insertEpisodes(networkList)
    }

    override fun getSharedPrefs() = sharedPref

}