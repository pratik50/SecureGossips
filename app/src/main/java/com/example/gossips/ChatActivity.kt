package com.example.gossips

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gossips.adapters.MessageAdapter
import com.example.gossips.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*



class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageBox: EditText
    private lateinit var sendButton: ImageView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>
    private lateinit var mDbRef: DatabaseReference
    private lateinit var sharedPreferences: SharedPreferences

    private var chatRoomId: String?= null
    private var receiverUid: String? = null
    private var senderUid: String? = null
    private var isInitiator: Boolean = false  // Flag to track the initiator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Retrieve user data from intent
        receiverUid = intent.getStringExtra("uid")
        senderUid = FirebaseAuth.getInstance().currentUser?.uid
        val name = intent.getStringExtra("name")

        sharedPreferences = getSharedPreferences("SecureChatPrefs", Context.MODE_PRIVATE)

        mDbRef = FirebaseDatabase.getInstance().getReference()

        // Create a unique chat room ID
        chatRoomId = createChatRoomId(senderUid, receiverUid)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.secure_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = name

        initializeViews()

        // Listen for  messages
        listenForMessages()

        sendButton.setOnClickListener { sendMessage(senderUid) }

        // Listen for secure chat requests
        listenForSecureChatRequest()

        // Set up the listener for the dialogState
        setupDialogStateListener()
    }

    private fun createChatRoomId(senderUid: String?, receiverUid: String?): String {
        return if (senderUid!! < receiverUid!!) {
            senderUid + receiverUid
        } else {
            receiverUid + senderUid
        }
    }

    private fun initializeViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageBox = findViewById(R.id.messageBox)
        sendButton = findViewById(R.id.sentButton)
        messageList = ArrayList()
        messageAdapter = MessageAdapter(this, messageList)

        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = messageAdapter
    }

    private fun listenForMessages() {
        mDbRef.child("chats").child(chatRoomId!!).child("messages")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messageList.clear()  // Clear previous data
                    for (postSnapshot in snapshot.children) {
                        val message = postSnapshot.getValue(Message::class.java)
                        message?.let { messageList.add(it) }
                    }
                    messageAdapter.notifyDataSetChanged()
                    chatRecyclerView.scrollToPosition(messageList.size - 1) // Auto-scroll to the latest message
                }

                override fun onCancelled(error: DatabaseError) {
                    // Log error if needed
                }
            })
    }

    private fun sendMessage(senderUid: String?) {
        val messageText = messageBox.text.toString().trim()
        if (messageText.isNotEmpty()) {
            val messageObject = Message(messageText, senderUid)

            // Add message to the common room
            mDbRef.child("chats").child(chatRoomId!!).child("messages").push()
                .setValue(messageObject)
            messageBox.setText("")  // Clear the input box
        }
    }

    // Inflating menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.secure_chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.secure_chat -> {
                if (!isInitiator) {
                    showSecretKeyDialog()  // Allow the user to enter secret key again
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSecretKeyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_secret_key, null)
        val secretKeyInput = dialogView.findViewById<EditText>(R.id.secretKeyInput)

        AlertDialog.Builder(this)
            .setTitle("Enter Secret Key")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val secretKey = secretKeyInput.text.toString().trim()
                if (secretKey.isNotEmpty()) {
                    // Store the secret key temporarily
                    sharedPreferences.edit().putString("secretKey", secretKey).apply()

                    Log.d("making", "onDataChange: inside the sender user $secretKey")

                    sendSecureChatRequest()
                    isInitiator = true // Prevent multiple dialog openings
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSecureChatRequest() {
        val secureChatRequest = mapOf(
            "requesterUid" to senderUid,
            "status" to "pending"
        )

        mDbRef.child("secureChatRequests").child(chatRoomId!!).setValue(secureChatRequest)
    }

    private fun listenForSecureChatRequest() {
        mDbRef.child("secureChatRequests").child(chatRoomId!!)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        handleSecureChatRequest(snapshot)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
    }

    private fun handleSecureChatRequest(snapshot: DataSnapshot) {
        val status = snapshot.child("status").value.toString()

        when (status) {
            "pending" -> {
                if (!isInitiator) {
                    showSecureChatRequestDialog()
                }
            }
            "accepted" -> {
                if (!isInitiator) {
                    showSecretKeyDialogForAcceptedRequest()
                }
            }
            "declined" -> {
                if (isInitiator) {
                    showToast("Secure chat request was declined.")
                    declineSecureChat()
                }
            }
        }
    }

    private fun showSecureChatRequestDialog() {
        AlertDialog.Builder(this)
            .setTitle("Secure Chat Request")
            .setMessage("The other user wants to start a secure chat. Accept?")
            .setPositiveButton("Yes") { _, _ -> acceptSecureChat() }
            .setNegativeButton("No") { _, _ -> declineSecureChat() }
            .setCancelable(false)
            .show()
    }

    // Update the acceptSecureChat method
    private fun acceptSecureChat() {
        // Update the request status to "accepted"
        mDbRef.child("secureChatRequests").child(chatRoomId!!).child("status").setValue("accepted")
            .addOnCompleteListener {
                mDbRef.child("secureChatRequests").child(chatRoomId!!).removeValue()
            }
    }

    private fun showSecretKeyDialogForAcceptedRequest() {

        // Create a new node to track the secret key dialog state
        val dialogNodeRef = mDbRef.child("CheckNodePoint").child(chatRoomId!!).child("dialogState")
        dialogNodeRef.setValue(false)

        val dialogView = layoutInflater.inflate(R.layout.dialog_secret_key, null)
        val secretKeyInput = dialogView.findViewById<EditText>(R.id.secretKeyInput)

        AlertDialog.Builder(this)
            .setTitle("Enter Secret Key")
            .setMessage("Enter the exact Provided Key by Sender else chat will not decrypt")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val secretKey = secretKeyInput.text.toString()
                if (secretKey.isNotEmpty()) {
                    // Store the secret key temporarily
                    sharedPreferences.edit().putString("secretKey", secretKey).apply()
                    Log.d("making", "onDataChange: inside the Accepting user $secretKey")

                    // Update the dialog state to true when the key is entered
                    dialogNodeRef.setValue(true)

                    // Notify both users about the secure chat
                    navigateToSecureChat(secretKey)


                }
            }
            .setCancelable(false)
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun declineSecureChat() {
        mDbRef.child("secureChatRequests").child(chatRoomId!!).child("status").setValue("declined")
            .addOnCompleteListener {
                isInitiator = false
                mDbRef.child("secureChatRequests").child(chatRoomId!!).removeValue()
            }
    }

    private fun navigateToSecureChat(secretKey: String) {
        // This method will handle redirecting to SecureChatActivity for the receiver
        val intent = Intent(this, SecureModeActivity::class.java).apply {
            putExtra("chatRoomId", chatRoomId)
            putExtra("secretKey", secretKey)
        }
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupDialogStateListener() {
        val dialogNodeRef = mDbRef.child("CheckNodePoint").child(chatRoomId!!).child("dialogState")

        // Listen to changes in the dialog state
        dialogNodeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    if (snapshot.getValue(Boolean::class.java) == true) {
                        // Start SecureChatActivity only if it's the sender's side
                        if (isInitiator) {
                            // Retrieve the secret key from SharedPreferences
                            val secretKey = sharedPreferences.getString("secretKey", null)

                            if (secretKey != null) {
                                // Create an intent and pass both chatRoomId and secretKey to the new activity
                                val intent = Intent(this@ChatActivity, SecureModeActivity::class.java).apply {
                                    putExtra("chatRoomId", chatRoomId)
                                    putExtra("secretKey", secretKey)
                                }

                                mDbRef.child("CheckNodePoint").child(chatRoomId!!).removeValue()

                                startActivity(intent)

                                // Remove the listener to prevent multiple intents
                                dialogNodeRef.removeEventListener(this)
                            } else {
                                Log.e("ChatActivity", "Secret key is missing!")
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Error occurred: ${error.message}")
            }
        })
    }
}