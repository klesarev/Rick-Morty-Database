package com.shevaalex.android.rickmortydatabase.source;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import com.shevaalex.android.rickmortydatabase.source.database.Character;
import com.shevaalex.android.rickmortydatabase.source.database.Episode;
import com.shevaalex.android.rickmortydatabase.source.database.Location;
import com.shevaalex.android.rickmortydatabase.source.database.RickMortyDatabase;
import com.shevaalex.android.rickmortydatabase.source.network.ApiCall;
import com.shevaalex.android.rickmortydatabase.source.network.NetworkDataParsing;
import com.shevaalex.android.rickmortydatabase.utils.AppExecutors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MainRepository {
    private static final String TAG = "MainRepo";
    private static final Object LOCK = new Object();
    private final NetworkDataParsing networkDataParsing;
    private final RickMortyDatabase rmDatabase;
    private final AppExecutors appExecutors;
    private static MainRepository sInstance;
    private Character lastDbCharacter;
    private Character lastNetworkCharacter;
    private Location lastDbLocation;
    private Location lastNetworkLocation;
    private Episode lastDbEpisode;
    private Episode lastNetworkEpisode;
    private int characterEntriesDbCount;
    private int locationEntriesDbCount;
    private int episodeEntriesDbCount;
    private boolean dbIsUpToDate;
    private boolean characterTableIsUpToDate;
    private boolean locationTableIsUpToDate;
    private boolean episodeTableIsUpToDate;

    private MainRepository(Application application) {
        this.networkDataParsing = NetworkDataParsing.getInstance(application);
        this.rmDatabase = RickMortyDatabase.getInstance(application);
        this.appExecutors = AppExecutors.getInstance();
        initialiseDataBase();
    }

    public static synchronized MainRepository getInstance(Application application) {
        if (sInstance == null) {
            synchronized (LOCK) {
                Log.d(TAG, "Creating a new repository instance");
                sInstance = new MainRepository(application);
            }
        } else {
            Log.d(TAG, "Getting previous repository object");
        }
        return sInstance;
    }

    public boolean dbIsUpToDate() {   return dbIsUpToDate;    }

    // calls a method to check if database sync/initialisation is needed and fetch data if necessary
    private void initialiseDataBase() {
        fetchLastDbEntries();
        String [] baseUrlArray = {ApiCall.ApiCallCharacterKeys.BASE_URL_CHARACTER_PAGES,
                ApiCall.ApiCallLocationKeys.BASE_URL_LOCATION_PAGES, ApiCall.ApiCallEpisodeKeys.BASE_URL_EPISODE_PAGES};
        for (String url : baseUrlArray) {
            networkInitialCall(url);
        }
    }



    // gets last objects from SQLite database
    private void fetchLastDbEntries() {
        Future<Character> futureCharacter = appExecutors.diskIO().submit(() -> {
            if (rmDatabase.getCharacterDao().showLastInCharacterList() != null) {
                return rmDatabase.getCharacterDao().showLastInCharacterList();
            } else { return null; }
        });
        try { this.lastDbCharacter = futureCharacter.get(); }
        catch (ExecutionException | InterruptedException e) { e.printStackTrace();  }
        Future<Location>  futureLocation = appExecutors.diskIO().submit(() -> {
            if (rmDatabase.getLocationDao().showLastInLocationList() != null) {
                return rmDatabase.getLocationDao().showLastInLocationList();
            } else { return null; }
        });
        try { this.lastDbLocation = futureLocation.get(); }
        catch (ExecutionException | InterruptedException e) {  e.printStackTrace(); }
        Future<Episode> futureEpisode = appExecutors.diskIO().submit(() -> {
            if (rmDatabase.getEpisodeDao().showLastInEpisodeList() != null) {
                return rmDatabase.getEpisodeDao().showLastInEpisodeList();
            } else { return null; }
        });
        try { this.lastDbEpisode = futureEpisode.get(); }
        catch (ExecutionException | InterruptedException e) { e.printStackTrace(); }

        // get number of entries in local database
        Future<Integer> futureCharacterCount = appExecutors.diskIO().submit(() -> rmDatabase.getCharacterDao().getCharacterCount());
        try { this.characterEntriesDbCount = futureCharacterCount.get(); }
        catch (ExecutionException | InterruptedException e) { e.printStackTrace(); }
        Future<Integer> futureLocationCount = appExecutors.diskIO().submit(() -> rmDatabase.getLocationDao().getLocationCount());
        try { this.locationEntriesDbCount = futureLocationCount.get(); }
        catch (ExecutionException | InterruptedException e) { e.printStackTrace(); }
        Future<Integer> futureEpisodeCount = appExecutors.diskIO().submit(() -> rmDatabase.getEpisodeDao().getEpisodeCount());
        try { this.episodeEntriesDbCount = futureEpisodeCount.get(); }
        catch (ExecutionException | InterruptedException e) { e.printStackTrace(); }
    }

    private void networkInitialCall (String url) {
        appExecutors.networkIO().execute(() -> networkDataParsing.getVolleyResponce(response -> {
            try {
                JSONObject jsonObject = response.getJSONObject(ApiCall.INFO);
                int numberOfPages = jsonObject.getInt(ApiCall.PAGES);
                getLastEntries(numberOfPages, url);
                Log.d(TAG, "networkInitialCall: getLastEntries " +numberOfPages + " / " + url);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, url));
    }

    private void getLastEntries(final int numberOfPages, String url) {
        appExecutors.networkIO().execute(() -> networkDataParsing.getVolleyResponce(response -> {
            //get a JSONArray from the last page and fetch the last entry object
            try {
                JSONArray jsonArray = response.getJSONArray(ApiCall.RESULTS_ARRAY);
                int lastArrayObjectId = jsonArray.length() - 1;
                JSONObject entryObjectJson = jsonArray.getJSONObject(lastArrayObjectId);
                Object lastEntryObject = parseJsonObject(entryObjectJson, url);
                //compare entry objects from DataBase and Network, make a DB sync if necessary
                if (lastEntryObject != null) {
                    compareLastEntries(lastEntryObject, url, numberOfPages);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, url + numberOfPages));
    }

    private void compareLastEntries(Object lastEntryObject, String url, int numberOfPages) {
        if (lastEntryObject.getClass() == Character.class) {
            lastNetworkCharacter = (Character) lastEntryObject;
            if (lastNetworkCharacter.equals(lastDbCharacter) && lastNetworkCharacter.getId() == characterEntriesDbCount) {
                characterTableIsUpToDate = true;
            } else {
                for (int x = 1; x < numberOfPages + 1; x++) {
                    updateDataBaseEntries(x, url);
                }
            }
        } else if (lastEntryObject.getClass() == Location.class) {
            lastNetworkLocation = (Location) lastEntryObject;
            if (lastNetworkLocation.equals(lastDbLocation) && lastNetworkLocation.getId() == locationEntriesDbCount) {
                locationTableIsUpToDate = true;
            } else {
                for (int x = 1; x < numberOfPages + 1; x++) {
                    updateDataBaseEntries(x, url);
                }
            }
        } else if (lastEntryObject.getClass() == Episode.class) {
            lastNetworkEpisode = (Episode) lastEntryObject;
            if (lastNetworkEpisode.equals(lastDbEpisode) && lastNetworkEpisode.getId() == episodeEntriesDbCount) {
                episodeTableIsUpToDate = true;
            } else {
                for (int x = 1; x < numberOfPages + 1; x++) {
                    updateDataBaseEntries(x, url);
                }
            }
        }
        if (characterTableIsUpToDate && locationTableIsUpToDate && episodeTableIsUpToDate) {
            dbIsUpToDate = true;
            networkDataParsing.cancelVolleyRequests();
        }
    }

    // loops through all pages and uses data to populate Database
    private void updateDataBaseEntries(final int pageNumber, String url) {
        appExecutors.networkIO().execute(() -> networkDataParsing.getVolleyResponce(response -> {
            try {
                JSONArray jsonArray = response.getJSONArray(ApiCall.RESULTS_ARRAY);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    Object newEntryObject = parseJsonObject(jsonObject, url);
                    if (newEntryObject != null) {
                        addEntryToDatabase(newEntryObject);
                    }
                }
                if (characterTableIsUpToDate && locationTableIsUpToDate && episodeTableIsUpToDate) {
                    dbIsUpToDate = true;
                    networkDataParsing.cancelVolleyRequests();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, url + pageNumber));
    }

    // add a new entry to the database
    private void addEntryToDatabase (Object newEntryObject) {
        if (newEntryObject.getClass() == Character.class) {
            //adds a new Character to the database
            Character newCharacter = (Character) newEntryObject;
            appExecutors.diskIO().execute(() -> rmDatabase.getCharacterDao().insertCharacter(newCharacter));
            if (newCharacter.equals(lastNetworkCharacter) && lastNetworkCharacter.getId() == characterEntriesDbCount) {
                characterTableIsUpToDate = true;
                lastDbCharacter = newCharacter;
            }
        } else if (newEntryObject.getClass() == Location.class) {
            //adds a new Location to the database
            Location newLocation = (Location) newEntryObject;
            appExecutors.diskIO().execute(() -> rmDatabase.getLocationDao().insertLocation(newLocation));
            if (newLocation.equals(lastNetworkLocation) && lastNetworkLocation.getId() == locationEntriesDbCount) {
                locationTableIsUpToDate = true;
                lastDbLocation = newLocation;
            }
        } else if (newEntryObject.getClass() == Episode.class) {
            //adds a new Episode to the database
            Episode newEpisode = (Episode) newEntryObject;
            appExecutors.diskIO().execute(() -> rmDatabase.getEpisodeDao().insertEpisode(newEpisode));
            if (newEpisode.equals(lastNetworkEpisode) && lastNetworkEpisode.getId() == episodeEntriesDbCount) {
                episodeTableIsUpToDate = true;
                lastDbEpisode = newEpisode;
            }
        }
    }

    private Object parseJsonObject (JSONObject entryObjectJson, String url) {
        switch (url) {
            case ApiCall.ApiCallCharacterKeys.BASE_URL_CHARACTER_PAGES:
                try {
                    int id = entryObjectJson.getInt(ApiCall.ApiCallCharacterKeys.CHARACTER_ID);
                    String name = entryObjectJson.getString(ApiCall.ApiCallCharacterKeys.CHARACTER_NAME);
                    String status = entryObjectJson.getString(ApiCall.ApiCallCharacterKeys.CHARACTER_STATUS);
                    String species = entryObjectJson.getString(ApiCall.ApiCallCharacterKeys.CHARACTER_SPECIES);
                    String type = entryObjectJson.getString(ApiCall.ApiCallCharacterKeys.CHARACTER_TYPE);
                    String gender = entryObjectJson.getString(ApiCall.ApiCallCharacterKeys.CHARACTER_GENDER);
                    JSONObject originLocation = entryObjectJson.getJSONObject(ApiCall.ApiCallCharacterKeys.CHARACTER_ORIGIN_LOCATION);
                    String originLocString = originLocation.getString(ApiCall.ApiCallCharacterKeys.CHARACTER_LOCATIONS_URL);
                    int originLocId = 0;
                    if (originLocString.contains(ApiCall.ApiCallCharacterKeys.CHARACTER_LOCATIONS_SUBSTRING)) {
                        int stringEnd = originLocString.lastIndexOf(ApiCall.ApiCallCharacterKeys.CHARACTER_LOCATIONS_SUBSTRING);
                        originLocId = Integer.parseInt(originLocString.substring(stringEnd + 9));
                    }
                    JSONObject lastKnownLoc = entryObjectJson.getJSONObject(ApiCall.ApiCallCharacterKeys.CHARACTER_LAST_LOCATION);
                    String lastKnownLocString = lastKnownLoc.getString(ApiCall.ApiCallCharacterKeys.CHARACTER_LOCATIONS_URL);
                    int lastKnownLocId = 0;
                    if (lastKnownLocString.contains(ApiCall.ApiCallCharacterKeys.CHARACTER_LOCATIONS_SUBSTRING)) {
                        int stringEnd = lastKnownLocString.lastIndexOf(ApiCall.ApiCallCharacterKeys.CHARACTER_LOCATIONS_SUBSTRING);
                        lastKnownLocId = Integer.parseInt(lastKnownLocString.substring(stringEnd + 9));
                    }
                    String imgUrl = entryObjectJson.getString(ApiCall.ApiCallCharacterKeys.CHARACTER_IMAGE_URL);
                    String episodeList = entryObjectJson.getString(ApiCall.ApiCallCharacterKeys.CHARACTER_EPISODE_LIST);
                    return new Character(id, name, status, species, type, gender, originLocId,
                            lastKnownLocId, imgUrl, episodeList);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
            case ApiCall.ApiCallLocationKeys.BASE_URL_LOCATION_PAGES:
                try {
                    int id = entryObjectJson.getInt(ApiCall.ApiCallLocationKeys.LOCATION_ID);
                    String name = entryObjectJson.getString(ApiCall.ApiCallLocationKeys.LOCATION_NAME);
                    String type = entryObjectJson.getString(ApiCall.ApiCallLocationKeys.LOCATION_TYPE);
                    String dimension = entryObjectJson.getString(ApiCall.ApiCallLocationKeys.LOCATION_DIMENSION);
                    String residents = entryObjectJson.getString(ApiCall.ApiCallLocationKeys.LOCATION_RESIDENTS);
                    return new Location(id, name, type, dimension, residents);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
            case ApiCall.ApiCallEpisodeKeys.BASE_URL_EPISODE_PAGES:
                try {
                    int id = entryObjectJson.getInt(ApiCall.ApiCallEpisodeKeys.EPISODE_ID);
                    String name = entryObjectJson.getString(ApiCall.ApiCallEpisodeKeys.EPISODE_NAME);
                    String airDate = entryObjectJson.getString(ApiCall.ApiCallEpisodeKeys.EPISODE_AIR_DATE);
                    String code = entryObjectJson.getString(ApiCall.ApiCallEpisodeKeys.EPISODE_CODE);
                    String characters = entryObjectJson.getString(ApiCall.ApiCallEpisodeKeys.EPISODE_CHARACTERS);
                    return new Episode(id, name, airDate, code, characters);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
            default: return  null;
        }
    }


    public void syncDatabase() {
        initialiseDataBase();
    }

    // calls the appropriate method based on search query and filter applied
    public LiveData<PagedList<Character>> getCharacterListFiltered(String query, int filter) {
        LiveData<PagedList<Character>> mCharacterList = new LiveData<PagedList<Character>>() {};
        if (query == null || query.equals("")) {
            switch (filter) {
                case 0:
                    mCharacterList = getAllCharacters();
                    break;
                case 101:
                    mCharacterList = getAllCharsNoDead();
                    break;
            }
        } else {
            switch (filter) {
                case 0:
                    mCharacterList = searchInCharacters(query);
                    break;
                case 101:
                    mCharacterList = searchInCharactersNoDead(query);
                    break;
            }
        }
        return mCharacterList;
    }

    //CHARACTERS
    //gets all characters
    private LiveData<PagedList<Character>> getAllCharacters() {
        return new LivePagedListBuilder<>(rmDatabase.getCharacterDao().showAllCharacters(), 50).setFetchExecutor(appExecutors.diskIO()).build();
    }

    //gets all characters, excludes Dead
    private LiveData<PagedList<Character>> getAllCharsNoDead() {
        return new LivePagedListBuilder<>(rmDatabase.getCharacterDao().showAllCharsNoDead(), 50).setFetchExecutor(appExecutors.diskIO()).build();
    }

    //performs search by name in database, shows all
    private LiveData<PagedList<Character>> searchInCharacters(String query) {
        return new LivePagedListBuilder<>(rmDatabase.getCharacterDao().searchInCharacterList("%" + query + "%"), 50).setFetchExecutor(appExecutors.diskIO()).build();
    }

    //performs search by name in database, excludes Dead
    private LiveData<PagedList<Character>> searchInCharactersNoDead(String query) {
        return new LivePagedListBuilder<>(rmDatabase.getCharacterDao().searchInCharacterListNoDead("%" + query + "%"), 50).setFetchExecutor(appExecutors.diskIO()).build();
    }

    //LOCATIONS
    //gets all locations
    private LiveData<PagedList<Location>> getAllLocations() {
        return new LivePagedListBuilder<>(rmDatabase.getLocationDao().showAllLocations(), 50).setFetchExecutor(appExecutors.diskIO()).build();
    }

}