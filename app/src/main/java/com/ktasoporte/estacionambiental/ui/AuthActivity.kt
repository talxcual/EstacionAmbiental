package com.ktasoporte.estacionambiental.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.auth.FirebaseUser
import com.ktasoporte.estacionambiental.R

class AuthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var db: FirebaseFirestore

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var btnLogin: Button
    private lateinit var btnGoogleSignIn: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        auth = Firebase.auth
        db = Firebase.firestore

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("AuthActivity", "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("AuthActivity", "Google sign in failed", e)
                val errorMessage = when (e.statusCode) {
                    GoogleSignInStatusCodes.CANCELED -> getString(R.string.google_signin_cancelled)
                    GoogleSignInStatusCodes.NETWORK_ERROR -> getString(R.string.google_signin_network_error)
                    10 -> "Error de desarrollador (10): El SHA-1 de firma o el ID de cliente Web no coinciden con la consola de Firebase."
                    12500 -> "Error de configuración (12500): Verifica el SHA-1 en Firebase Console y que la firma sea válida."
                    else -> getString(R.string.google_signin_failed_generic, "Código ${e.statusCode}: ${e.message}")
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                tvStatus.text = errorMessage
            }
        }

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnRegister = findViewById(R.id.btnRegister)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        tvStatus = findViewById(R.id.tvStatus)

        btnRegister.setOnClickListener {
            registerUser()
        }

        btnLogin.setOnClickListener {
            loginUser()
        }

        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        startNeonGlowAnimation()
    }

    private fun startNeonGlowAnimation() {
        val viewEmailGlow = findViewById<android.view.View>(R.id.viewEmailGlow)
        val viewPasswordGlow = findViewById<android.view.View>(R.id.viewPasswordGlow)

        val emailAnimator = android.animation.ObjectAnimator.ofFloat(viewEmailGlow, "alpha", 0.3f, 1.0f).apply {
            duration = 1500
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
        }

        val passwordAnimator = android.animation.ObjectAnimator.ofFloat(viewPasswordGlow, "alpha", 0.3f, 1.0f).apply {
            duration = 1500
            startDelay = 300
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
        }

        emailAnimator.start()
        passwordAnimator.start()
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        saveUserDataToFirestore(user, user.displayName ?: user.email ?: "Usuario Google")
                        Toast.makeText(this, getString(R.string.google_auth_success), Toast.LENGTH_SHORT).show()
                        tvStatus.text = getString(R.string.google_auth_success_email, user.email)
                        navigateToDashboard()
                    } else {
                        Toast.makeText(this, "Usuario Google nulo después de la autenticación.", Toast.LENGTH_SHORT).show()
                        tvStatus.text = "Error: Usuario Google nulo."
                    }
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthUserCollisionException -> getString(R.string.google_auth_collision_error)
                        else -> getString(R.string.google_auth_firebase_failed_generic, exception?.message)
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    tvStatus.text = errorMessage
                }
            }
    }

    private fun registerUser() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        etEmail.error = null
        etPassword.error = null

        if (email.isEmpty()) {
            etEmail.error = getString(R.string.error_email_empty)
            etEmail.requestFocus()
            tvStatus.text = getString(R.string.error_email_empty_status)
            return
        }
        if (password.isEmpty()) {
            etPassword.error = getString(R.string.error_password_empty)
            etPassword.requestFocus()
            tvStatus.text = getString(R.string.error_password_empty_status)
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        saveUserDataToFirestore(user, email.substringBefore("@"))
                        Toast.makeText(this, getString(R.string.register_success, user.email), Toast.LENGTH_SHORT).show()
                        tvStatus.text = getString(R.string.register_success_status, user.email)
                        navigateToDashboard()
                    } else {
                        Toast.makeText(this, "Usuario registrado nulo.", Toast.LENGTH_SHORT).show()
                        tvStatus.text = "Error: Usuario registrado nulo."
                    }
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthWeakPasswordException -> {
                            etPassword.error = getString(R.string.error_password_weak_et)
                            etPassword.requestFocus()
                            getString(R.string.error_password_weak_toast)
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            etEmail.error = getString(R.string.error_email_invalid_et)
                            etEmail.requestFocus()
                            getString(R.string.error_credentials_invalid_toast)
                        }
                        is FirebaseAuthUserCollisionException -> {
                            etEmail.error = getString(R.string.error_email_in_use_et)
                            etEmail.requestFocus()
                            getString(R.string.error_email_in_use_toast)
                        }
                        else -> getString(R.string.register_failed_generic, exception?.message)
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    tvStatus.text = getString(R.string.register_failed_status, errorMessage)
                }
            }
    }

    private fun loginUser() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        etEmail.error = null
        etPassword.error = null

        if (email.isEmpty()) {
            etEmail.error = getString(R.string.error_email_empty)
            etEmail.requestFocus()
            tvStatus.text = getString(R.string.error_email_empty_status)
            return
        }
        if (password.isEmpty()) {
            etPassword.error = getString(R.string.error_password_empty)
            etPassword.requestFocus()
            tvStatus.text = getString(R.string.error_password_empty_status)
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        Toast.makeText(this, getString(R.string.login_success, user.email), Toast.LENGTH_SHORT).show()
                        tvStatus.text = getString(R.string.login_success_status, user.email)
                        navigateToDashboard()
                    } else {
                        Toast.makeText(this, "Usuario de inicio de sesión nulo.", Toast.LENGTH_SHORT).show()
                        tvStatus.text = "Error: Usuario de inicio de sesión nulo."
                    }
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidCredentialsException -> {
                            getString(R.string.error_credentials_invalid_toast)
                        }
                        else -> getString(R.string.login_failed_generic, exception?.message)
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    tvStatus.text = getString(R.string.login_failed_status, errorMessage)
                }
            }
    }

    private fun saveUserDataToFirestore(user: FirebaseUser, userName: String) {
        val userId = user.uid
        val userEmail = user.email
        val userPhotoUrl = user.photoUrl?.toString()

        val userData = hashMapOf(
            "uid" to userId,
            "email" to userEmail,
            "name" to userName,
            "photoUrl" to userPhotoUrl,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Log.d("AuthActivity", "Datos de usuario guardados en Firestore para UID: $userId")
            }
            .addOnFailureListener { e ->
                Log.w("AuthActivity", "Error al guardar datos de usuario en Firestore", e)
                Toast.makeText(this, "Error al guardar datos de usuario: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            navigateToDashboard()
        }
    }
}
