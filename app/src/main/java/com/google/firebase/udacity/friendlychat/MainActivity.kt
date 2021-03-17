/**
 * Copyright Google Inc. All Rights Reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import com.google.firebase.udacity.friendlychat.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {
    private var mMessageAdapter: MessageAdapter? = null
    private var mUsername: String? = null
    private lateinit var binding: ActivityMainBinding

    // Firebase instance variables
    private var mMessagesDatabaseReference: DatabaseReference? = null
    private var mChildEventListener: ChildEventListener? = null
    private var mAuthStateListener: FirebaseAuth.AuthStateListener? = null
    private var mFirebaseAuth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        checkAuth()
        init()
    }

    private fun checkAuth() {
        // Initialize Firebase components
        mFirebaseAuth = FirebaseAuth.getInstance()
        val database = Firebase.database.reference
        mMessagesDatabaseReference = database.child("messages")

        mAuthStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User is signed in
                onSignedInInitialize(user.displayName)
            } else {
                // User is signed out
                onSignedOutCleanup()
                val providers = arrayListOf(
                        AuthUI.IdpConfig.EmailBuilder().build(),
                        AuthUI.IdpConfig.GoogleBuilder().build())

                startActivityForResult(
                        AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setAvailableProviders(providers)
                                .build(),
                        RC_SIGN_IN)
            }

        }
    }

    private fun init() {
        mUsername = ANONYMOUS
        val friendlyMessages: List<FriendlyMessage> = ArrayList()
        mMessageAdapter = MessageAdapter(this, R.layout.item_message, friendlyMessages)
        binding.run {
            messageListView.adapter = mMessageAdapter
            progressBar.isVisible = false
            messageEditText.doOnTextChanged { text, _, _, _ ->
                sendButton.isEnabled = text.toString().trim { it <= ' ' }.isNotEmpty()
            }
            messageEditText.filters = arrayOf<InputFilter>(LengthFilter(DEFAULT_MSG_LENGTH_LIMIT))
            sendButton.setOnClickListener {
                val friendlyMessage = FriendlyMessage(messageEditText.text.toString(), mUsername, null)
                mMessagesDatabaseReference?.push()?.setValue(friendlyMessage)
                messageEditText.setText("")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {

                // Sign-in succeeded, set up the UI
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show()
            } else if (resultCode == RESULT_CANCELED) {
                // Sign in was canceled by the user, finish the activity
                Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mFirebaseAuth?.addAuthStateListener(mAuthStateListener)
    }

    override fun onPause() {
        super.onPause()
        if (mAuthStateListener != null) {
            mFirebaseAuth?.removeAuthStateListener(mAuthStateListener);
        }
        mMessageAdapter?.clear()
        detachDatabaseReadListener()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                AuthUI.getInstance().signOut(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onSignedInInitialize(username: String) {
        mUsername = username
        attachDatabaseReadListener()
    }

    private fun onSignedOutCleanup() {
        mUsername = ANONYMOUS
        mMessageAdapter?.clear()
        detachDatabaseReadListener()
    }

    private fun attachDatabaseReadListener() {
        if (mChildEventListener == null) {
            mChildEventListener = object : ChildEventListener {
                override fun onChildAdded(dataSnapshot: DataSnapshot, previousChildName: String?) {
                    val friendlyMessage = dataSnapshot.getValue<FriendlyMessage>()
                    mMessageAdapter?.add(friendlyMessage)
                    Log.d(TAG, "onChildAdded: " + friendlyMessage?.text)
                }

                override fun onChildChanged(dataSnapshot: DataSnapshot, previousChildName: String?) {
                    val friendlyMessage = dataSnapshot.getValue(FriendlyMessage::class.java)
                    Log.d(TAG, "onChildChanged: " + friendlyMessage?.text)
                }

                override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                    val friendlyMessage = dataSnapshot.getValue(FriendlyMessage::class.java)
                    Log.d(TAG, "onChildRemoved: " + friendlyMessage?.text)
                }

                override fun onChildMoved(dataSnapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(databaseError: DatabaseError) {}
            }
            mChildEventListener?.let {
                mMessagesDatabaseReference?.addChildEventListener(it)
            }
        }
    }

    private fun detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            mChildEventListener?.let {
                mMessagesDatabaseReference?.removeEventListener(it)
            }
            mChildEventListener = null
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val ANONYMOUS = "anonymous"
        const val DEFAULT_MSG_LENGTH_LIMIT = 1000
        const val RC_SIGN_IN = 1
    }
}