package com.example.aiaiavatardemo

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etUsername = findViewById(R.id.etUsername)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogin).setOnClickListener {
            val username = etUsername.text?.toString()?.trim() ?: ""
            if (TextUtils.isEmpty(username)) {
                Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Navigate to MainActivity with userId
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("userId", username)
            startActivity(intent)
        }
    }
}