package com.snatik.storage.app

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import com.snatik.storage.Storage

import java.io.File

/**
 * Created by sromku on June, 2017.
 */
class FilesAdapter(context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mFiles: List<File>? = null
    private var mListener: OnFileItemListener? = null
    private val mStorage: Storage = Storage(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.file_line_view, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val file = mFiles!![position]
        val fileViewHolder = holder as FileViewHolder
        fileViewHolder.itemView.setOnClickListener { mListener!!.onClick(file) }
        fileViewHolder.itemView.setOnLongClickListener {
            mListener!!.onLongClick(file)
            true
        }
        fileViewHolder.mName.text = file.name
        fileViewHolder.mIcon.setImageResource(if (file.isDirectory)
            R.drawable.ic_folder_primary_24dp
        else
            R.drawable
                    .ic_file_primary_24dp)
        if (file.isDirectory) {
            fileViewHolder.mSize.visibility = View.GONE
        } else {
            fileViewHolder.mSize.visibility = View.VISIBLE
            fileViewHolder.mSize.text = mStorage.getReadableSize(file)
        }

    }

    override fun getItemCount(): Int {
        return if (mFiles != null) mFiles!!.size else 0
    }

    fun setFiles(files: List<File>) {
        mFiles = files
    }

    fun setListener(listener: OnFileItemListener) {
        mListener = listener
    }

    internal class FileViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        var mName: TextView = v.findViewById<View>(R.id.name) as TextView
        var mSize: TextView = v.findViewById<View>(R.id.size) as TextView
        var mIcon: ImageView = v.findViewById<View>(R.id.icon) as ImageView
    }

    interface OnFileItemListener {
        fun onClick(file: File)

        fun onLongClick(file: File)
    }
}
