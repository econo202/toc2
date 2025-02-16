/*
 * Copyright 2019 Michael Moessner
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

import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt


class ScenesFragment : Fragment() {

    private val viewModel by activityViewModels<ScenesViewModel> {
        ScenesViewModel.Factory(AppPreferences.readScenesDatabase(requireActivity()))
    }
    private val metronomeViewModel by activityViewModels<MetronomeViewModel> {
        val playerConnection = PlayerServiceConnection.getInstance(
                requireContext(),
                AppPreferences.readMetronomeBpm(requireActivity()),
                AppPreferences.readMetronomeNoteList(requireActivity())
        )
        MetronomeViewModel.Factory(playerConnection)
    }
    private var speedLimiter: SpeedLimiter? = null

    private var scenesRecyclerView: RecyclerView? = null
    private val scenesAdapter = ScenesAdapter().apply {
        onSceneClickedListener = ScenesAdapter.OnSceneClickedListener { stableId ->
            // this will lead to loading the clicked item
            viewModel.setActiveStableId(stableId)
        }
    }

    private var lastRemovedItemIndex = -1
    private var lastRemovedItem: Scene? = null

    private var noScenesMessage: TextView? = null

    private var playFab: FloatingActionButton? = null
    private var playFabStatus = PlayerStatus.Paused

    private val sceneArchiving = SceneArchiving(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.scenes, menu)
//        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {

        val loadDataItem = menu.findItem(R.id.action_load)
        loadDataItem?.isVisible = false

        val editItem = menu.findItem(R.id.action_edit)
        editItem?.isVisible = viewModel.activeStableId.value != Scene.NO_STABLE_ID
    }

    override fun onOptionsItemSelected(item : MenuItem) : Boolean {
        when (item.itemId) {
            R.id.action_archive -> {
                if (viewModel.scenes.value?.size ?: 0 == 0) {
                    Toast.makeText(requireContext(), R.string.database_empty, Toast.LENGTH_LONG).show()
                } else {
                    sceneArchiving.archiveScenes(viewModel.scenes.value)
                }
            }
            R.id.action_unarchive -> {
                sceneArchiving.unarchiveScenes()
            }
            R.id.action_clear_all -> {
                clearAllSavedItems()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        Log.v("Metronome", "ScenesFragment:onCreateView")
        val view = inflater.inflate(R.layout.fragment_scenes, container, false)

        noScenesMessage = view.findViewById(R.id.noScenesMessage)

        playFab = view.findViewById(R.id.play_fab)

        playFab?.setOnClickListener {
            if (metronomeViewModel.playerStatus.value == PlayerStatus.Playing)
                metronomeViewModel.pause()
            else
                metronomeViewModel.play()
        }

        scenesRecyclerView = view.findViewById(R.id.scenes)
        scenesRecyclerView?.setHasFixedSize(true)
        scenesRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        scenesRecyclerView?.adapter = scenesAdapter
//        setSelectionTracker()

        val simpleTouchHelper = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {

            val background = activity?.let { ContextCompat.getDrawable(it, R.drawable.scene_below_background) }
            val deleteIcon = activity?.let { ContextCompat.getDrawable(it, R.drawable.scene_delete) }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.absoluteAdapterPosition
                val toPos = target.absoluteAdapterPosition
                if(fromPos != toPos) {
                    viewModel.scenes.value?.move(fromPos, toPos)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Log.v("Metronome", "ScenesFragment:onSwiped " + viewHolder.getAdapterPosition())

                lastRemovedItemIndex = viewHolder.absoluteAdapterPosition
                lastRemovedItem = viewModel.scenes.value?.remove(lastRemovedItemIndex)

                (getView() as CoordinatorLayout?)?.let { coLayout ->
                    lastRemovedItem?.let { removedItem ->
                        Snackbar.make(coLayout, getString(R.string.scene_deleted), Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo) {
                                    if (lastRemovedItem != null) {
                                        viewModel.scenes.value?.add(lastRemovedItemIndex, removedItem)
                                        lastRemovedItem = null
                                        lastRemovedItemIndex = -1
                                    }
                                }.show()
                    }

                }
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {

                val itemView = viewHolder.itemView

                // not sure why, but this method get's called for viewholder that are already swiped away
                if (viewHolder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
                    // not interested in those
                    return
                }

                background?.setBounds(itemView.left, itemView.top, itemView.right, itemView.bottom)
                background?.draw(c)

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    deleteIcon?.alpha = min(255, (255 * 3 * dX.absoluteValue / itemView.width).toInt())
                    val iconHeight = (0.4f * (itemView.height - itemView.paddingTop - itemView.paddingBottom)).roundToInt()
                    val deleteIconLeft = itemView.right - iconHeight - itemView.paddingRight //itemView.right + iconHeight + itemView.paddingRight + dX.roundToInt()
                    deleteIcon?.setBounds(deleteIconLeft,
                            (itemView.top + itemView.bottom - iconHeight) / 2,
                            deleteIconLeft + iconHeight,
                            (itemView.top + itemView.bottom + iconHeight) / 2)
                    deleteIcon?.draw(c)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val touchHelper = ItemTouchHelper(simpleTouchHelper)
        touchHelper.attachToRecyclerView(scenesRecyclerView)

        metronomeViewModel.noteList.observe(viewLifecycleOwner) {noteList ->
            // all this complicated code just checks is the notelist of the active stable id
            // is equal to the note list in the metronome and if it is not equal, we make sure
            // that the active saved items are unselected
            var areNoteListsEqual = false
            viewModel.activeStableId.value?.let { stableId ->
                viewModel.scenes.value?.scenes?.firstOrNull { it.stableId == stableId }?.noteList?.let { activeNoteList ->
                    areNoteListsEqual = true
                    if (activeNoteList.size != noteList.size) {
                        areNoteListsEqual = false
                    } else {
                      noteList.zip(activeNoteList) {a, b ->
                          if (a.id != b.id || a.volume != b.volume)
                              areNoteListsEqual = false
                      }
                    }
                }
            }

//            Log.v("Metronome", "ScenesFragment.observeNoteList: areNoteListsEqual=$areNoteListsEqual")
            if (!areNoteListsEqual) {
                viewModel.setActiveStableId(Scene.NO_STABLE_ID)
            }
        }

        metronomeViewModel.bpm.observe(viewLifecycleOwner) { bpm ->
            // unselect active item if the bpm doesn't match the metronome bpm
            viewModel.activeStableId.value?.let { stableId ->
                viewModel.scenes.value?.scenes?.firstOrNull { it.stableId == stableId }?.bpm?.let { activeBpm ->
                    if (activeBpm != bpm) {
                        viewModel.setActiveStableId(Scene.NO_STABLE_ID)
                    }
                }
            }
        }

        metronomeViewModel.noteStartedEvent.observe(viewLifecycleOwner) { noteListItem ->
            metronomeViewModel.noteList.value?.let { noteList ->
                val index = noteList.indexOfFirst { it.uid == noteListItem.uid }
                scenesAdapter.animateNoteAndTickVisualizer(index, metronomeViewModel.bpm.value, scenesRecyclerView)
            }
        }

        viewModel.scenes.observe(viewLifecycleOwner) { database ->
//            Log.v("Metronome", "ScenesFragment: submitting new data base list to adapter: size: ${database.savedItems.size}")
            val databaseCopy = ArrayList<Scene>(database.scenes.size)
            database.scenes.forEach { databaseCopy.add(it.copy()) }
            scenesAdapter.submitList(databaseCopy)
            activity?.let{AppPreferences.writeScenesDatabase(viewModel.scenesAsString, it)}

            if(database.size == 0)
                noScenesMessage?.visibility = View.VISIBLE
            else
                noScenesMessage?.visibility = View.GONE
        }

        viewModel.activeStableId.observe(viewLifecycleOwner) { stableId ->
//            Log.v("Metronome", "ScenesFragment: observing stable id: $stableId")
            viewModel.scenes.value?.getScene(stableId)?.let { item ->
                if (item.noteList.size > 0)
                    metronomeViewModel.setNoteList(item.noteList)
                speedLimiter?.let {
                    it.checkSavedItemBpmAndAlert(item.bpm, requireContext())
                    metronomeViewModel.setBpm(it.limit(item.bpm))
                }
                metronomeViewModel.setNextNoteIndex(0)

                // we don't show this since it is rather obvious and it would also be shown when fragment is loaded
                //Toast.makeText(requireContext(), getString(R.string.loaded_message, item.title), Toast.LENGTH_SHORT).show()
                activity?.invalidateOptionsMenu()
            }

            scenesAdapter.setActiveStableId(stableId, scenesRecyclerView)
        }

//        if (metronomeViewModel.playerStatus.value == PlayerStatus.Playing)
//            playFab?.setImageResource(R.drawable.ic_play_to_pause)
//        else
//            playFab?.setImageResource(R.drawable.ic_pause_to_play)
        playFabStatus = metronomeViewModel.playerStatus.value ?: PlayerStatus.Paused

        metronomeViewModel.playerStatus.observe(viewLifecycleOwner) { playerStatus ->
//            Log.v("Metronome", "ScenesFragment: observing playerStatus: $playerStatus")
            if (playerStatus != playFabStatus) {
                if (playerStatus == PlayerStatus.Playing)
                    playFab?.setImageResource(R.drawable.ic_pause_to_play)
                else
                    playFab?.setImageResource(R.drawable.ic_play_to_pause)
                playFabStatus = playerStatus
                val drawable = playFab?.drawable as Animatable?
                drawable?.start()
            }
        }

        speedLimiter = SpeedLimiter(PreferenceManager.getDefaultSharedPreferences(requireContext()), viewLifecycleOwner)
        return view
    }

    override fun onResume() {
        super.onResume()
        if (metronomeViewModel.playerStatus.value == PlayerStatus.Playing)
            playFab?.setImageResource(R.drawable.ic_play_to_pause)
        else
            playFab?.setImageResource(R.drawable.ic_pause_to_play)

    }
    private fun clearAllSavedItems() {
        val builder = AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.clear_all_question)
            setNegativeButton(R.string.no) { dialog, _ -> dialog.dismiss() }
            setPositiveButton(R.string.yes) { _, _ ->
                viewModel.scenes.value?.clear()
                AppPreferences.writeScenesDatabase(viewModel.scenesAsString, requireActivity())
            }
        }
        builder.show()
    }

    fun getDatabaseString() : String? {
        return viewModel.scenes.value?.getScenesString()
    }

    fun loadDatabaseFromString(scenesString: String, task: SceneDatabase.InsertMode, filename: String?) {
        activity?.let { act ->
            when (viewModel.scenes.value?.loadSceneFromString(scenesString, task)) {
                SceneDatabase.FileCheck.Empty ->
                    Toast.makeText(act, act.getString(R.string.file_empty, filename), Toast.LENGTH_LONG).show()
                SceneDatabase.FileCheck.Invalid ->
                    Toast.makeText(act, act.getString(R.string.file_invalid, filename), Toast.LENGTH_LONG).show()
                SceneDatabase.FileCheck.Ok ->
                    AppPreferences.writeScenesDatabase(viewModel.scenesAsString, act)
            }
        }
    }
}
