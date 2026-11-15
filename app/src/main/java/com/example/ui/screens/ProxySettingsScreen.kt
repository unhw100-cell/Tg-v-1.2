package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rustgram.NativeBridge
import com.example.tdlib.TdClient
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.liquidGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class ProxyKind(val label: String, val tdlibName: String) {
    SOCKS5("SOCKS5", "proxyTypeSocks5"),
    HTTP("HTTP", "proxyTypeHttp"),
    MTPROTO("MTProto", "proxyTypeMtproto")
}

private data class ProxyRow(
    val id: Int,
    val server: String,
    val port: Int,
    val type: String,
    val enabled: Boolean,
    val proxyJson: String
)

/** Proxy management backed by TDLib's real proxy API, available even before Telegram auth. */
@Composable
fun ProxySettingsScreen(
    tdClient: TdClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var proxies by remember { mutableStateOf(emptyList<ProxyRow>()) }
    var selectedKind by remember { mutableStateOf(ProxyKind.SOCKS5) }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1080") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var importLink by remember { mutableStateOf("") }
    var enableOnAdd by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Управление прокси через TDLib") }
    var isBusy by remember { mutableStateOf(false) }

    fun send(request: JSONObject, failureMessage: String = "TDLib не подключена: запрос не отправлен"): Boolean {
        request.put("@extra", "rustgram-proxy-${System.nanoTime()}")
        if (!tdClient.send(request)) {
            status = failureMessage
            return false
        }
        isBusy = true
        return true
    }

    fun rustRequest(operation: String, config: JSONObject? = null, proxyId: Int = 0): JSONObject? {
        val raw = runCatching {
            NativeBridge.buildProxyRequest(operation, config?.toString().orEmpty(), proxyId)
        }.getOrNull()
        return raw?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    fun refresh() = send(
        rustRequest("list") ?: JSONObject().put("@type", "getProxies")
    )

    LaunchedEffect(tdClient) {
        val updatesJob = launch {
            tdClient.updates.collect { update ->
                // TDLib is asynchronous: only consume responses to this screen's own requests.
                if (!update.optString("@extra").startsWith("rustgram-proxy-")) return@collect
                when (update.optString("@type")) {
                    "addedProxies" -> {
                        val values = update.optJSONArray("proxies")
                        val parsed = ArrayList<ProxyRow>()
                        if (values != null) {
                            for (index in 0 until values.length()) {
                                val item = values.optJSONObject(index) ?: continue
                                val proxy = item.optJSONObject("proxy") ?: continue
                                parsed += ProxyRow(
                                    id = item.optInt("id", item.optInt("proxy_id", -1)),
                                    server = proxy.optString("server", ""),
                                    port = proxy.optInt("port", 0),
                                    type = proxy.optJSONObject("type")?.optString("@type", "Proxy") ?: "Proxy",
                                    enabled = item.optBoolean("is_enabled", false),
                                    proxyJson = proxy.toString()
                                )
                            }
                        }
                        proxies = parsed.filter { it.id >= 0 && it.server.isNotBlank() }
                        isBusy = false
                        status = "Найдено прокси: ${proxies.size}"
                    }
                    "addedProxy", "ok" -> {
                        isBusy = false
                        refresh()
                    }
                    "error" -> {
                        isBusy = false
                        status = "Ошибка TDLib ${update.optInt("code")}: ${update.optString("message", "неизвестная ошибка")}"
                    }
                    "seconds" -> {
                        isBusy = false
                        status = "Проверка завершена: ${update.optDouble("seconds", -1.0)} с"
                    }
                }
            }
        }
        delay(120)
        refresh()
        updatesJob.join()
    }

    fun createProxyObject(): JSONObject? {
        val validPort = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: run {
            status = "Порт должен быть числом от 1 до 65535"
            return null
        }
        if (server.isBlank() || server.any { it.isWhitespace() || it.isISOControl() }) {
            status = "Укажи корректный адрес прокси"
            return null
        }
        val proxyType = JSONObject().put("@type", selectedKind.tdlibName)
        when (selectedKind) {
            ProxyKind.SOCKS5, ProxyKind.HTTP -> {
                proxyType.put("username", username.trim())
                proxyType.put("password", password)
                if (selectedKind == ProxyKind.HTTP) proxyType.put("http_only", false)
            }
            ProxyKind.MTPROTO -> {
                if (secret.isBlank()) {
                    status = "Для MTProto требуется secret"
                    return null
                }
                proxyType.put("secret", secret.trim())
            }
        }
        return JSONObject()
            .put("@type", "proxy")
            .put("server", server.trim())
            .put("port", validPort)
            .put("type", proxyType)
    }

    fun rustConfig(): JSONObject {
        val kind = when (selectedKind) {
            ProxyKind.SOCKS5 -> JSONObject().put("kind", "socks5").put("username", username.trim()).put("password", password)
            ProxyKind.HTTP -> JSONObject().put("kind", "http").put("username", username.trim()).put("password", password).put("http_only", false)
            ProxyKind.MTPROTO -> JSONObject().put("kind", "mtproto").put("secret", secret.trim())
        }
        return JSONObject()
            .put("server", server.trim())
            .put("port", port.toIntOrNull() ?: 0)
            .put("enabled", enableOnAdd)
            .put("proxy_type", kind)
    }

    fun addProxy() {
        val proxy = createProxyObject() ?: return
        val request = rustRequest("add", rustConfig())
            ?: JSONObject().put("@type", "addProxy").put("proxy", proxy).put("enable", enableOnAdd)
        if (send(request, "Не удалось передать addProxy в TDLib")) {
            status = "Прокси отправлен в TDLib; ожидаю подтверждение"
        }
    }

    fun importProxyLink() {
        val uri = runCatching { Uri.parse(importLink.trim()) }.getOrNull()
        if (uri == null || importLink.isBlank()) {
            status = "Вставь ссылку Telegram на прокси"
            return
        }
        val isSupportedTelegramLink =
            (uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.orEmpty().lowercase() in setOf("t.me", "telegram.me") &&
                uri.path.orEmpty().trim('/').lowercase() in setOf("proxy", "socks")) ||
            (uri.scheme.equals("tg", ignoreCase = true) && uri.host.orEmpty().lowercase() in setOf("proxy", "socks"))
        if (!isSupportedTelegramLink) {
            status = "Поддерживаются ссылки t.me/proxy, t.me/socks, tg://proxy и tg://socks"
            return
        }
        val importedServer = uri.getQueryParameter("server")
        val importedPort = uri.getQueryParameter("port")
        val importedSecret = uri.getQueryParameter("secret")
        val importedPortNumber = importedPort?.toIntOrNull()
        if (importedServer.isNullOrBlank() || importedPortNumber == null || importedPortNumber !in 1..65535) {
            status = "Ссылка должна содержать server и port"
            return
        }
        server = importedServer
        port = importedPortNumber.toString()
        if (!importedSecret.isNullOrBlank()) {
            selectedKind = ProxyKind.MTPROTO
            secret = importedSecret
        } else {
            selectedKind = ProxyKind.SOCKS5
            username = uri.getQueryParameter("user") ?: ""
            password = uri.getQueryParameter("pass") ?: ""
        }
        importLink = ""
        status = "Параметры ссылки загружены. Проверь адрес и нажми «Добавить прокси»"
    }

    LiquidGlassBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .liquidGlass(shape = RoundedCornerShape(50), strong = true)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    "Прокси-серверы",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { refresh() }, enabled = !isBusy) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить список")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Состояние", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            if (isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        if (!tdClient.isNativeTdLibLinked) {
                            Text(
                                "Нативная TDLib не загружена. Настройки можно заполнить, но подключить прокси до сборки корректной native-библиотеки нельзя.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Text("Добавить вручную", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProxyKind.entries.forEach { kind ->
                                FilterChip(
                                    selected = selectedKind == kind,
                                    onClick = { selectedKind = kind },
                                    label = { Text(kind.label) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Сервер или IP") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Порт") },
                            singleLine = true
                        )
                        if (selectedKind == ProxyKind.MTPROTO) {
                            OutlinedTextField(
                                value = secret,
                                onValueChange = { secret = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Secret") },
                                singleLine = true
                            )
                        } else {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Имя пользователя (необязательно)") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Пароль (необязательно)") },
                                singleLine = true
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Включить сразу", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = enableOnAdd, onCheckedChange = { enableOnAdd = it })
                        }
                        Button(onClick = { addProxy() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Добавить прокси")
                        }
                    }
                }

                Text("Импорт ссылки Telegram", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = importLink,
                            onValueChange = { importLink = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("t.me/proxy или tg://proxy") },
                            singleLine = true
                        )
                        OutlinedButton(onClick = { importProxyLink() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Разобрать ссылку")
                        }
                        Text(
                            "Ссылка только заполняет поля: перед подключением проверь источник прокси и его владельца.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text("Сохранённые прокси", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (proxies.isEmpty()) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Пока нет сохранённых прокси. Обнови список или добавь сервер выше.",
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    proxies.forEach { proxy ->
                        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${proxy.server}:${proxy.port}", fontWeight = FontWeight.SemiBold)
                                        Text(proxy.type.removePrefix("proxyType"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (proxy.enabled) {
                                        Text(
                                            "Активен",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Использовать", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = proxy.enabled,
                                        onCheckedChange = { enabled ->
                                            send(
                                                if (enabled) rustRequest("enable", proxyId = proxy.id)
                                                    ?: JSONObject().put("@type", "enableProxy").put("proxy_id", proxy.id)
                                                else rustRequest("disable") ?: JSONObject().put("@type", "disableProxy")
                                            )
                                        }
                                    )
                                    IconButton(onClick = {
                                        val proxyObject = runCatching { JSONObject(proxy.proxyJson) }.getOrNull()
                                        if (proxyObject == null) {
                                            status = "Не удалось прочитать параметры прокси"
                                        } else {
                                            // TDLib pingProxy accepts the complete proxy object, not proxy_id.
                                            send(JSONObject().put("@type", "pingProxy").put("proxy", proxyObject))
                                        }
                                    }) {
                                        Icon(Icons.Default.Speed, contentDescription = "Проверить задержку")
                                    }
                                    IconButton(onClick = {
                                        send(rustRequest("remove", proxyId = proxy.id)
                                            ?: JSONObject().put("@type", "removeProxy").put("proxy_id", proxy.id))
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
