package com.snatik.storage.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.snatik.storage.EncryptConfiguration
import com.snatik.storage.Storage
import com.snatik.storage.app.Helper.fileExt
import com.snatik.storage.app.dialogs.*
import com.snatik.storage.helpers.OrderType
import com.snatik.storage.helpers.SizeUnit
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity(), FilesAdapter.OnFileItemListener, AddItemsDialog.DialogListener, UpdateItemDialog.DialogListener, NewFolderDialog.DialogListener, NewTextFileDialog.DialogListener, ConfirmDeleteDialog.ConfirmListener, RenameDialog.DialogListener {
    private var mRecyclerView: RecyclerView? = null
    private var mFilesAdapter: FilesAdapter? = null
    private var mStorage: Storage? = null
    private var mPathView: TextView? = null
    private var mMovingText: TextView? = null
    private var mCopy: Boolean = false
    private var mMovingLayout: View? = null
    private var mTreeSteps = 0
    private var mMovingPath: String? = null
    private var mInternal = false

    private val currentPath: String
        get() = mPathView!!.text.toString()

    private val previousPath: String
        get() {
            val path = currentPath
            val lastIndexOf = path.lastIndexOf(File.separator)
            if (lastIndexOf < 0) {
                Helper.showSnackbar("Can't go anymore", mRecyclerView!!)
                return currentPath
            }
            return path.substring(0, lastIndexOf)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mStorage = Storage(applicationContext)

        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        mRecyclerView = findViewById(R.id.recycler)
        mPathView = findViewById(R.id.path)
        mMovingLayout = findViewById(R.id.moving_layout)
        mMovingText = mMovingLayout!!.findViewById(R.id.moving_file_name)

        mMovingLayout!!.findViewById<View>(R.id.accept_move).setOnClickListener {
            mMovingLayout!!.visibility = View.GONE
            if (mMovingPath != null) {

                if (!mCopy) {
                    val toPath = currentPath + File.separator + mStorage!!.getFile(mMovingPath!!).name
                    if (mMovingPath != toPath) {
                        mStorage!!.move(mMovingPath!!, toPath)
                        Helper.showSnackbar("Moved", mRecyclerView!!)
                        showFiles(currentPath)
                    } else {
                        Helper.showSnackbar("The file is already here", mRecyclerView!!)
                    }
                } else {
                    val toPath = currentPath + File.separator + "copy " + mStorage!!.getFile(mMovingPath!!)
                            .name
                    mStorage!!.copy(mMovingPath!!, toPath)
                    Helper.showSnackbar("Copied", mRecyclerView!!)
                    showFiles(currentPath)
                }
                mMovingPath = null
            }
        }

        mMovingLayout!!.findViewById<View>(R.id.decline_move).setOnClickListener {
            mMovingLayout!!.visibility = View.GONE
            mMovingPath = null
        }

        val layoutManager = GridLayoutManager(this, 3)
        mRecyclerView!!.layoutManager = layoutManager
        mFilesAdapter = FilesAdapter(applicationContext)
        mFilesAdapter!!.setListener(this)
        mRecyclerView!!.adapter = mFilesAdapter

        findViewById<View>(R.id.fab).setOnClickListener { AddItemsDialog.newInstance().show(supportFragmentManager, "add_items") }

        mPathView!!.setOnClickListener { showPathMenu() }

        // load files
        showFiles(mStorage!!.externalStorageDirectory)

        checkPermission()
    }

    private fun showPathMenu() {
        val popUpMenu = PopupMenu(this, mPathView)
        val inflater = popUpMenu.menuInflater
        inflater.inflate(R.menu.path_menu, popUpMenu.menu)

        popUpMenu.menu.findItem(R.id.go_internal).isVisible = !mInternal
        popUpMenu.menu.findItem(R.id.go_external).isVisible = mInternal

        popUpMenu.show()

        popUpMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.go_up -> {
                    val previousPath = previousPath
                    mTreeSteps = 0
                    showFiles(previousPath)
                }
                R.id.go_internal -> {
                    showFiles(mStorage!!.internalFilesDirectory)
                    mInternal = true
                }
                R.id.go_external -> {
                    showFiles(mStorage!!.externalStorageDirectory)
                    mInternal = false
                }
            }
            true
        }
    }

    private fun showFiles(path: String) {
        mPathView!!.text = path
        val files = mStorage!!.getFiles(path)
        if (files != null) {
            Collections.sort(files, OrderType.NAME.comparator)
        }
        mFilesAdapter!!.setFiles(files!!)
        mFilesAdapter!!.notifyDataSetChanged()
    }


    override fun onClick(file: File) {
        if (file.isDirectory) {
            mTreeSteps++
            val path = file.absolutePath
            showFiles(path)
        } else {

            try {
                val intent = Intent(Intent.ACTION_VIEW)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt(file.absolutePath))
                val apkURI = FileProvider.getUriForFile(
                        this,
                        applicationContext
                                .packageName + ".provider", file)
                intent.setDataAndType(apkURI, mimeType)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                if (mStorage!!.getSize(file, SizeUnit.KB) > 500) {
                    Helper.showSnackbar("The file is too big for preview", mRecyclerView!!)
                    return
                }
                val intent = Intent(this, ViewTextActivity::class.java)
                intent.putExtra(ViewTextActivity.EXTRA_FILE_NAME, file.name)
                intent.putExtra(ViewTextActivity.EXTRA_FILE_PATH, file.absolutePath)
                startActivity(intent)
            }

        }
    }

    override fun onLongClick(file: File) {
        UpdateItemDialog.newInstance(file.absolutePath).show(supportFragmentManager, "update_item")
    }

    override fun onBackPressed() {
        if (mTreeSteps > 0) {
            val path = previousPath
            mTreeSteps--
            showFiles(path)
            return
        }
        super.onBackPressed()
    }

    override fun onOptionClick(which: Int, path: String?) {
        when (which) {
            R.id.new_file -> NewTextFileDialog.newInstance().show(supportFragmentManager, "new_file_dialog")
            R.id.new_folder -> NewFolderDialog.newInstance().show(supportFragmentManager, "new_folder_dialog")
            R.id.delete -> ConfirmDeleteDialog.newInstance(path!!).show(supportFragmentManager, "confirm_delete")
            R.id.rename -> RenameDialog.newInstance(path!!).show(supportFragmentManager, "rename")
            R.id.move -> {
                mMovingText!!.text = getString(R.string.moving_file, mStorage!!.getFile(path!!).name)
                mMovingPath = path
                mCopy = false
                mMovingLayout!!.visibility = View.VISIBLE
            }
            R.id.copy -> {
                mMovingText!!.text = getString(R.string.copy_file, mStorage!!.getFile(path!!).name)
                mMovingPath = path
                mCopy = true
                mMovingLayout!!.visibility = View.VISIBLE
            }
        }
    }

    override fun onNewFolder(name: String) {
        val currentPath = currentPath
        val folderPath = currentPath + File.separator + name
        val created = mStorage!!.createDirectory(folderPath)
        if (created) {
            showFiles(currentPath)
            Helper.showSnackbar("New folder created: $name", mRecyclerView!!)
        } else {
            Helper.showSnackbar("Failed create folder: $name", mRecyclerView!!)
        }
    }

    override fun onNewFile(name: String, content: String, encrypt: Boolean) {
        val currentPath = currentPath
        val folderPath = currentPath + File.separator + name
        if (encrypt) {
            mStorage!!.setEncryptConfiguration(EncryptConfiguration.Builder()
                    .setEncryptContent(IVX, SECRET_KEY, SALT)
                    .build())
        }
        mStorage!!.createFile(folderPath, content)
        showFiles(currentPath)
        Helper.showSnackbar("New file created: $name", mRecyclerView!!)
    }

    override fun onConfirmDelete(path: String?) {
        if (mStorage!!.getFile(path!!).isDirectory) {
            mStorage!!.deleteDirectory(path)
            Helper.showSnackbar("Folder was deleted", mRecyclerView!!)
        } else {
            mStorage!!.deleteFile(path)
            Helper.showSnackbar("File was deleted", mRecyclerView!!)
        }
        showFiles(currentPath)
    }

    override fun onRename(fromPath: String, toPath: String) {
        mStorage!!.rename(fromPath, toPath)
        showFiles(currentPath)
        Helper.showSnackbar("Renamed", mRecyclerView!!)
    }

    //    @Override
    //    public boolean onCreateOptionsMenu(Menu menu) {
    //        MenuInflater inflater = getMenuInflater();
    //        inflater.inflate(R.menu.main_menu, menu);
    //        return true;
    //    }
    //
    //    @Override
    //    public boolean onOptionsItemSelected(MenuItem item) {
    //        switch (item.getItemId()) {
    //            case R.id.order:
    //                break;
    //            case R.id.filter:
    //                break;
    //        }
    //
    //        return super.onOptionsItemSelected(item);
    //    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager
                        .PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showFiles(mStorage!!.externalStorageDirectory)
        } else {
            finish()
        }
    }

    companion object {

        private const val PERMISSION_REQUEST_CODE = 1000
        private const val IVX = "abcdefghijklmnop"
        private const val SECRET_KEY = "secret1234567890"
        private val SALT = "0000111100001111".toByteArray()
    }
}
