package com.example.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.ui.theme.AvailableGreen
import com.example.ui.theme.AvailableGreenBg
import com.example.ui.theme.BorderStone
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.OutOfStockRed
import com.example.ui.theme.OutOfStockRedBg
import com.example.ui.theme.WarmStoneSurface
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AccountSheetDialog(
    currentUserId: String,
    userEmail: String?,
    userPhone: String?,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val shopRefId = remember(currentUserId) { PhoneAuthHelper.shopReferenceId(currentUserId) }
    var linkedEmail by remember(userEmail) { mutableStateOf(userEmail) }
    var linkedPhone by remember(userPhone) { mutableStateOf(userPhone) }

    var phoneDigits by remember { mutableStateOf("") }
    var otpDigits by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var cooldown by remember { mutableIntStateOf(0) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000L)
            cooldown -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = ForestGreen)
                Text("Shop Account & Sign-In", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Shop Reference ID Card (Non-password support code)
                Surface(
                    color = WarmStoneSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderStone, MaterialTheme.shapes.medium)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shop Reference ID",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = shopRefId,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("shop_reference_id")
                            )
                            Text(
                                text = "Reference code for support only (never a password).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Shop Reference ID", shopRefId))
                                isError = false
                                statusMessage = "Copied $shopRefId"
                            }
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Shop Reference ID")
                        }
                    }
                }

                // Linked Providers Summary
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Connected Sign-In Methods",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    ProviderStatusRow(
                        label = "Google Account",
                        value = linkedEmail ?: "Not linked",
                        isLinked = !linkedEmail.isNullOrBlank()
                    )
                    ProviderStatusRow(
                        label = "Mobile Number (+91)",
                        value = linkedPhone ?: "Not linked",
                        isLinked = !linkedPhone.isNullOrBlank()
                    )
                }

                if (statusMessage != null) {
                    Surface(
                        color = if (isError) OutOfStockRedBg else AvailableGreenBg,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) OutOfStockRed else AvailableGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = statusMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) OutOfStockRed else AvailableGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Link Phone OTP to preserve current UID
                if (linkedPhone.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderStone, MaterialTheme.shapes.medium)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = ForestGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Link +91 Mobile OTP",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Linking adds OTP sign-in to this same shop register without losing any data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = phoneDigits,
                            onValueChange = { phoneDigits = it.filter { c -> c.isDigit() }.take(10) },
                            prefix = { Text("+91 ") },
                            label = { Text("10-digit Mobile Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            enabled = !isBusy && verificationId == null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("link_phone_input")
                        )

                        if (verificationId == null) {
                            Button(
                                onClick = {
                                    val normalized = PhoneAuthHelper.normalizeIndianPhone(phoneDigits)
                                    val activity = context as? Activity
                                    when {
                                        normalized == null -> {
                                            isError = true
                                            statusMessage = "Enter a valid 10-digit Indian mobile number."
                                        }
                                        activity == null -> {
                                            isError = true
                                            statusMessage = "Unable to start OTP from current screen."
                                        }
                                        else -> {
                                            isError = false
                                            statusMessage = null
                                            isBusy = true
                                            PhoneAuthHelper.sendOtp(
                                                activity = activity,
                                                e164Phone = normalized,
                                                resendingToken = null,
                                                linkToCurrentUser = true,
                                                scope = scope,
                                                onCodeSent = { vId, token ->
                                                    isBusy = false
                                                    verificationId = vId
                                                    resendToken = token
                                                    cooldown = 60
                                                    isError = false
                                                    statusMessage = "OTP sent to $normalized"
                                                },
                                                onAutoVerifiedSuccess = {
                                                    isBusy = false
                                                    linkedPhone = Firebase.auth.currentUser?.phoneNumber ?: normalized
                                                    isError = false
                                                    statusMessage = "Mobile number linked! Same Shop ID preserved."
                                                },
                                                onError = { msg ->
                                                    isBusy = false
                                                    isError = true
                                                    statusMessage = msg
                                                }
                                            )
                                        }
                                    }
                                },
                                enabled = !isBusy && phoneDigits.length == 10,
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isBusy) "Sending OTP..." else "Send OTP to Link")
                            }
                        } else {
                            OutlinedTextField(
                                value = otpDigits,
                                onValueChange = { otpDigits = it.filter { c -> c.isDigit() }.take(6) },
                                label = { Text("6-Digit OTP") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val vId = verificationId ?: return@Button
                                    isBusy = true
                                    scope.launch {
                                        val res = PhoneAuthHelper.submitOtpCode(
                                            verificationId = vId,
                                            otpCode = otpDigits,
                                            linkToCurrentUser = true
                                        )
                                        isBusy = false
                                        res.fold(
                                            onSuccess = {
                                                linkedPhone = Firebase.auth.currentUser?.phoneNumber
                                                verificationId = null
                                                otpDigits = ""
                                                isError = false
                                                statusMessage = "Mobile number linked to your shop!"
                                            },
                                            onFailure = { err ->
                                                isError = true
                                                statusMessage = err.localizedMessage
                                            }
                                        )
                                    }
                                },
                                enabled = !isBusy && otpDigits.length == 6,
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isBusy) "Linking..." else "Verify & Link Phone")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = { verificationId = null; otpDigits = "" }) {
                                    Text("Change")
                                }
                                TextButton(
                                    enabled = !isBusy && cooldown == 0,
                                    onClick = {
                                        val normalized = PhoneAuthHelper.normalizeIndianPhone(phoneDigits) ?: return@TextButton
                                        val activity = context as? Activity ?: return@TextButton
                                        isBusy = true
                                        PhoneAuthHelper.sendOtp(
                                            activity = activity,
                                            e164Phone = normalized,
                                            resendingToken = resendToken,
                                            linkToCurrentUser = true,
                                            scope = scope,
                                            onCodeSent = { vId, token ->
                                                isBusy = false
                                                verificationId = vId
                                                resendToken = token
                                                cooldown = 60
                                            },
                                            onAutoVerifiedSuccess = {
                                                isBusy = false
                                                linkedPhone = Firebase.auth.currentUser?.phoneNumber ?: normalized
                                            },
                                            onError = { msg ->
                                                isBusy = false
                                                isError = true
                                                statusMessage = msg
                                            }
                                        )
                                    }
                                ) {
                                    Text(if (cooldown > 0) "Resend (${cooldown}s)" else "Resend OTP")
                                }
                            }
                        }
                    }
                }

                // Link Google if signed in via Phone only
                if (linkedEmail.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            isBusy = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                linkToCurrentUser = true,
                                onAuthSuccess = {
                                    isBusy = false
                                    linkedEmail = Firebase.auth.currentUser?.email
                                    isError = false
                                    statusMessage = "Google account linked to this shop!"
                                },
                                onAuthError = { msg ->
                                    isBusy = false
                                    isError = true
                                    statusMessage = msg
                                },
                                onAuthCancelled = { isBusy = false },
                                scope = scope
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Link Google Account")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onSignOut()
            }) {
                Text("Sign Out", color = OutOfStockRed)
            }
        }
    )
}

@Composable
private fun ProviderStatusRow(
    label: String,
    value: String,
    isLinked: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isLinked) FontWeight.Bold else FontWeight.Normal,
            color = if (isLinked) ForestGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
