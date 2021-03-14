/*
 * Copyright 2020 Michael Moessner
 *
 * This file is part of Metronome.
 *
 * Metronome is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Metronome is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Metronome.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.moekadu.metronome

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ScenesViewModel(initialDatabaseString: String) : ViewModel() {

    private val sceneDatabase = SceneDatabase()
    private val _scenes = MutableLiveData(sceneDatabase)
    val scenes: LiveData<SceneDatabase> get() = _scenes
    val scenesAsString: String get() = scenes.value?.getScenesString() ?: ""

    private val _activeStableId = MutableLiveData(Scene.NO_STABLE_ID)
    val activeStableId: LiveData<Long> get() = _activeStableId

    private val _editingStableId = MutableLiveData(Scene.NO_STABLE_ID)
    val editingStableId: LiveData<Long> get() = _editingStableId

    init  {
        sceneDatabase.databaseChangedListener = SceneDatabase.DatabaseChangedListener {
//            Log.v("Metronome", "ScenesViewModel.init: database changed")
            _scenes.value = it
        }
        sceneDatabase.loadSceneFromString(initialDatabaseString, SceneDatabase.InsertMode.Replace)
    }

    fun setActiveStableId(stableId: Long) {
//        Log.v("Metronome", "ScenesViewModel.setStableActiveId: $stableId")
        _activeStableId.value = stableId
    }

    fun setEditingStableId(stableId: Long) {
        _editingStableId.value = stableId
    }

    class Factory(private val initialDatabaseString: String) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            Log.v("Metronome", "ScenesViewModel.factory.create")
            return ScenesViewModel(initialDatabaseString) as T
        }
    }
}