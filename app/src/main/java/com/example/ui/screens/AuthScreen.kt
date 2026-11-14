package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.repository.TelegramRepository
import com.example.tdlib.CountryItem
import com.example.tdlib.TdAuthState
import com.example.tdlib.WorldCountries

val sampleCountries: List<CountryItem> = WorldCountries.all

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    repository: TelegramRepository,
    onAuthSuccess: () -> Unit
) {
    val authState by repository.authState.collectAsState()
    val allCountries = remember { repository.getWorldCountries() }

    var selectedCountry by remember {
        mutableStateOf<CountryItem?>(allCountries.find { it.code == "US" } ?: allCountries.first())
    }
    var dialCodeDigits by remember { mutableStateOf(selectedCountry?.dialCode?.removePrefix("+") ?: "1") }
    var phoneNumber by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showApiDialog by remember { mutableStateOf(false) }
    var authModeTab by remember { mutableIntStateOf(0) } // 0: Phone, 1: QR

    LaunchedEffect(authState) {
        if (authState is TdAuthState.Ready) {
            onAuthSuccess()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "RustGram",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showApiDialog = true },
                        modifier = Modifier.testTag("auth_api_config_btn")
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "Configure API Credentials",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.rustgram_icon),
                    contentDescription = "RustGram Logo",
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.height(18.dp))

            when (val state = authState) {
                is TdAuthState.WaitCode -> {
                    Text(
                        text = state.phoneNumber,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "We have sent you a Telegram verification code. Please enter it below.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = {
                            if (it.length <= 6) codeInput = it
                            if (it.length == 5) repository.submitVerificationCode(it)
                        },
                        placeholder = { Text("12345") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (codeInput.isNotEmpty()) repository.submitVerificationCode(codeInput)
                        }),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .testTag("verification_code_input")
                    )

                    Spacer(Modifier.height(22.dp))

                    Button(
                        onClick = { repository.submitVerificationCode(codeInput) },
                        enabled = codeInput.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_code_button")
                    ) {
                        Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = {
                        repository.submitPhoneNumber("+$dialCodeDigits${phoneNumber.replace(" ", "")}")
                    }) {
                        Text("Resend Code")
                    }
                }

                is TdAuthState.WaitPassword -> {
                    Text(
                        text = "Two-Step Verification",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Your account is protected by a 2FA Cloud Password.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.hint != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Hint: ${state.hint}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Cloud Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (passwordInput.isNotEmpty()) repository.submit2FaPassword(passwordInput)
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("2fa_password_input")
                    )

                    Spacer(Modifier.height(22.dp))

                    Button(
                        onClick = { repository.submit2FaPassword(passwordInput) },
                        enabled = passwordInput.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_password_button")
                    ) {
                        Text("Submit Password", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                else -> {
                    // iOS Segmented Mode Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                            .padding(3.dp)
                    ) {
                        listOf("Phone Number", "QR Code").forEachIndexed { idx, label ->
                            val selected = authModeTab == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .clickable { authModeTab = idx }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    if (authModeTab == 0) {
                        Text(
                            text = "Your Phone",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Please confirm your country code and enter your phone number.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(20.dp))

                        // Grouped iOS Phone Input Card
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                // Country Selector Row (All 237+ World Countries & +888)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCountryDialog = true }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .testTag("country_picker_card"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(selectedCountry?.flag ?: "🌐", fontSize = 22.sp)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = selectedCountry?.name ?: "Choose Country (${allCountries.size})",
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Choose Country",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 16.dp)
                                )

                                // Editable Dial Code + Phone Number Row (Telegram iOS style)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.width(82.dp)
                                    ) {
                                        Text(
                                            text = "+",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        BasicTextField(
                                            value = dialCodeDigits,
                                            onValueChange = { raw ->
                                                val digits = raw.filter { it.isDigit() }.take(4)
                                                dialCodeDigits = digits
                                                selectedCountry = if (digits.isNotEmpty()) {
                                                    repository.matchCountryByDialCode(digits)
                                                } else null
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("country_dial_code_input")
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(0.5.dp)
                                            .height(22.dp)
                                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                    )

                                    Spacer(Modifier.width(14.dp))

                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (phoneNumber.isEmpty()) {
                                            Text(
                                                text = "Phone number",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 17.sp
                                            )
                                        }
                                        BasicTextField(
                                            value = phoneNumber,
                                            onValueChange = { phoneNumber = it.filter { c -> c.isDigit() || c == ' ' } },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 17.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Phone,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(onDone = {
                                                val fullPhone = "+$dialCodeDigits${phoneNumber.replace(" ", "")}"
                                                if (dialCodeDigits.isNotEmpty() && phoneNumber.isNotBlank()) {
                                                    repository.submitPhoneNumber(fullPhone)
                                                }
                                            }),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("phone_number_input")
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        Button(
                            onClick = {
                                val fullPhone = "+$dialCodeDigits${phoneNumber.replace(" ", "")}"
                                repository.submitPhoneNumber(fullPhone)
                            },
                            enabled = dialCodeDigits.isNotEmpty() && phoneNumber.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("continue_phone_button")
                        ) {
                            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.size(196.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.QrCode2,
                                    contentDescription = "QR Login Code",
                                    tint = Color.Black,
                                    modifier = Modifier.size(150.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Log in to Telegram by QR Code", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "1. Open Telegram on your phone\n2. Go to Settings > Devices > Link Desktop\n3. Scan this code",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                repository.submitPhoneNumber("+1 202 555 0199")
                                repository.submitVerificationCode("12345")
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Quick Demo Sign In", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showCountryDialog) {
        CountryPickerDialog(
            countries = allCountries,
            onCountrySelected = { country ->
                selectedCountry = country
                dialCodeDigits = country.dialCode.removePrefix("+")
                showCountryDialog = false
            },
            onDismiss = { showCountryDialog = false }
        )
    }

    if (showApiDialog) {
        ApiConfigDialog(
            repository = repository,
            onDismiss = { showApiDialog = false }
        )
    }
}

@Composable
fun CountryPickerDialog(
    countries: List<CountryItem> = WorldCountries.all,
    onCountrySelected: (CountryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(countries, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) countries
        else countries.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.dialCode.contains(q) ||
                it.dialCode.removePrefix("+").startsWith(q.removePrefix("+")) ||
                it.code.equals(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Choose Country (${filteredCountries.size})",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country or code (+7, +1, +888)…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("country_search_input")
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(filteredCountries, key = { "${it.code}_${it.dialCode}" }) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCountrySelected(country) }
                                .padding(vertical = 11.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(country.flag, fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(country.name, fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                country.dialCode,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
