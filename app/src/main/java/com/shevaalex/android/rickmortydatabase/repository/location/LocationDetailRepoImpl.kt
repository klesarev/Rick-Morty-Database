package com.shevaalex.android.rickmortydatabase.repository.location

import androidx.lifecycle.LiveData
import com.shevaalex.android.rickmortydatabase.models.character.CharacterEntity
import com.shevaalex.android.rickmortydatabase.source.local.CharacterDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationDetailRepoImpl
@Inject
constructor(
        private val characterDao: CharacterDao
) : LocationDetailRepository {

    override fun getCharacters(characterIds: List<Int>): LiveData<List<CharacterEntity>> =
            characterDao.getCharactersByIds(characterIds)

}