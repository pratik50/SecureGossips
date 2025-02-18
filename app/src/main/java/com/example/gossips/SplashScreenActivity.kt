package com.example.gossips

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SplashScreenActivity : AppCompatActivity() {


    private val auth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

    }

    override fun onStart() {
        super.onStart()

        runOnUiThread {
            Handler(Looper.getMainLooper()).postDelayed({
                if (auth.currentUser != null) {
                    // User is authenticated, so navigate to the home activity
                    startActivity(Intent(this@SplashScreenActivity, MainActivity::class.java))
                    finish()
                }else{
                    startActivity(Intent(this@SplashScreenActivity, LoginActivity::class.java))
                }
            },3000)
        }
    }
}