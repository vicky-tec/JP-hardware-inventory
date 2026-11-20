package com.example.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.theme.AvailableGreen
import com.example.ui.theme.AvailableGreenBg
import com.example.ui.theme.BorderStone
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDark
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object PhoneAuthHelper {
    fun shopReferenceId(uid: String): String {
        val clean = uid.replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
        return "DS-${clean.take(8).ifBlank { "SHOP" }}"
    }

    fun normalizeIndianPhone(rawInput: String): String? {
        val digitsOnly = rawInput.filter { it.isDigit() }
        val tenDigits = when {
            digitsOnly.length == 10 -> digitsOnly
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> digitsOnly.drop(1)
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> digitsOnly.drop(2)
            else -> return null
        }
        return if (tenDigits.length == 10) "+91$tenDigits" else null
    }

    fun formatAuthError(error: Throwable): String {
        return when (error) {
            is FirebaseAuthUserCollisionException ->
                "This phone number is already linked to another JP Hardware account. Sign out and sign in with that phone number, or use a different mobile number."
            is FirebaseAuthInvalidCredentialsException ->
                "The OTP or mobile number is invalid. Please check and try again."
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "Phone OTP sign-in is not enabled yet in Firebase Console Authentication settings. Please enable Phone provider and configure SHA fingerprints."
                "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" ->
                    "Too many SMS OTP requests right now. Please wait 60 seconds or sign in with Google."
                "ERROR_CAPTCHA_CHECK_FAILED", "ERROR_APP_NOT_AUTHORIZED" ->
                    "App verification (Play Integrity / reCAPTCHA or SHA certificate) is required for Phone OTP in Firebase Console."
                else -> error.localizedMessage ?: "Phone verification failed (${error.errorCode})."
            }
            else -> error.localizedMessage ?: "Verification failed. Please try again."
        }
    }

    fun sendOtp(
        activity: Activity,
        e164Phone: String,
        resendingToken: PhoneAuthProvider.ForceResendingToken?,
        linkToCurrentUser: Boolean,
        scope: CoroutineScope,
        onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
        onAutoVerifiedSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                scope.launch {
                    try {
                        val user = Firebase.auth.currentUser
                        if (linkToCurrentUser && user != null) {
                            user.linkWithCredential(credential).await()
                        } else {
                            Firebase.auth.signInWithCredential(credential).await()
                        }
                        onAutoVerifiedSuccess()
                    } catch (e: Exception) {
                        onError(formatAuthError(e))
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w("PhoneAuth", "Verification failed", e)
                onError(formatAuthError(e))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId, token)
            }
        }

        val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(e164Phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resendingToken != null) {
            builder.setForceResendingToken(resendingToken)
        }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    suspend fun submitOtpCode(
        verificationId: String,
        otpCode: String,
        linkToCurrentUser: Boolean
    ): Result<Unit> {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, otpCode.trim())
            val user = Firebase.auth.currentUser
            if (linkToCurrentUser && user != null) {
                user.linkWithCredential(credential).await()
            } else {
                Firebase.auth.signInWithCredential(credential).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(formatAuthError(e), e))
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var checkedAutoSignIn by remember { mutableStateOf(false) }

    var showPhoneOtpSection by remember { mutableStateOf(false) }
    var phoneDigits by remember { mutableStateOf("") }
    var otpDigits by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var resendCooldownSeconds by remember { mutableIntStateOf(0) }
    var isPhoneBusy by remember { mutableStateOf(false) }

    LaunchedEffect(resendCooldownSeconds) {
        if (resendCooldownSeconds > 0) {
            delay(1000L)
            resendCooldownSeconds -= 1
        }
    }

    LaunchedEffect(Unit) {
        if (!checkedAutoSignIn) {
            checkedAutoSignIn = true
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ForestGreen, ForestGreenDark)
                )
            )
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.96f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(84.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.jp_hardware_logo),
                    contentDescription = "JP Hardware Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Simple Stock, Sales & Profit Register\nfor Hardware & Building Material Shops",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        color = AvailableGreenBg,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("firebase_ready_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = "Cloud Firestore ready",
                                tint = AvailableGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Cloud Firestore Backup Ready",
                                style = MaterialTheme.typography.labelMedium,
                                color = AvailableGreen
                            )
                        }
                    }

                    FeatureBullet("Track pipes, sariya, nuts, bolts, taps & cement")
                    FeatureBullet("Instant low-stock & out-of-stock alerts")
                    FeatureBullet("Accurate profit with weighted average cost")

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = "Sign-in error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                onAuthCancelled = {
                                    isLoading = false
                                },
                                scope = scope
                            )
                        },
                        enabled = !isLoading && !isPhoneBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreen,
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Signing in...", style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderStone)
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderStone)
                    }

                    OutlinedButton(
                        onClick = {
                            errorMessage = null
                            showPhoneOtpSection = !showPhoneOtpSection
                        },
                        enabled = !isLoading && !isPhoneBusy,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("phone_sign_in_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showPhoneOtpSection) "Hide Mobile OTP Login" else "Use +91 Mobile OTP (Optional)"
                        )
                    }

                    AnimatedVisibility(visible = showPhoneOtpSection) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderStone, MaterialTheme.shapes.medium)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Indian Mobile Sign-In (+91)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = phoneDigits,
                                onValueChange = { raw ->
                                    errorMessage = null
                                    phoneDigits = raw.filter { it.isDigit() }.take(10)
                                },
                                prefix = { Text("+91 ", fontWeight = FontWeight.Bold) },
                                label = { Text("10-digit Mobile Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                enabled = !isPhoneBusy && verificationId == null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_number_input")
                            )

                            if (verificationId == null) {
                                Button(
                                    onClick = {
                                        val normalized = PhoneAuthHelper.normalizeIndianPhone(phoneDigits)
                                        val activity = context as? Activity
                                        when {
                                            normalized == null -> errorMessage = "Enter a valid 10-digit Indian mobile number."
                                            activity == null -> errorMessage = "Activity context unavailable for OTP."
                                            else -> {
                                                errorMessage = null
                                                isPhoneBusy = true
                                                PhoneAuthHelper.sendOtp(
                                                    activity = activity,
                                                    e164Phone = normalized,
                                                    resendingToken = null,
                                                    linkToCurrentUser = false,
                                                    scope = scope,
                                                    onCodeSent = { vId, token ->
                                                        isPhoneBusy = false
                                                        verificationId = vId
                                                        resendToken = token
                                                        resendCooldownSeconds = 60
                                                    },
                                                    onAutoVerifiedSuccess = {
                                                        isPhoneBusy = false
                                                        onAuthSuccess()
                                                    },
                                                    onError = { msg ->
                                                        isPhoneBusy = false
                                                        errorMessage = msg
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    enabled = !isPhoneBusy && phoneDigits.length == 10,
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("send_otp_button")
                                ) {
                                    Text(if (isPhoneBusy) "Sending OTP..." else "Send 6-Digit OTP")
                                }
                            } else {
                                OutlinedTextField(
                                    value = otpDigits,
                                    onValueChange = { raw ->
                                        errorMessage = null
                                        otpDigits = raw.filter { it.isDigit() }.take(6)
                                    },
                                    label = { Text("Enter 6-Digit OTP") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    enabled = !isPhoneBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("otp_code_input")
                                )

                                Button(
                                    onClick = {
                                        val vId = verificationId ?: return@Button
                                        if (otpDigits.length < 6) {
                                            errorMessage = "Enter all 6 OTP digits."
                                            return@Button
                                        }
                                        errorMessage = null
                                        isPhoneBusy = true
                                        scope.launch {
                                            val res = PhoneAuthHelper.submitOtpCode(
                                                verificationId = vId,
                                                otpCode = otpDigits,
                                                linkToCurrentUser = false
                                            )
                                            isPhoneBusy = false
                                            res.fold(
                                                onSuccess = { onAuthSuccess() },
                                                onFailure = { err -> errorMessage = err.localizedMessage }
                                            )
                                        }
                                    },
                                    enabled = !isPhoneBusy && otpDigits.length == 6,
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("verify_otp_button")
                                ) {
                                    Text(if (isPhoneBusy) "Verifying..." else "Verify OTP & Open Shop")
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            verificationId = null
                                            otpDigits = ""
                                            errorMessage = null
                                        },
                                        enabled = !isPhoneBusy
                                    ) {
                                        Text("Change Number")
                                    }

                                    TextButton(
                                        onClick = {
                                            val normalized = PhoneAuthHelper.normalizeIndianPhone(phoneDigits) ?: return@TextButton
                                            val activity = context as? Activity ?: return@TextButton
                                            errorMessage = null
                                            isPhoneBusy = true
                                            PhoneAuthHelper.sendOtp(
                                                activity = activity,
                                                e164Phone = normalized,
                                                resendingToken = resendToken,
                                                linkToCurrentUser = false,
                                                scope = scope,
                                                onCodeSent = { vId, token ->
                                                    isPhoneBusy = false
                                                    verificationId = vId
                                                    resendToken = token
                                                    resendCooldownSeconds = 60
                                                },
                                                onAutoVerifiedSuccess = {
                                                    isPhoneBusy = false
                                                    onAuthSuccess()
                                                },
                                                onError = { msg ->
                                                    isPhoneBusy = false
                                                    errorMessage = msg
                                                }
                                            )
                                        },
                                        enabled = !isPhoneBusy && resendCooldownSeconds == 0
                                    ) {
                                        Text(
                                            if (resendCooldownSeconds > 0) "Resend in ${resendCooldownSeconds}s" else "Resend OTP"
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Tip: If you already use Google Sign-In, sign in with Google first and link your +91 number in Shop Account settings so your existing data stays intact.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Your shop register stays safely linked to your signed-in account across phone changes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureBullet(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = ForestGreen,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit = {},
    scope: CoroutineScope,
    linkToCurrentUser: Boolean = false
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activity = context as? Activity
                ?: throw IllegalStateException("Activity context required for Google Sign-In")
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val googleCred = GoogleAuthProvider.getCredential(token, null)
                val existingUser = Firebase.auth.currentUser
                if (linkToCurrentUser && existingUser != null) {
                    existingUser.linkWithCredential(googleCred).await()
                } else {
                    Firebase.auth.signInWithCredential(googleCred).await()
                }
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: FirebaseAuthUserCollisionException) {
            onAuthError("This Google account is already linked to another JP Hardware shop.")
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
