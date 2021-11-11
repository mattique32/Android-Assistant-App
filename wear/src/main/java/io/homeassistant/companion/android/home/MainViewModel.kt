package io.homeassistant.companion.android.home

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.data.SimplifiedEntity
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private lateinit var homePresenter: HomePresenter

    // TODO: This is bad, do this instead: https://stackoverflow.com/questions/46283981/android-viewmodel-additional-arguments
    fun init(homePresenter: HomePresenter) {
        this.homePresenter = homePresenter
        loadEntities()
    }

    var entities = mutableStateListOf<Entity<*>>()
        private set
    var favoriteEntityIds = mutableStateListOf<String>()
        private set
    var shortcutEntities = mutableStateListOf<SimplifiedEntity>()
        private set

    fun loadEntities() {
        viewModelScope.launch {
            favoriteEntityIds.addAll(homePresenter.getWearHomeFavorites())
            shortcutEntities.addAll(homePresenter.getTileShortcuts())
            entities.addAll(homePresenter.getEntities())
        }
    }

    fun toggleEntity(entityId: String) {
        viewModelScope.launch {
            homePresenter.onEntityClicked(entityId)
            val updatedEntities = homePresenter.getEntities()
            // This should be better....
            for (i in updatedEntities.indices) {
                entities[i] = updatedEntities[i]
            }
        }
    }

    fun addFavorite(entityId: String) {

        viewModelScope.launch {
            favoriteEntityIds.add(entityId)
            homePresenter.setWearHomeFavorites(favoriteEntityIds)
        }
    }

    fun removeFavorite(entity: String) {

        viewModelScope.launch {
            favoriteEntityIds.remove(entity)
            homePresenter.setWearHomeFavorites(favoriteEntityIds)
        }
    }

    fun clearFavorites() {
        viewModelScope.launch {
            favoriteEntityIds.clear()
            homePresenter.setWearHomeFavorites(favoriteEntityIds)
        }
    }

    fun setTileShortcut(index: Int, entity: SimplifiedEntity) {
        viewModelScope.launch {
            if (index < shortcutEntities.size) {
                shortcutEntities[index] = entity
            } else {
                shortcutEntities.add(entity)
            }
            homePresenter.setTileShortcuts(shortcutEntities)
        }
    }

    fun clearTileShortcut(index: Int) {
        viewModelScope.launch {
            if (index < shortcutEntities.size) {
                shortcutEntities.removeAt(index)
                homePresenter.setTileShortcuts(shortcutEntities)
            }
        }
    }

    fun logout() {
        homePresenter.onLogoutClicked()
    }
}
