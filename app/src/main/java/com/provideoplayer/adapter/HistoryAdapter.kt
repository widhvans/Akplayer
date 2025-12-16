package com.provideoplayer.adapter

import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.provideoplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryAdapter(
    private val historyItems: List<Pair<String, String>>,  // (title, uri)
    private val onItemClick: (String, String) -> Unit  // (title, uri)
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        val videoTitle: TextView = view.findViewById(R.id.videoTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (title, uriString) = historyItems[position]
        holder.videoTitle.text = title
        
        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick(title, uriString)
        }
        
        // Load thumbnail
        loadThumbnail(holder, uriString)
    }

    override fun getItemCount() = historyItems.size
    
    private fun loadThumbnail(holder: ViewHolder, uriString: String) {
        val context = holder.itemView.context
        
        // Default placeholder
        holder.thumbnail.setImageResource(R.drawable.ic_video_placeholder)
        
        // Skip network streams - no thumbnail
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            holder.thumbnail.setImageResource(R.drawable.ic_video_placeholder)
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uri = Uri.parse(uriString)
                var bitmap: Bitmap? = null
                
                // Try to get video ID from content URI
                if (uri.scheme == "content") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bitmap = context.contentResolver.loadThumbnail(
                            uri,
                            Size(160, 90),
                            null
                        )
                    } else {
                        // Get video ID from URI for older API
                        val videoId = ContentUris.parseId(uri)
                        @Suppress("DEPRECATION")
                        bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver,
                            videoId,
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                        )
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        holder.thumbnail.setImageBitmap(bitmap)
                    } else {
                        holder.thumbnail.setImageResource(R.drawable.ic_video_placeholder)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    holder.thumbnail.setImageResource(R.drawable.ic_video_placeholder)
                }
            }
        }
    }
}
