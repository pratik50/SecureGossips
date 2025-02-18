package com.example.gossips

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gossips.adapters.MessageAdapter
import com.example.gossips.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class SecureModeActivity : AppCompatActivity() {

    private lateinit var chatRoomId: String
    private lateinit var secretKey: String
    private lateinit var mDbRef: DatabaseReference
    private lateinit var mAuth: FirebaseAuth
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageView
    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>
    private var isDialogShowing = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_secure_mode)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Adjust view for virtual keyboard
        val rootLayout = findViewById<ConstraintLayout>(R.id.main)
        val messageSection = findViewById<ConstraintLayout>(R.id.linearLayout1)
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootLayout.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootLayout.rootView.height
            val keypadHeight = screenHeight - r.bottom

            if (keypadHeight > screenHeight * 0.15) {
                messageSection.translationY = -keypadHeight.toFloat()
            } else {
                messageSection.translationY = 0f
            }
        }

        mAuth = FirebaseAuth.getInstance()

        // Retrieve the chatRoomId and secretKey from the intent
        chatRoomId = intent.getStringExtra("chatRoomId")!!
        secretKey = intent.getStringExtra("secretKey")!!

        mDbRef = FirebaseDatabase.getInstance().getReference()

        // Initialize toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.secure_chat_toolbar)
        setSupportActionBar(toolbar)

        // Initialize UI components
        messageEditText = findViewById(R.id.message_section)
        sendButton = findViewById(R.id.sent_btn)
        messageRecyclerView = findViewById(R.id.secure_chatRecyclerView)
        messageList = ArrayList()
        messageAdapter = MessageAdapter(this, messageList)

        // Setup RecyclerView
        messageRecyclerView.layoutManager = LinearLayoutManager(this)
        messageRecyclerView.adapter = messageAdapter

        // Set up listener for sending messages
        sendButton.setOnClickListener {
            val message = messageEditText.text.toString()
            val senderId = mAuth.currentUser?.uid

            if (message.isNotEmpty()) {
                sendSecureMessage(message, senderId!!)
                messageEditText.text.clear()
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        // Load messages from Firebase
        loadSecureMessages()

        listenForTermination()

        listenForWrongKey()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendSecureMessage(message: String, senderId: String) {
        val encryptedMessage = encryptMessage(message, secretKey)

        //Message object for storage
        val messageObject = Message(encryptedMessage, senderId)

        val messageId = mDbRef.child("chats").child(chatRoomId).child("securemessages").push().key
        if (messageId != null) {
            mDbRef.child("chats").child(chatRoomId).child("securemessages").child(messageId).setValue(messageObject)
                .addOnSuccessListener {
                    val originalMessageObject = Message(message, senderId)
                    if (!messageList.contains(originalMessageObject)) {
                        messageList.add(originalMessageObject)
                        messageAdapter.notifyItemInserted(messageList.size - 1)
                        messageRecyclerView.scrollToPosition(messageList.size - 1)
                    }

                    Toast.makeText(this, "Message sent securely", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadSecureMessages() {
        mDbRef.child("chats").child(chatRoomId).child("securemessages").addChildEventListener(object :
            ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val messageObject = snapshot.getValue(Message::class.java)

                if (messageObject != null) {
                    try {
                        val decryptedMessage = decryptMessage(messageObject.message!!, secretKey)
                        val displayMessageObject = Message(decryptedMessage, messageObject.senderId)
                        displayMessage(displayMessageObject)
                    } catch (e: Exception) {
                        showInvalidKeyError()
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun displayMessage(message: Message) {
        messageList.add(message)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        messageRecyclerView.scrollToPosition(messageList.size - 1)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun encryptMessage(message: String, secretKey: String): String {
        val fixedKey = fixKeyLength(secretKey)
        val key = SecretKeySpec(fixedKey, "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encryptedBytes = cipher.doFinal(message.toByteArray())
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun decryptMessage(encryptedMessage: String, secretKey: String): String {
        val fixedKey = fixKeyLength(secretKey)
        val key = SecretKeySpec(fixedKey, "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedMessage))
        return String(decryptedBytes)
    }

    private fun fixKeyLength(key: String): ByteArray {
        var fixedKey = key.toByteArray()
        if (fixedKey.size < 16) {
            fixedKey = fixedKey.copyOf(16)
        } else if (fixedKey.size > 16) {
            fixedKey = fixedKey.copyOf(16)
        }
        return fixedKey
    }

    private fun showInvalidKeyError() {
        // Create a dialog to show the error message
        val dialog = AlertDialog.Builder(this)
            .setTitle("Invalid Key")
            .setMessage("One of you entered wrong Key!! \nWe are terminating the session...")
            .setCancelable(false)
            .create()

        dialog.show()

        mDbRef.child("chats").child(chatRoomId).child("isWrong").setValue(true)

        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()
            terminateSession() // Call the method to terminate the session
            mDbRef.child("chats").child(chatRoomId).child("isWrong").removeValue()
        }, 7000)
    }

    // Add a listener for the isWrong field
    private fun listenForWrongKey() {
        mDbRef.child("chats").child(chatRoomId).child("isWrong").addValueEventListener(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.getValue(Boolean::class.java) == true) {
                    showInvalidKeyError()
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }

    // Method to terminate the chat session
    private fun terminateSession() {
        mDbRef.child("chats").child(chatRoomId).child("isTerminated").setValue(true)

        mDbRef.child("chats").child(chatRoomId).child("isTerminated").removeValue()
        mDbRef.child("chats").child(chatRoomId).child("securemessages").removeValue()
        finishAffinity()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.vanish_chat, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.vanish_chat) {
            showExitConfirmationDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showExitConfirmationDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Exit Chat")
            .setMessage("Do you really want to exit? \nSession will be Terminated!!")
            .setPositiveButton("Yes") { _, _ ->
                // Update the database to indicate the chat is terminating
                mDbRef.child("chats").child(chatRoomId).child("isTerminated").setValue(true)

                mDbRef.child("chats").child(chatRoomId).child("isTerminated").removeValue()
                mDbRef.child("chats").child(chatRoomId).child("securemessages").removeValue()

                finishAffinity()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun listenForTermination() {
        mDbRef.child("chats").child(chatRoomId).child("isTerminated").addValueEventListener(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.getValue(Boolean::class.java) == true) {
                    finishAffinity()
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        if (!isDialogShowing) {
            showExitConfirmationDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Update the database to indicate the chat is terminating
        mDbRef.child("chats").child(chatRoomId).child("isTerminated").setValue(true)

        mDbRef.child("chats").child(chatRoomId).child("isTerminated").removeValue()
        mDbRef.child("chats").child(chatRoomId).child("securemessages").removeValue()
    }

    override fun onPause() {
        super.onPause()
        // Update the database to indicate the chat is terminating
        mDbRef.child("chats").child(chatRoomId).child("isTerminated").setValue(true)

        mDbRef.child("chats").child(chatRoomId).child("isTerminated").removeValue()
        mDbRef.child("chats").child(chatRoomId).child("securemessages").removeValue()
    }
}