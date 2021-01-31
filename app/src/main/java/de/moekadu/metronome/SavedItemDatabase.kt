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
import java.util.*
import kotlin.math.min

data class SavedItem(var title: String = "", var date: String = "",
                     var time: String = "", var bpm: Float = 0f, var noteList: String = "")

class SavedItemDatabase {
    private val _savedItems = mutableListOf<SavedItem>()
    val savedItems: List<SavedItem> get() = _savedItems
    val size get() = _savedItems.size

    fun interface DatabaseChangedListener {
        fun onChanged(savedItemDatabase: SavedItemDatabase)
    }
    var databaseChangedListener: DatabaseChangedListener? = null

    fun remove(position: Int) : SavedItem {
        if (BuildConfig.DEBUG && position >= _savedItems.size)
            throw RuntimeException("Invalid position")

        val item = _savedItems.removeAt(position)
        databaseChangedListener?.onChanged(this)
        return item
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex >= _savedItems.size)
            return
        val item = _savedItems.removeAt(fromIndex)
        val toIndexCorrected = min(toIndex, _savedItems.size)
        _savedItems.add(toIndexCorrected, item)
        databaseChangedListener?.onChanged(this)
    }

    fun add(item: SavedItem) {
        Log.v("Metronome", "SavedItemDatabase.add: Adding: ${item.title}, ${item.noteList}")
        _savedItems.add(item)
        databaseChangedListener?.onChanged(this)
    }

    fun add(position: Int, item: SavedItem) {
        val positionCorrected = min(position, _savedItems.size)
        _savedItems.add(positionCorrected, item)
        databaseChangedListener?.onChanged(this)
    }

    fun getSaveDataString() : String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(String.format(Locale.ENGLISH, "%50s", BuildConfig.VERSION_NAME))
        for (si in savedItems) {
            stringBuilder.append(String.format(Locale.ENGLISH, "%200s%10s%5s%12.5f%sEND",
                    si.title, si.date, si.time, si.bpm, si.noteList))
        }
        return stringBuilder.toString()
    }

    fun loadDataFromString(dataString: String, mode: Int = REPLACE): Int {
        val newSavedItems = mutableListOf<SavedItem>()
        if (mode != REPLACE)
            newSavedItems.addAll(savedItems)

        // Log.v("Metronome", "SavedItemFragment:loadData: " + dataString);
        if(dataString == "")
            return FILE_EMPTY
        else if(dataString.length < 50)
            return FILE_INVALID

//        val version = dataString.substring(0, 50).trim()
        var pos = 50
        var numItemsRead = 0
        while(pos < dataString.length)
        {
            val si = SavedItem()

            if(pos + 200 >= dataString.length)
                return FILE_INVALID
            si.title = dataString.substring(pos, pos + 200).trim()
            pos += 200
            if(pos + 10 >= dataString.length)
                return FILE_INVALID
            si.date = dataString.substring(pos, pos + 10)
            pos += 10
            if(pos + 5 >= dataString.length)
                return FILE_INVALID
            si.time = dataString.substring(pos, pos + 5)
            pos += 5
            if(pos + 6 >= dataString.length)
                return FILE_INVALID
            try {
                si.bpm = (dataString.substring(pos, pos + 12).trim()).toFloat()
            }
            catch (e: NumberFormatException) {
                return FILE_INVALID
            }
            pos += 12
            val noteListEnd = dataString.indexOf("END", pos)
            if(noteListEnd == -1)
                return FILE_INVALID
            si.noteList = dataString.substring(pos, noteListEnd)
            if (NoteList.checkString(si.noteList) == NoteList.STRING_INVALID)
                return FILE_INVALID
            pos = noteListEnd + 3

            if (mode == PREPEND)
                newSavedItems.add(numItemsRead, si)
            else
                newSavedItems.add(si)
            ++numItemsRead
        }

        _savedItems.clear()
        _savedItems.addAll(newSavedItems)
        databaseChangedListener?.onChanged(this)
        return FILE_OK
    }

    fun clear() {
        _savedItems.clear()
        databaseChangedListener?.onChanged(this)
    }

    companion object {
        const val REPLACE = 0
        const val PREPEND = 1
        const val APPEND = 2
        const val FILE_OK = 0
        const val FILE_EMPTY = 1
        const val FILE_INVALID = 2
    }
}