package com.snatik.storage.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView

import com.snatik.storage.EncryptConfiguration
import com.snatik.storage.Storage

/**
 * Created by sromku on June, 2017.
 */
class ViewTextActivity : AppCompatActivity() {

    private var mContentView: TextView? = null
    private var mPath: String? = null
    private var mStorage: Storage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(EXTRA_FILE_NAME)
        mPath = intent.getStringExtra(EXTRA_FILE_PATH)

        setContentView(R.layout.activity_view_text_file)
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        val drawerDrawable = DrawerArrowDrawable(this)
        drawerDrawable.color = resources.getColor(android.R.color.white)
        drawerDrawable.progress = 1f
        toolbar.navigationIcon = drawerDrawable

        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setTitle(name)
        supportActionBar!!.setHomeButtonEnabled(true)

        mContentView = findViewById<View>(R.id.content) as TextView
        mStorage = Storage(this)
        val bytes = mStorage!!.readFile(mPath!!)
        mContentView!!.text = String(bytes!!)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.text_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.decrypt -> {
                mStorage!!.setEncryptConfiguration(EncryptConfiguration.Builder()
                        .setEncryptContent(IVX, SECRET_KEY, SALT)
                        .build())
                val bytes = mStorage!!.readFile(mPath!!)
                if (bytes != null) {
                    mContentView!!.text = String(bytes)
                } else {
                    Helper.showSnackbar("Failed to decrypt", mContentView!!)
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    companion object {

        val EXTRA_FILE_NAME = "name"
        val EXTRA_FILE_PATH = "path"

        private val IVX = "abcdefghijklmnop"
        private val SECRET_KEY = "secret1234567890"
        private val SALT = "0000111100001111".toByteArray()
    }
}
