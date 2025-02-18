package com.example.gossips

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gossips.adapters.UserAdapter
import com.example.gossips.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

//Not yet implemented under process

class EmailSearchActivity : AppCompatActivity() {

    private lateinit var emailRecyclerView: RecyclerView
    private lateinit var filteredList: ArrayList<User>
    private lateinit var adapter: UserAdapter
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mDbRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_search)

        mAuth = FirebaseAuth.getInstance()
        mDbRef = FirebaseDatabase.getInstance().getReference("user")

        emailRecyclerView = findViewById(R.id.emailRecyclerView)
        emailRecyclerView.layoutManager = LinearLayoutManager(this)

        filteredList = ArrayList()
        adapter = UserAdapter(this, filteredList) // Initialize with an empty list
        emailRecyclerView.adapter = adapter

        // Set the toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.email_toolbar)
        setSupportActionBar(toolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        Log.d("EmailSearchActivity", "onCreateOptionsMenu called")
        menuInflater.inflate(R.menu.search_menu, menu)  // Inflate the menu
        val searchItem = menu?.findItem(R.id.menu_search)
        val searchView = searchItem?.actionView as? SearchView
        if (searchView == null) {
            Log.e("EmailSearchActivity", "SearchView is null")
        } else {
            Log.d("EmailSearchActivity", "SearchView initialized")
        }

        // Set up SearchView properties
        searchView?.queryHint = "Search by email..."
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.d("SearchViewmm", "Text submitted: $query")
                if (!query.isNullOrEmpty()) {
                    searchUsersByEmail(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        return true
    }

    private fun searchUsersByEmail(query: String?) {
        filteredList.clear()

        if (!query.isNullOrEmpty()) {
            Log.d("FirebaseQuery", "Searching for users with email containing: $query")

            // Query Firebase database
            mDbRef.child("user").child("email")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        Log.d("FirebaseQuery", "onDataChange triggered, snapshot exists: ${snapshot.exists()}")

                        if (snapshot.exists()) {
                            for (userSnapshot in snapshot.children) {
                                val user = userSnapshot.getValue(User::class.java)
                                Log.d("FirebaseData", "User retrieved: $user")

                                // Check if email or name contains the search query
                                if (user != null &&
                                    (user.email?.contains(query, ignoreCase = true) == true ||
                                            user.name?.contains(query, ignoreCase = true) == true)) {
                                    Log.d("FirebaseData", "User matched: ${user.email}")
                                    if (user.uid != mAuth.currentUser?.uid) {
                                        filteredList.add(user)
                                    }
                                }
                            }
                            Log.d("EmailSearchActivity", "Filtered users count: ${filteredList.size}")
                        } else {
                            Log.d("FirebaseQuery", "No users found for the query")
                        }

                        adapter.updateList(filteredList)
                        emailRecyclerView.visibility = if (filteredList.isNotEmpty()) View.VISIBLE else View.GONE
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("EmailSearchActivity", "Error searching users: ${error.message}")
                    }
                })
        } else {
            Log.d("FirebaseQuery", "Search query is empty, hiding RecyclerView")
            emailRecyclerView.visibility = View.GONE
        }
    }

    private fun startChatWithUser(user: User) {
        val intent = Intent(this@EmailSearchActivity, ChatActivity::class.java)
        intent.putExtra("userId", user.uid)
        startActivity(intent)
    }
}