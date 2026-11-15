package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.repository.TelegramRepository
import com.example.tdlib.AppLanguage
import com.example.tdlib.CountryItem
import com.example.tdlib.TdAuthState
import com.example.tdlib.WorldCountries
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCapsuleButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.liquidGlass
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.TelegramIosRed
import com.example.ui.theme.TelegramOnlineGreen

val sampleCountries: List<CountryItem> = WorldCountries.all

@Composable
fun AuthScreen(
    repository: TelegramRepository,
    onAuthSuccess: () -> Unit
) {
    val strings = LocalAppStrings.current
    val language by repository.language.collectAsState()
    val authState by repository.authState.collectAsState()
    val authError by repository.authError.collectAsState()
    val isAuthBusy by repository.isAuthBusy.collectAsState()
    val tdLogEntries by repository.tdLogEntries.collectAsState()
    val connectionStatus by repository.connectionStatus.collectAsState()
    val allCountries = remember { repository.getWorldCountries() }

    var selectedCountry by remember {
        mutableStateOf<CountryItem?>(
            allCountries.find { it.code == if (language == AppLanguage.RU) "RU" else "US" } ?: allCountries.first()
        )
    }
    var dialCodeDigits by remember { mutableStateOf(selectedCountry?.dialCode?.removePrefix("+") ?: "7") }
    var phoneNumber by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showApiDialog by remember { mutableStateOf(false) }
    var showTdLog by remember { mutableStateOf(false) }
    var authModeTab by remember { mutableIntStateOf(0) } // 0: Phone, 1: QR

    LaunchedEffect(authState) {
        if (authState is TdAuthState.Ready) {
            onAuthSuccess()
        }
    }

    LiquidGlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Floating Liquid Glass Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(50), strong = true)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // RU / EN Language Switcher Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        .padding(3.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val active = language == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                                .clickable { repository.setLanguage(lang) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("auth_lang_${lang.code}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lang.code.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Text(
                    text = strings.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showTdLog = !showTdLog },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("auth_tdlog_toggle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = strings.authTdLogTitle,
                            tint = if (showTdLog) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showApiDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("auth_api_config_btn")
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = strings.apiCredentialsTitle,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Hero Glass Icon
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .liquidGlass(shape = CircleShape, strong = true, accentTint = MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.rustgram_icon),
                    contentDescription = "RustGram Logo",
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Error Banner (from real TDLib server responses)
            AnimatedVisibility(visible = !authError.isNullOrBlank()) {
                LiquidGlassCard(
                    accentTint = TelegramIosRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = TelegramIosRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = authError ?: "",
                            color = TelegramIosRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { repository.clearAuthError() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = strings.close, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Expandable Live TDLib Status Log Drawer (Honor 600 Lite Diagnostics)
            AnimatedVisibility(visible = showTdLog) {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.authTdLogTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = connectionStatus,
                                fontSize = 11.sp,
                                color = TelegramOnlineGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        if (tdLogEntries.isEmpty()) {
                            Text(
                                text = strings.authWaitingTdlib,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            tdLogEntries.takeLast(8).forEach { entry ->
                                Text(
                                    text = "${entry.timestamp} [${entry.tag}] ${entry.message}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (entry.isError) TelegramIosRed else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            when (val state = authState) {
                is TdAuthState.WaitCode -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TextButton(onClick = { repository.backToPhoneStep() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                            Spacer(Modifier.width(4.dp))
                            Text(strings.back)
                        }
                    }

                    Text(
                        text = state.phoneNumber,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.authCodeSent,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))

                    LiquidGlassCard(
                        strong = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(6)
                                    codeInput = digits
                                    if (digits.length == state.codeLength && !isAuthBusy) {
                                        repository.submitVerificationCode(digits)
                                    }
                                },
                                placeholder = { Text("12345") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (codeInput.isNotEmpty() && !isAuthBusy) {
                                        repository.submitVerificationCode(codeInput)
                                    }
                                }),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 24.sp,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 8.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .testTag("verification_code_input")
                            )

                            Spacer(Modifier.height(18.dp))

                            Button(
                                onClick = { repository.submitVerificationCode(codeInput) },
                                enabled = codeInput.isNotEmpty() && !isAuthBusy,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("submit_code_button")
                            ) {
                                if (isAuthBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(strings.authContinue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { repository.submitPhoneNumber(state.phoneNumber) },
                        enabled = !isAuthBusy
                    ) {
                        Text(strings.authResendCode)
                    }
                }

                is TdAuthState.WaitPassword -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TextButton(onClick = { repository.backToPhoneStep() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                            Spacer(Modifier.width(4.dp))
                            Text(strings.back)
                        }
                    }

                    Text(
                        text = strings.authTwoStepTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.authTwoStepSubtitle,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.hint != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${state.hint}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(18.dp))

                    LiquidGlassCard(strong = true, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text(strings.authCloudPassword) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (passwordInput.isNotEmpty() && !isAuthBusy) {
                                        repository.submit2FaPassword(passwordInput)
                                    }
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("2fa_password_input")
                            )

                            Spacer(Modifier.height(18.dp))

                            Button(
                                onClick = { repository.submit2FaPassword(passwordInput) },
                                enabled = passwordInput.isNotEmpty() && !isAuthBusy,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("submit_password_button")
                            ) {
                                if (isAuthBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(strings.authSubmitPassword, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Liquid Glass Segmented Mode Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(shape = RoundedCornerShape(50), strong = false)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(strings.authPhoneTab, strings.authQrTab).forEachIndexed { idx, label ->
                            val selected = authModeTab == idx
                            LiquidGlassCapsuleButton(
                                onClick = {
                                    authModeTab = idx
                                    if (idx == 1) repository.requestQrCodeAuth()
                                },
                                selected = selected,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    if (authModeTab == 0) {
                        Text(
                            text = strings.authYourPhone,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.authPhoneSubtitle,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(18.dp))

                        LiquidGlassCard(strong = true, modifier = Modifier.fillMaxWidth()) {
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
                                            text = selectedCountry?.name ?: "${strings.authChooseCountry} (${allCountries.size})",
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = strings.authChooseCountry,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 16.dp)
                                )

                                // Editable Dial Code + Phone Number Row
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
                                            .width(0.6.dp)
                                            .height(22.dp)
                                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                    )

                                    Spacer(Modifier.width(14.dp))

                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (phoneNumber.isEmpty()) {
                                            Text(
                                                text = strings.authPhonePlaceholder,
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
                                                if (dialCodeDigits.isNotEmpty() && phoneNumber.isNotBlank() && !isAuthBusy) {
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

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = {
                                val fullPhone = "+$dialCodeDigits${phoneNumber.replace(" ", "")}"
                                repository.submitPhoneNumber(fullPhone)
                            },
                            enabled = dialCodeDigits.isNotEmpty() && phoneNumber.isNotBlank() && !isAuthBusy,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("continue_phone_button")
                        ) {
                            if (isAuthBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(strings.authWaitingTdlib, fontSize = 14.sp)
                            } else {
                                Text(strings.authContinue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        LiquidGlassCard(strong = true, modifier = Modifier.size(206.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.92f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.QrCode2,
                                    contentDescription = strings.authQrTitle,
                                    tint = Color.Black,
                                    modifier = Modifier.size(156.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(strings.authQrTitle, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.authQrSteps,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Explicit Quick Demo Sign In Capsule (Works cleanly offline or before API keys)
                    LiquidGlassCapsuleButton(
                        onClick = { repository.enterDemoMode() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("demo_sign_in_btn")
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.authDemoSignIn,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
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
    val strings = LocalAppStrings.current
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
        modifier = Modifier.liquidGlass(shape = RoundedCornerShape(28.dp), strong = true),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${strings.authChooseCountry} (${filteredCountries.size})",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.authSearchCountry) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
                Text(strings.close)
            }
        }
    )
}
