package com.provideoplayer.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.provideoplayer.PlayerActivity
import com.provideoplayer.R
import com.provideoplayer.adapter.FolderAdapter
import com.provideoplayer.adapter.VideoAdapter
import com.provideoplayer.databinding.FragmentBrowseBinding
import com.provideoplayer.model.FolderItem
import com.provideoplayer.model.VideoItem
import com.provideoplayer.utils.VideoScanner
import kotlinx.coroutines.launch

class BrowseFragment : Fragment() {
    
    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter
    
    private var allVideos: List<VideoItem> = emptyList()
    private var allAudioFiles: List<VideoItem> = emptyList()
    private var allFolders: List<FolderItem> = emptyList()
    
    private var browseFilter = 1  // 1=Videos, 2=Audio
    private var isShowingFolders = true
    private var currentFolderId: Long? = null
    private var currentFolderPath: String? = null
    private var currentFolderName: String? = null  // Store folder name for filter switch
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        setupFilterButtons()
        loadData()
    }
    
    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter(
            onVideoClick = { video, position ->
                openPlayer(video, position)
            },
            onVideoLongClick = { video ->
                showMediaInfo(video)
                true
            }
        )
        
        folderAdapter = FolderAdapter { folder ->
            openFolder(folder)
        }
        
        binding.recyclerView.apply {
            setHasFixedSize(true)
            // Set item animator to null for smoother transitions
            itemAnimator = null
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(
            R.color.purple_500,
            R.color.purple_700,
            R.color.teal_200
        )
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }
    }
    
    private fun setupFilterButtons() {
        updateFilterButtonStyles()
        
        binding.btnFilterVideo.setOnClickListener {
            if (browseFilter == 1) return@setOnClickListener  // Already on video filter
            
            val previousFilter = browseFilter
            browseFilter = 1
            updateFilterButtonStyles()
            
            // If we're inside a folder, stay inside but switch content type
            if (!isShowingFolders && currentFolderPath != null) {
                switchFilterInFolder()
            } else {
                // At folder level, just refresh folder list
                showBrowseMedia()
            }
        }
        
        binding.btnFilterAudio.setOnClickListener {
            if (browseFilter == 2) return@setOnClickListener  // Already on audio filter
            
            val previousFilter = browseFilter
            browseFilter = 2
            updateFilterButtonStyles()
            
            // If we're inside a folder, stay inside but switch content type
            if (!isShowingFolders && currentFolderPath != null) {
                switchFilterInFolder()
            } else {
                // At folder level, just refresh folder list
                showBrowseMedia()
            }
        }
    }
    
    /**
     * Switch filter while staying inside the current folder
     */
    private fun switchFilterInFolder() {
        // Clear RecyclerView pool to prevent thumbnail glitches
        binding.recyclerView.recycledViewPool.clear()
        
        if (browseFilter == 2) {
            // Switching to audio filter
            showAudioInFolder(currentFolderPath!!)
        } else {
            // Switching to video filter - need to find matching folder
            if (currentFolderId != null) {
                showVideosInFolder(currentFolderId!!)
            } else {
                // Try to find folder by path
                val matchingFolder = allFolders.find { it.path == currentFolderPath }
                if (matchingFolder != null) {
                    currentFolderId = matchingFolder.id
                    showVideosInFolder(matchingFolder.id)
                } else {
                    // Fallback to folder list
                    currentFolderPath = null
                    currentFolderName = null
                    (activity as? VideosFragment.TabHost)?.setBackEnabled(false)
                    (activity as? VideosFragment.TabHost)?.updateTitle("Browse")
                    showBrowseMedia()
                }
            }
        }
    }
    
    private fun updateFilterButtonStyles() {
        val context = requireContext()
        
        binding.btnFilterVideo.strokeWidth = if (browseFilter == 1) 0 else 2
        binding.btnFilterAudio.strokeWidth = if (browseFilter == 2) 0 else 2
        
        if (browseFilter == 1) {
            binding.btnFilterVideo.setBackgroundColor(context.getColor(R.color.purple_500))
            binding.btnFilterVideo.setTextColor(context.getColor(R.color.white))
            binding.btnFilterVideo.iconTint = android.content.res.ColorStateList.valueOf(context.getColor(R.color.white))
        } else {
            binding.btnFilterVideo.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnFilterVideo.setTextColor(context.getColor(R.color.purple_500))
            binding.btnFilterVideo.iconTint = android.content.res.ColorStateList.valueOf(context.getColor(R.color.purple_500))
        }
        
        if (browseFilter == 2) {
            binding.btnFilterAudio.setBackgroundColor(context.getColor(R.color.purple_500))
            binding.btnFilterAudio.setTextColor(context.getColor(R.color.white))
            binding.btnFilterAudio.iconTint = android.content.res.ColorStateList.valueOf(context.getColor(R.color.white))
        } else {
            binding.btnFilterAudio.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnFilterAudio.setTextColor(context.getColor(R.color.purple_500))
            binding.btnFilterAudio.iconTint = android.content.res.ColorStateList.valueOf(context.getColor(R.color.purple_500))
        }
    }
    
    private fun loadData() {
        if (!isAdded) return
        
        binding.progressBar.visibility = View.VISIBLE
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allVideos = VideoScanner.getAllVideos(requireContext())
                allAudioFiles = VideoScanner.getAllAudio(requireContext())
                allFolders = VideoScanner.getAllFolders(requireContext())
                
                binding.swipeRefresh.isRefreshing = false
                binding.progressBar.visibility = View.GONE
                
                showBrowseMedia()
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyText.text = "Error loading data"
            }
        }
    }
    
    private fun showBrowseMedia() {
        isShowingFolders = true
        currentFolderId = null
        currentFolderPath = null
        currentFolderName = null
        
        // Clear RecyclerView pool to prevent rendering issues
        binding.recyclerView.recycledViewPool.clear()
        binding.recyclerView.adapter = folderAdapter
        applyFolderLayoutPreference()
        
        val filteredFolders = when (browseFilter) {
            1 -> {
                // Video filter
                allFolders.mapNotNull { folder ->
                    val videoCount = allVideos.count { video ->
                        video.folderId == folder.id &&
                        !video.mimeType.startsWith("audio") &&
                        !video.path.endsWith(".mp3", true) &&
                        !video.path.endsWith(".m4a", true) &&
                        !video.path.endsWith(".aac", true) &&
                        !video.path.endsWith(".wav", true) &&
                        !video.path.endsWith(".flac", true)
                    }
                    if (videoCount > 0) folder.copy(videoCount = videoCount) else null
                }
            }
            2 -> {
                // Audio filter
                val audioFolderMap = mutableMapOf<String, Int>()
                allAudioFiles.forEach { audio ->
                    val folderPath = audio.path.substringBeforeLast("/")
                    audioFolderMap[folderPath] = (audioFolderMap[folderPath] ?: 0) + 1
                }
                
                audioFolderMap.map { (path, count) ->
                    val folderName = path.substringAfterLast("/")
                    FolderItem(
                        id = path.hashCode().toLong(),
                        name = if (folderName.isNotEmpty()) folderName else "Audio",
                        path = path,
                        videoCount = count
                    )
                }
            }
            else -> allFolders
        }
        
        if (filteredFolders.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyText.text = when (browseFilter) {
                1 -> "No video folders found"
                2 -> "No audio folders found"
                else -> "No media folders found"
            }
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            folderAdapter.mediaType = browseFilter
            folderAdapter.submitList(filteredFolders.sortedByDescending { it.videoCount })
        }
    }
    
    private fun openFolder(folder: FolderItem) {
        currentFolderId = folder.id
        currentFolderPath = folder.path
        currentFolderName = folder.name
        
        (activity as? VideosFragment.TabHost)?.setBackEnabled(true)
        (activity as? VideosFragment.TabHost)?.updateTitle(folder.name)
        
        // Clear RecyclerView pool before switching adapters
        binding.recyclerView.recycledViewPool.clear()
        
        if (browseFilter == 2) {
            showAudioInFolder(folder.path)
        } else {
            showVideosInFolder(folder.id)
        }
    }
    
    private fun showVideosInFolder(folderId: Long) {
        isShowingFolders = false
        
        // Clear pool and reset adapter for clean transition
        binding.recyclerView.recycledViewPool.clear()
        binding.recyclerView.adapter = null
        binding.recyclerView.adapter = videoAdapter
        applyLayoutPreference()
        
        var folderVideos = allVideos.filter { it.folderId == folderId }
        
        if (browseFilter > 0) {
            folderVideos = folderVideos.filter { video ->
                val isAudio = video.mimeType.startsWith("audio") ||
                             video.path.endsWith(".mp3", true) ||
                             video.path.endsWith(".m4a", true) ||
                             video.path.endsWith(".aac", true) ||
                             video.path.endsWith(".wav", true) ||
                             video.path.endsWith(".flac", true)
                             
                when (browseFilter) {
                    1 -> !isAudio
                    2 -> isAudio
                    else -> true
                }
            }
        }
        
        videoAdapter.submitList(folderVideos)
        
        if (folderVideos.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyText.text = "No videos in this folder"
        } else {
            binding.emptyView.visibility = View.GONE
        }
    }
    
    private fun showAudioInFolder(folderPath: String) {
        isShowingFolders = false
        
        // Clear pool and reset adapter for clean transition
        binding.recyclerView.recycledViewPool.clear()
        binding.recyclerView.adapter = null
        binding.recyclerView.adapter = videoAdapter
        applyLayoutPreference()
        
        val folderAudio = allAudioFiles.filter { 
            it.path.substringBeforeLast("/") == folderPath 
        }
        
        videoAdapter.submitList(folderAudio)
        
        if (folderAudio.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyText.text = "No audio files in this folder"
        } else {
            binding.emptyView.visibility = View.GONE
        }
    }
    
    private fun applyLayoutPreference() {
        val prefs = requireContext().getSharedPreferences("pro_video_player_prefs", Context.MODE_PRIVATE)
        val isGrid = prefs.getBoolean("is_grid_view", true)
        
        binding.recyclerView.layoutManager = if (isGrid) {
            GridLayoutManager(requireContext(), 2)
        } else {
            LinearLayoutManager(requireContext())
        }
        videoAdapter.isListView = !isGrid
    }
    
    private fun applyFolderLayoutPreference() {
        val prefs = requireContext().getSharedPreferences("pro_video_player_prefs", Context.MODE_PRIVATE)
        val isGrid = prefs.getBoolean("is_grid_view", true)
        
        binding.recyclerView.layoutManager = if (isGrid) {
            GridLayoutManager(requireContext(), 2)
        } else {
            LinearLayoutManager(requireContext())
        }
    }
    
    private fun openPlayer(video: VideoItem, position: Int) {
        val context = requireContext()
        val isAudio = video.mimeType.startsWith("audio") ||
                     video.path.endsWith(".mp3", true) ||
                     video.path.endsWith(".m4a", true) ||
                     video.path.endsWith(".flac", true) ||
                     video.path.endsWith(".wav", true) ||
                     video.path.endsWith(".aac", true)
        
        if (isAudio) {
            saveAudioToHistory(video.uri.toString())
        } else {
            saveVideoToHistory(video.uri.toString(), video.title)
        }
        
        val playlist = videoAdapter.currentList.toList()
        val videoIndex = playlist.indexOfFirst { it.id == video.id }.takeIf { it >= 0 } ?: position
        
        val intent = Intent(context, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PlayerActivity.EXTRA_VIDEO_URI, video.uri.toString())
            putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.title)
            putExtra(PlayerActivity.EXTRA_VIDEO_POSITION, videoIndex)
            putStringArrayListExtra(
                PlayerActivity.EXTRA_PLAYLIST,
                ArrayList(playlist.map { it.uri.toString() })
            )
            putStringArrayListExtra(
                PlayerActivity.EXTRA_PLAYLIST_TITLES,
                ArrayList(playlist.map { it.title })
            )
        }
        startActivity(intent)
    }
    
    private fun saveVideoToHistory(uri: String, title: String) {
        val prefs = requireContext().getSharedPreferences("pro_video_player_prefs", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("video_history", "[]") ?: "[]"
        
        val historyArray = try {
            org.json.JSONArray(historyJson)
        } catch (e: Exception) {
            org.json.JSONArray()
        }
        
        val newArray = org.json.JSONArray()
        for (i in 0 until historyArray.length()) {
            val existingUri = historyArray.getString(i)
            if (existingUri != uri) {
                newArray.put(existingUri)
            }
        }
        newArray.put(uri)
        
        val finalArray = org.json.JSONArray()
        val startIndex = if (newArray.length() > 20) newArray.length() - 20 else 0
        for (i in startIndex until newArray.length()) {
            finalArray.put(newArray.getString(i))
        }
        
        prefs.edit()
            .putString("video_history", finalArray.toString())
            .putString("last_video_uri", uri)
            .putString("last_video_title", title)
            .apply()
    }
    
    private fun saveAudioToHistory(uri: String) {
        val prefs = requireContext().getSharedPreferences("pro_video_player_prefs", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("audio_history", "[]") ?: "[]"
        
        val historyArray = try {
            org.json.JSONArray(historyJson)
        } catch (e: Exception) {
            org.json.JSONArray()
        }
        
        val newArray = org.json.JSONArray()
        for (i in 0 until historyArray.length()) {
            val existingUri = historyArray.getString(i)
            if (existingUri != uri) {
                newArray.put(existingUri)
            }
        }
        newArray.put(uri)
        
        val finalArray = org.json.JSONArray()
        val startIndex = if (newArray.length() > 50) newArray.length() - 50 else 0
        for (i in startIndex until newArray.length()) {
            finalArray.put(newArray.getString(i))
        }
        
        prefs.edit()
            .putString("audio_history", finalArray.toString())
            .apply()
    }
    
    private fun showMediaInfo(video: VideoItem) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(video.title)
            .setMessage("""
                📁 Folder: ${video.folderName}
                ⏱️ Duration: ${video.getFormattedDuration()}
                📊 Size: ${video.getFormattedSize()}
                📂 Path: ${video.path}
            """.trimIndent())
            .setPositiveButton("Play") { _, _ ->
                openPlayer(video, 0)
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    fun onBackPressed(): Boolean {
        return if (!isShowingFolders && (currentFolderId != null || currentFolderPath != null)) {
            currentFolderId = null
            currentFolderPath = null
            currentFolderName = null
            (activity as? VideosFragment.TabHost)?.setBackEnabled(false)
            (activity as? VideosFragment.TabHost)?.updateTitle("Browse")
            showBrowseMedia()
            true
        } else {
            false
        }
    }
    
    fun refreshData() {
        if (isAdded && _binding != null) {
            // Clear RecyclerView pool to prevent thumbnail glitches
            binding.recyclerView.recycledViewPool.clear()
            
            // Apply layout based on current view
            if (isShowingFolders) {
                applyFolderLayoutPreference()
                // Reset adapter for clean refresh
                binding.recyclerView.adapter = null
                binding.recyclerView.adapter = folderAdapter
                val currentList = folderAdapter.currentList.toList()
                folderAdapter.submitList(null)
                folderAdapter.submitList(currentList)
            } else {
                applyLayoutPreference()
                // Reset adapter for clean refresh
                binding.recyclerView.adapter = null
                binding.recyclerView.adapter = videoAdapter
                val currentList = videoAdapter.currentList.toList()
                videoAdapter.submitList(null)
                videoAdapter.submitList(currentList)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
