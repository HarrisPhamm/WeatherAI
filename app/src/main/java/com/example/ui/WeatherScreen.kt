package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FavoriteCity
import com.example.data.GeocodeResult
import com.example.viewmodel.WeatherUiState
import com.example.viewmodel.WeatherViewModel
import com.example.viewmodel.WeatherAlert
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// Core color palette matching Elegant Dark theme exactly
val ElegantDarkBg = Color(0xFF1C1B1F)
val ElegantDarkSurface = Color(0xFF2B2930)
val ElegantDarkBorder = Color(0xFF49454F)
val ElegantDarkText = Color(0xFFE6E1E5)
val ElegantDarkMutedText = Color(0xFFCAC4D0)
val ElegantDarkSecondaryText = Color(0xFF938F99)
val ElegantDarkLavender = Color(0xFFD0BCFF)
val ElegantDarkPillBg = Color(0xFFE8DEF8)
val ElegantDarkPillText = Color(0xFF1D192B)

data class WeatherInfo(
    val description: String,
    val icon: ImageVector,
    val glowColor: Color
)

fun getWeatherInfo(code: Int): WeatherInfo {
    return when (code) {
        0 -> WeatherInfo("Trời quang mây tạnh", Icons.Filled.WbSunny, Color(0xFFFBBF24))
        1, 2, 3 -> WeatherInfo(
            description = when (code) {
                1 -> "Ít mây, trời nắng"
                2 -> "Mây rải rác"
                else -> "Trời nhiều mây"
            },
            icon = Icons.Filled.WbCloudy,
            glowColor = Color(0xFF94A3B8)
        )
        45, 48 -> WeatherInfo("Có sương mù", Icons.Filled.Cloud, Color(0xFF64748B))
        51, 53, 55 -> WeatherInfo("Mưa phùn nhẹ", Icons.Filled.WaterDrop, Color(0xFF60A5FA))
        61, 63, 65, 80, 81, 82 -> WeatherInfo(
            description = if (code in 80..82) "Mưa rào nặng hạt" else "Mưa rơi vừa",
            icon = Icons.Filled.Umbrella,
            glowColor = Color(0xFF3B82F6)
        )
        71, 73, 75, 77, 85, 86 -> WeatherInfo("Có tuyết rơi", Icons.Filled.AcUnit, Color(0xFF22D3EE))
        95, 96, 99 -> WeatherInfo("Giông bão sấm sét", Icons.Filled.Thunderstorm, Color(0xFFA855F7))
        else -> WeatherInfo("Thời tiết ôn hòa", Icons.Filled.WbSunny, Color(0xFFFBBF24))
    }
}

fun formatDateFallback(dateStr: String): String {
    val parts = dateStr.split("-")
    if (parts.size == 3) {
        return "${parts[2]}/${parts[1]}"
    }
    return dateStr
}

fun formatDayName(dateStr: String): String {
    return try {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val date = format.parse(dateStr)
        if (date != null) {
            val todayCal = java.util.Calendar.getInstance()
            val targetCal = java.util.Calendar.getInstance()
            targetCal.time = date
            
            // Check if it is today
            val isToday = todayCal.get(java.util.Calendar.YEAR) == targetCal.get(java.util.Calendar.YEAR) &&
                    todayCal.get(java.util.Calendar.DAY_OF_YEAR) == targetCal.get(java.util.Calendar.DAY_OF_YEAR)
            if (isToday) return "Hôm nay"
            
            // Check if it is tomorrow
            todayCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val isTomorrow = todayCal.get(java.util.Calendar.YEAR) == targetCal.get(java.util.Calendar.YEAR) &&
                    todayCal.get(java.util.Calendar.DAY_OF_YEAR) == targetCal.get(java.util.Calendar.DAY_OF_YEAR)
            if (isTomorrow) return "Ngày mai"
            
            when (targetCal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> "Thứ Hai"
                java.util.Calendar.TUESDAY -> "Thứ Ba"
                java.util.Calendar.WEDNESDAY -> "Thứ Tư"
                java.util.Calendar.THURSDAY -> "Thứ Năm"
                java.util.Calendar.FRIDAY -> "Thứ Sáu"
                java.util.Calendar.SATURDAY -> "Thứ Bảy"
                java.util.Calendar.SUNDAY -> "Chủ Nhật"
                else -> "Ngày khác"
            }
        } else {
            "Sắp tới"
        }
    } catch (e: Exception) {
        dateStr
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val geminiText by viewModel.geminiState.collectAsState()
    val isGeminiLoading by viewModel.isGeminiLoading.collectAsState()
    val currentCity by viewModel.currentCity.collectAsState()
    val weatherAlerts by viewModel.weatherAlerts.collectAsState()
    val userApiKey by viewModel.userApiKey.collectAsState()

    var hourlyRange by remember { mutableStateOf(24) } // hourly range (24 or 48 hours)
    var selectedTab by remember { mutableStateOf(0) }

    val hourlyLazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val weather = (uiState as? WeatherUiState.Success)?.weather
    val hourly = weather?.hourly
    val currentWeatherTime = weather?.currentWeather?.time
    
    val currentHourIndex = remember(hourly, currentWeatherTime) {
        if (hourly != null) {
            try {
                val currentCal = java.util.Calendar.getInstance()
                if (!currentWeatherTime.isNullOrEmpty()) {
                    try {
                        val format = if (currentWeatherTime.count { it == ':' } == 2) {
                            "yyyy-MM-dd'T'HH:mm:ss"
                        } else {
                            "yyyy-MM-dd'T'HH:mm"
                        }
                        val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                        val date = sdf.parse(currentWeatherTime)
                        if (date != null) {
                            currentCal.time = date
                        }
                    } catch (e: Exception) {
                        // ignore/fallback to system
                    }
                }
                var matchIndex = -1
                val itemSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US)
                
                // Fine-grained hour-matching: same year, day of year, and hour of day
                for (i in 0 until hourly.time.size) {
                    val tStr = hourly.time[i]
                    try {
                        val date = itemSdf.parse(tStr)
                        if (date != null) {
                            val itemCal = java.util.Calendar.getInstance()
                            itemCal.time = date
                            if (itemCal.get(java.util.Calendar.YEAR) == currentCal.get(java.util.Calendar.YEAR) &&
                                itemCal.get(java.util.Calendar.DAY_OF_YEAR) == currentCal.get(java.util.Calendar.DAY_OF_YEAR) &&
                                itemCal.get(java.util.Calendar.HOUR_OF_DAY) == currentCal.get(java.util.Calendar.HOUR_OF_DAY)) {
                                matchIndex = i
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                // Fallback: search closest point using absolute offset
                if (matchIndex == -1) {
                    var minDiffMs = Long.MAX_VALUE
                    val currentMs = currentCal.timeInMillis
                    for (i in 0 until hourly.time.size) {
                        val tStr = hourly.time[i]
                        try {
                            val date = itemSdf.parse(tStr)
                            if (date != null) {
                                val diff = Math.abs(date.time - currentMs)
                                if (diff < minDiffMs) {
                                    minDiffMs = diff
                                    matchIndex = i
                                }
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                matchIndex
            } catch (e: Exception) {
                -1
            }
        } else {
            -1
        }
    }

    val context = LocalContext.current

    LaunchedEffect(hourly, currentHourIndex, selectedTab) {
        if (selectedTab == 0 && hourly != null && currentHourIndex >= 0) {
            try {
                // Yield thread to allow full layout pass first
                kotlinx.coroutines.delay(120)
                val itemWidthPx = with(density) { 96.dp.roundToPx() }
                val screenWidthPx = context.resources.displayMetrics.widthPixels
                val offsetPx = (screenWidthPx - itemWidthPx) / 2
                
                // One single crisp scroll to center
                hourlyLazyListState.scrollToItem(currentHourIndex, -offsetPx)
            } catch (e: Exception) {
                // Ignore gracefully
            }
        }
    }
    val permissionState = rememberPermissionState(
        permission = android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val requestLocationDetails: () -> Unit = {
        if (permissionState.status.isGranted) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: android.location.Location? ->
                    if (location != null) {
                        viewModel.selectLocation(
                            location.latitude,
                            location.longitude,
                            "Vị trí định vị"
                        )
                    } else {
                        // fallback to getCurrentLocation but with a proper token source
                        try {
                            val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                            fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                tokenSource.token
                            ).addOnSuccessListener { currLocation ->
                                if (currLocation != null) {
                                    viewModel.selectLocation(
                                        currLocation.latitude,
                                        currLocation.longitude,
                                        "Vị trí định vị"
                                    )
                                }
                            }.addOnFailureListener {
                                // Fail gracefully
                            }
                        } catch (ex: Exception) {
                            // ignore any getCurrentLocation exceptions
                        }
                    }
                }.addOnFailureListener {
                    // Fail gracefully on any GMS API issues
                }
            } catch (e: Exception) {
                // ignore any location provider exceptions
            }
        } else {
            permissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: android.location.Location? ->
                    if (location != null) {
                        viewModel.selectLocation(
                            location.latitude,
                            location.longitude,
                            "Vị trí định vị"
                        )
                    } else {
                        try {
                            val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                            fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                tokenSource.token
                            ).addOnSuccessListener { currLocation ->
                                if (currLocation != null) {
                                    viewModel.selectLocation(
                                        currLocation.latitude,
                                        currLocation.longitude,
                                        "Vị trí định vị"
                                    )
                                }
                            }.addOnFailureListener {
                                // Fail gracefully
                            }
                        } catch (ex: Exception) {
                            // ignore
                        }
                    }
                }.addOnFailureListener {
                    // Fail gracefully
                }
            } catch (e: Exception) {
                // ignore any location provider exceptions
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchRow by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(selectedTab) {
        if (selectedTab == 3) {
            showSearchRow = false
            searchQuery = ""
            viewModel.clearSearch()
            focusManager.clearFocus()
        }
    }

    val tempUnit by viewModel.tempUnitSetting.collectAsState()
    val themeSetting by viewModel.themeSetting.collectAsState()
    val weatherSource by viewModel.weatherSourceSetting.collectAsState()
    val accuApiKey by viewModel.accuApiKey.collectAsState()
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeSetting) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }

    // Local theme mapping to respond to light/dark settings perfectly
    val themeBg = MaterialTheme.colorScheme.background
    val themeSurface = MaterialTheme.colorScheme.surface
    val themeBorder = if (isDark) Color(0xFF49454F) else Color(0xFFCAC4D0)
    val themeText = MaterialTheme.colorScheme.onBackground
    val themeMutedText = MaterialTheme.colorScheme.onSurfaceVariant
    val themeSecondaryText = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val themeLavender = MaterialTheme.colorScheme.primary
    val themePillBg = MaterialTheme.colorScheme.primaryContainer
    val themePillText = MaterialTheme.colorScheme.onPrimaryContainer

    val formatTemp: (Double?) -> String = { tempCelsius ->
        if (tempCelsius == null) "--"
        else if (tempUnit == "F") {
            "${((tempCelsius * 9.0 / 5.0) + 32.0).toInt()}°F"
        } else {
            "${tempCelsius.toInt()}°C"
        }
    }

    val todayFormatted = remember {
        try {
            val cal = java.util.Calendar.getInstance()
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val month = cal.get(java.util.Calendar.MONTH) + 1
            "Hôm nay, $day Tháng $month"
        } catch (e: Exception) {
            "Hôm nay"
        }
    }

    Scaffold(
        bottomBar = {
            Surface(
                color = themeBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WeatherTabItem(
                        icon = Icons.Filled.Home,
                        label = "Chính",
                        description = "Trang chính",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        themeText = themeText,
                        themePillBg = themePillBg,
                        themePillText = themePillText,
                        modifier = Modifier.weight(1f)
                    )

                    WeatherTabItem(
                        icon = Icons.Filled.CalendarMonth,
                        label = "Dự báo",
                        description = "Dự báo",
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        themeText = themeText,
                        themePillBg = themePillBg,
                        themePillText = themePillText,
                        modifier = Modifier.weight(1f)
                    )

                    WeatherTabItem(
                        icon = Icons.Filled.Map,
                        label = "Bản đồ",
                        description = "Bản đồ",
                        isSelected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        themeText = themeText,
                        themePillBg = themePillBg,
                        themePillText = themePillText,
                        modifier = Modifier.weight(1f)
                    )

                    WeatherTabItem(
                        icon = Icons.Filled.Settings,
                        label = "Cài đặt",
                        description = "Cài đặt",
                        isSelected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        themeText = themeText,
                        themePillBg = themePillBg,
                        themePillText = themePillText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        val themeGlowColor = if (isDark) Color(0xFF312E81) else Color(0xFFE0E7FF)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(themeGlowColor, themeBg),
                        radius = 2000f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                // Header (City, Location tag, and Search actions) shown on all tabs except Settings
                if (selectedTab != 3) {
                    HeaderSection(
                        currentCityName = currentCity?.name ?: "Hà Nội",
                        todayDate = todayFormatted,
                        weatherSourceName = viewModel.getFriendlySourceName(),
                        onSearchToggle = { showSearchRow = !showSearchRow },
                        searchActive = showSearchRow,
                        onLocateClick = requestLocationDetails,
                        isPermissionGranted = permissionState.status.isGranted
                    )
                } else {
                    // Custom Header for Settings
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Cài đặt",
                            tint = themeLavender,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Thiết lập ứng dụng",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeText
                        )
                    }
                }

                // Search expanded block
                AnimatedVisibility(
                    visible = showSearchRow,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchCity(it)
                            },
                            placeholder = { Text("Tìm tên thành phố...", color = themeMutedText) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Tìm", tint = themeLavender) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        viewModel.clearSearch()
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Xoá", tint = themeText)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = themeText,
                                unfocusedTextColor = themeText,
                                focusedContainerColor = themeSurface,
                                unfocusedContainerColor = themeSurface.copy(alpha = 0.5f),
                                focusedBorderColor = themeLavender,
                                unfocusedBorderColor = themeBorder
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_input_field")
                        )

                        // Floating results box
                        if (searchResults.isNotEmpty()) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = themeSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp)
                                    .align(Alignment.TopCenter)
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                ) {
                                    items(searchResults) { result ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val city = FavoriteCity(
                                                        name = result.name,
                                                        latitude = result.latitude,
                                                        longitude = result.longitude,
                                                        country = result.country,
                                                        adminArea = result.admin1
                                                    )
                                                    viewModel.selectCity(city)
                                                    searchQuery = ""
                                                    showSearchRow = false
                                                    viewModel.clearSearch()
                                                    focusManager.clearFocus()
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Place, contentDescription = "Địa chỉ", tint = themeLavender, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(result.name, fontWeight = FontWeight.SemiBold, color = themeText)
                                                Text(
                                                    "${result.admin1 ?: ""} • ${result.country ?: ""}",
                                                    fontSize = 12.sp,
                                                    color = themeSecondaryText
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = themeBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Favorite Cities chips row
                if (selectedTab != 3 && favorites.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        items(favorites) { favorite ->
                            val isSelected = currentCity?.name == favorite.name
                            Surface(
                                onClick = { viewModel.selectCity(favorite) },
                                color = if (isSelected) themeSurface else themeSurface.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) themeLavender else Color.Transparent
                                ),
                                modifier = Modifier.testTag("city_chip_${favorite.name}")
                            ) {
                                Text(
                                    text = favorite.name,
                                    color = if (isSelected) themeLavender else themeMutedText,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Core Scrollable area
                if (selectedTab == 3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SettingsTabContent(
                            viewModel = viewModel,
                            tempUnit = tempUnit,
                            themeSetting = themeSetting,
                            weatherSource = weatherSource,
                            accuApiKey = accuApiKey,
                            userApiKey = userApiKey,
                            themeSurface = themeSurface,
                            themeBorder = themeBorder,
                            themeText = themeText,
                            themeSecondaryText = themeSecondaryText,
                            themeLavender = themeLavender,
                            themePillText = themePillText,
                            isDark = isDark
                        )
                    }
                } else {
                    when (uiState) {
                    is WeatherUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = themeLavender)
                        }
                    }
                    is WeatherUiState.Success -> {
                        val data = (uiState as WeatherUiState.Success).weather
                        val city = (uiState as WeatherUiState.Success).city
                        val current = data.currentWeather
                        val daily = data.daily
                        val hourly = data.hourly

                        if (selectedTab == 0 || selectedTab == 1) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (selectedTab == 0) {
                                // Home Tab: Main details, Alerts, Hourly, Gemini AI
                                if (current != null) {
                                    val weatherInfo = getWeatherInfo(current.weathercode)

                                    if (weatherAlerts.isNotEmpty()) {
                                        item {
                                            Card(
                                                shape = RoundedCornerShape(20.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isDark) Color(0xFF6B1A1A) else Color(0xFFFFEBEE)
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFFF87171) else Color(0xFFEF5350)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp)
                                                    .testTag("weather_alerts_card")
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        text = "CẢNH BÁO THỜI TIẾT NGUY HIỂM ⚠️",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFC62828),
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                    )
                                                    weatherAlerts.forEachIndexed { index, alert ->
                                                        Row(
                                                            verticalAlignment = Alignment.Top,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (alert.isSevere) Icons.Filled.Warning else Icons.Filled.Info,
                                                                contentDescription = "Alert notification",
                                                                tint = if (alert.isSevere) Color(0xFFEF4444) else Color(0xFFFBBF24),
                                                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = "${alert.type} (${alert.severity})",
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 13.5.sp,
                                                                    color = if (isDark) Color.White else Color(0xFF1A1A1A)
                                                                )
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                Text(
                                                                    text = alert.message,
                                                                    fontSize = 12.sp,
                                                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFD32F2F),
                                                                    lineHeight = 18.sp
                                                                )
                                                            }
                                                        }
                                                        if (index < weatherAlerts.lastIndex) {
                                                            HorizontalDivider(
                                                                color = Color(0xFFEF4444).copy(alpha = 0.5f),
                                                                modifier = Modifier.padding(vertical = 8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Temperature and Info display
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.size(110.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(70.dp)
                                                        .background(weatherInfo.glowColor.copy(alpha = 0.15f), CircleShape)
                                                )
                                                Icon(
                                                    imageVector = weatherInfo.icon,
                                                    contentDescription = weatherInfo.description,
                                                    tint = themeLavender,
                                                    modifier = Modifier.size(80.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Text(
                                                text = formatTemp(current.temperature),
                                                fontSize = 72.sp,
                                                fontWeight = FontWeight.Light,
                                                color = themeText,
                                                lineHeight = 80.sp
                                            )

                                            Text(
                                                text = weatherInfo.description,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = themeMutedText,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )

                                            val feelsLikeVal = current.feelsLike ?: current.temperature
                                            
                                            Text(
                                                text = "Cảm giác như ${formatTemp(feelsLikeVal)}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = themeLavender,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )

                                            val maxTemp = daily?.temperature2mMax?.firstOrNull()
                                            val minTemp = daily?.temperature2mMin?.firstOrNull()

                                            Row(
                                                modifier = Modifier.padding(top = 6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("Cao: ${formatTemp(maxTemp)}", color = themeText, fontSize = 14.sp)
                                                Text("|", color = themeSecondaryText, fontSize = 14.sp)
                                                Text("Thấp: ${formatTemp(minTemp)}", color = themeSecondaryText, fontSize = 14.sp)
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            val isFav = favorites.any { it.name == city.name && kotlin.math.abs(it.latitude - city.latitude) < 0.05 }
                                            IconButton(
                                                onClick = { viewModel.toggleFavorite(city) },
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(themeSurface)
                                                    .testTag("favorite_toggle_button")
                                            ) {
                                                Icon(
                                                    imageVector = if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                    contentDescription = "Yêu thích",
                                                    tint = if (isFav) Color(0xFFFBBF24) else themeSecondaryText,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Metric Cards
                                    item {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                val humVal = hourly?.relativeHumidity2m?.firstOrNull() ?: 60
                                                ElegantCardMetric(
                                                    icon = Icons.Filled.WaterDrop,
                                                    label = "Độ ẩm",
                                                    value = "$humVal%",
                                                    desc = "Điểm sương ${formatTemp(current.temperature * 0.85)}",
                                                    modifier = Modifier.weight(1f)
                                                )

                                                ElegantCardMetric(
                                                    icon = Icons.Filled.Air,
                                                    label = "Gió",
                                                    value = "${current.windspeed} km/h",
                                                    desc = "Hướng gió ${current.winddirection.toInt()}°",
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                val uvVal = daily?.uvIndexMax?.firstOrNull() ?: 1.0
                                                val uvDesc = when {
                                                    uvVal < 3 -> "Mức thấp"
                                                    uvVal < 6 -> "Mức trung bình"
                                                    uvVal < 8 -> "Mức cao"
                                                    else -> "Mức rất cao"
                                                }
                                                ElegantCardMetric(
                                                    icon = Icons.Filled.WbSunny,
                                                    label = "Chỉ số UV",
                                                    value = uvVal.toString(),
                                                    desc = uvDesc,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                val rainProb = daily?.precipitationProbabilityMax?.firstOrNull() ?: 0
                                                ElegantCardMetric(
                                                    icon = Icons.Filled.Thunderstorm,
                                                    label = "Khả năng mưa",
                                                    value = "$rainProb%",
                                                    desc = if (rainProb > 40) "Cần mang theo ô" else "Bầu trời quang",
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                    if (hourly != null) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 4.dp, bottom = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "DỰ BÁO THEO GIỜ",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = themeLavender
                                                )
                                                
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .background(themeSurface, RoundedCornerShape(12.dp))
                                                        .padding(2.dp)
                                                ) {
                                                    listOf(24, 48).forEach { hours ->
                                                        val isSelected = hourlyRange == hours
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(if (isSelected) themeLavender else Color.Transparent)
                                                                .clickable { hourlyRange = hours }
                                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                        ) {
                                                            Text(
                                                                text = "${hours}h",
                                                                color = if (isSelected) themePillText else themeMutedText,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
 
                                            LazyRow(
                                                state = hourlyLazyListState,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                contentPadding = PaddingValues(vertical = 4.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("hourly_forecast_row")
                                            ) {
                                                items((0 until minOf(hourly.time.size, hourlyRange)).toList()) { index ->
                                                    val rawTime = hourly.time.getOrNull(index) ?: ""
                                                    val timeLabel = rawTime.substringAfter("T")
                                                    val datePart = rawTime.substringBefore("T")
                                                    val isNextDay = hourly.time.firstOrNull()?.substringBefore("T") != datePart && datePart.isNotEmpty()
                                                    val daySfx = if (isNextDay) " (+1)" else ""
 
                                                    val temp = hourly.temperature2m.getOrNull(index)
                                                    val code = hourly.weatherCode?.getOrNull(index) ?: 0
                                                    val info = getWeatherInfo(code)
 
                                                    val isPast = currentHourIndex >= 0 && index < currentHourIndex
                                                    val isCurrent = currentHourIndex >= 0 && index == currentHourIndex
 
                                                    val cardAlpha = if (isPast) 0.4f else 1.0f
                                                    val cardBorderColor = if (isCurrent) themeLavender else (if (isDark) themeBorder.copy(alpha = 0.4f) else themeBorder.copy(alpha = 0.2f))
                                                    val cardBorderWidth = if (isCurrent) 2.dp else 1.dp
 
                                                    Card(
                                                        shape = RoundedCornerShape(20.dp),
                                                        colors = CardDefaults.cardColors(containerColor = themeSurface),
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            width = cardBorderWidth,
                                                            color = cardBorderColor
                                                        ),
                                                        modifier = Modifier
                                                            .width(96.dp)
                                                            .alpha(cardAlpha)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 12.dp, horizontal = 6.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Text(
                                                                text = if (isCurrent) "Bây giờ" else "$timeLabel$daySfx",
                                                                fontSize = 11.sp,
                                                                color = if (isCurrent) themeLavender else themeMutedText,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Icon(
                                                                info.icon, 
                                                                contentDescription = null, 
                                                                tint = if (isCurrent) themeLavender else themeLavender.copy(alpha = 0.8f), 
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(formatTemp(temp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = themeText)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Gemini AI Card
                                    item {
                                        Card(
                                            shape = RoundedCornerShape(24.dp),
                                            colors = CardDefaults.cardColors(containerColor = themeSurface),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("gemini_recommendation_card")
                                        ) {
                                            Column(modifier = Modifier.padding(18.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AutoAwesome,
                                                        contentDescription = "Trợ lý AI",
                                                        tint = themeLavender,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Cố Vấn Thời Trang & Sức Khỏe AI 💫",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = themeText
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))

                                                val activeKeyIsMissing = viewModel.getActiveApiKey().isEmpty()

                                                if (activeKeyIsMissing) {
                                                    Text(
                                                        text = "⚠️ Trợ lý AI chưa được cấu hình API Key.\nNhập API Key Gemini để dùng Cố vấn Thời tiết nhận phân tích quần áo thời trang, sức khỏe và dạo chơi từ AI!",
                                                        fontSize = 13.sp,
                                                        color = themeSecondaryText,
                                                        lineHeight = 20.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))

                                                    var keyInput by remember { mutableStateOf("") }
                                                    OutlinedTextField(
                                                        value = keyInput,
                                                        onValueChange = { keyInput = it },
                                                        placeholder = { Text("Nhập API Key ở đây...", fontSize = 13.sp, color = themeMutedText) },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = themeLavender,
                                                            unfocusedBorderColor = themeBorder,
                                                            focusedTextColor = themeText,
                                                            unfocusedTextColor = themeText
                                                        )
                                                    )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                if (keyInput.trim().isNotEmpty()) {
                                                                    viewModel.setUserApiKey(keyInput.trim())
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = themeLavender),
                                                            shape = RoundedCornerShape(12.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Lưu API Key", color = themePillText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        val context = androidx.compose.ui.platform.LocalContext.current
                                                        OutlinedButton(
                                                            onClick = {
                                                                val intent = android.content.Intent(
                                                                    android.content.Intent.ACTION_VIEW,
                                                                    android.net.Uri.parse("https://aistudio.google.com/")
                                                                )
                                                                context.startActivity(intent)
                                                            },
                                                            shape = RoundedCornerShape(12.dp),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder)
                                                        ) {
                                                            Text("Lấy Key 🔑", color = themeText, fontSize = 12.sp)
                                                        }
                                                    }
                                                } else {
                                                    if (isGeminiLoading) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            CircularProgressIndicator(
                                                                color = themeLavender,
                                                                strokeWidth = 2.dp,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Text(
                                                                "Gemini AI đang nhận định thời tiết...",
                                                                fontSize = 13.sp,
                                                                color = themeMutedText
                                                            )
                                                        }
                                                    } else {
                                                        Text(
                                                            text = geminiText ?: "Đang phân tích dữ liệu thời tiết của vùng qua trí tuệ nhân tạo Gemini AI...",
                                                            fontSize = 13.5.sp,
                                                            color = themeText,
                                                            lineHeight = 21.sp
                                                        )

                                                        if (userApiKey.trim().isNotEmpty()) {
                                                            Spacer(modifier = Modifier.height(12.dp))
                                                            TextButton(
                                                                onClick = { viewModel.setUserApiKey("") },
                                                                modifier = Modifier.align(Alignment.End)
                                                            ) {
                                                                Text("Xóa API Key cá nhân 🗑️", fontSize = 11.sp, color = themeSecondaryText)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (selectedTab == 1) {
                                // 7-Day Forecast Tab: "Sửa tab 7 ngày thành dự báo 7 ngày tại vị trí"
                                if (daily != null) {
                                    item {
                                        Text(
                                            text = "Dự báo 7 ngày tại vị trí: ${city.name}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = themeLavender,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                        )
                                        Text(
                                            text = "Chi tiết diễn biến thời tiết trong 7 ngày tới, bao gồm đỉnh nhiệt cực cực đại, cực thiểu và biểu hiện khí tượng tại thành phố ${city.name}.",
                                            fontSize = 12.sp,
                                            color = themeSecondaryText,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                                        )

                                        Card(
                                            shape = RoundedCornerShape(24.dp),
                                            colors = CardDefaults.cardColors(containerColor = themeSurface),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("daily_forecast_card")
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                daily.time.forEachIndexed { idx, dayRaw ->
                                                    val dayLabel = formatDayName(dayRaw)
                                                    val dateLabel = formatDateFallback(dayRaw)
                                                    val maxTemp = daily.temperature2mMax.getOrNull(idx)
                                                    val minTemp = daily.temperature2mMin.getOrNull(idx)
                                                    val code = daily.weatherCode.getOrNull(idx) ?: 0
                                                    val info = getWeatherInfo(code)

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1.3f)) {
                                                            Text(dayLabel, fontWeight = FontWeight.Bold, color = themeText, fontSize = 15.sp)
                                                            Text(dateLabel, color = themeSecondaryText, fontSize = 12.sp)
                                                        }

                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1.7f),
                                                            horizontalArrangement = Arrangement.Start
                                                        ) {
                                                            Icon(info.icon, contentDescription = null, tint = themeLavender, modifier = Modifier.size(20.dp))
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                info.description,
                                                                fontSize = 13.sp,
                                                                color = themeMutedText,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        Row(
                                                            horizontalArrangement = Arrangement.End,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text(
                                                                formatTemp(maxTemp),
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = themeText
                                                            )
                                                            Text(
                                                                " / ${formatTemp(minTemp)}",
                                                                fontSize = 13.sp,
                                                                color = themeSecondaryText
                                                            )
                                                        }
                                                    }

                                                    if (idx < daily.time.lastIndex) {
                                                        HorizontalDivider(color = themeBorder.copy(alpha = 0.4f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedTab == 2) {
                            // Map Tab: "Tab bản đồ hiển thị bản đồ thời tiết"
                            Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "Bản đồ thời tiết vệ tinh 🗺️",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = themeLavender,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                        )
                                        Text(
                                            "Hiển thị bản đồ radar thời tiết thời gian thực tại khu vực ${city.name}. Có thể kéo zoom trực quan.",
                                            fontSize = 12.sp,
                                            color = themeSecondaryText,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                                        )

                                        Card(
                                            shape = RoundedCornerShape(24.dp),
                                            colors = CardDefaults.cardColors(containerColor = themeSurface),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(480.dp)
                                        ) {
                                            val lat = city.latitude
                                            val lon = city.longitude
                                            
                                            AndroidView(
                                                factory = { ctx ->
                                                    android.webkit.WebView(ctx).apply {
                                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                                        )
                                                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                                        webViewClient = android.webkit.WebViewClient()
                                                        settings.javaScriptEnabled = true
                                                        settings.domStorageEnabled = true
                                                        settings.useWideViewPort = true
                                                        settings.loadWithOverviewMode = true
                                                        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                                                        settings.databaseEnabled = false
                                                        
                                                        val mapUrl = "https://embed.windy.com/embed2.html?lat=$lat&lon=$lon&zoom=5&level=surface&overlay=rain&product=ecmwf&menu=&message=true&marker=true&calendar=now&pressure=&type=map&location=coordinates&detail="
                                                        tag = mapUrl
                                                        loadUrl(mapUrl)
                                                    }
                                                },
                                                update = { webView ->
                                                    try {
                                                        val mapUrl = "https://embed.windy.com/embed2.html?lat=$lat&lon=$lon&zoom=5&level=surface&overlay=rain&product=ecmwf&menu=&message=true&marker=true&calendar=now&pressure=&type=map&location=coordinates&detail="
                                                        if (webView.tag != mapUrl) {
                                                            webView.tag = mapUrl
                                                            webView.loadUrl(mapUrl)
                                                        }
                                                    } catch (e: Exception) {
                                                        // ignore
                                                    }
                                                },
                                                onRelease = { webView ->
                                                    try {
                                                        webView.stopLoading()
                                                        webView.clearHistory()
                                                        webView.loadUrl("about:blank")

                                                    } catch (e: Exception) {
                                                        // ignore
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                    is WeatherUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = (uiState as WeatherUiState.Error).message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { currentCity?.let { viewModel.fetchWeather(it) } },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1.0f)
                                        ) {
                                            Text("Thử Lại")
                                        }

                                        Button(
                                            onClick = { selectedTab = 3 },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline),
                                            modifier = Modifier.weight(1.0f)
                                        ) {
                                            Text("Cài Đặt")
                                        }
                                    }

                                    if (weatherSource == "accuweather") {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "Chọn nguồn thời tiết miễn phí không cần API Key:",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.setWeatherSourceSetting("open_meteo")
                                                    currentCity?.let { viewModel.fetchWeather(it) }
                                                },
                                                modifier = Modifier.weight(1.0f)
                                            ) {
                                                Text("Open-Meteo", fontSize = 11.sp, maxLines = 1)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.setWeatherSourceSetting("met_norway")
                                                    currentCity?.let { viewModel.fetchWeather(it) }
                                                },
                                                modifier = Modifier.weight(1.0f)
                                            ) {
                                                Text("Met Norway", fontSize = 11.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = themeLavender)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun HeaderSection(
    currentCityName: String,
    todayDate: String,
    weatherSourceName: String,
    onSearchToggle: () -> Unit,
    searchActive: Boolean,
    onLocateClick: () -> Unit,
    isPermissionGranted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Vị trí",
                    tint = ElegantDarkLavender,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = currentCityName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = ElegantDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$todayDate • $weatherSourceName",
                fontSize = 13.sp,
                color = ElegantDarkMutedText,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // GPS Location fetch button
            IconButton(
                onClick = onLocateClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isPermissionGranted) ElegantDarkSurface else ElegantDarkSurface.copy(alpha = 0.5f))
                    .testTag("locate_gps_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = "Định vị vị trí",
                    tint = if (isPermissionGranted) ElegantDarkLavender else ElegantDarkMutedText,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Circular search action trigger
            IconButton(
                onClick = onSearchToggle,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (searchActive) ElegantDarkLavender else ElegantDarkSurface)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = if (searchActive) ElegantDarkPillText else ElegantDarkText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ElegantCardMetric(
    icon: ImageVector,
    label: String,
    value: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Label & Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Value text
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Desc hint
            Text(
                text = desc,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WeatherTabItem(
    icon: ImageVector,
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    themeText: Color,
    themePillBg: Color,
    themePillText: Color,
    modifier: Modifier = Modifier
) {
    val duration = 65 // Dynamic, ultra snappy fade spec
    val animSpec = androidx.compose.animation.core.tween<Float>(duration)
    val colorAnimSpec = androidx.compose.animation.core.tween<Color>(duration)

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.55f,
        animationSpec = animSpec,
        label = "tab_alpha"
    )

    val pillBgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) themePillBg else Color.Transparent,
        animationSpec = colorAnimSpec,
        label = "tab_pill_bg"
    )

    val iconColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) themePillText else themeText,
        animationSpec = colorAnimSpec,
        label = "tab_icon_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .alpha(alpha)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null // Disables any heavy unstyled ripple completely for supreme responsiveness
            ) {
                onClick()
            }
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(pillBgColor)
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = themeText,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SettingsTabContent(
    viewModel: com.example.viewmodel.WeatherViewModel,
    tempUnit: String,
    themeSetting: String,
    weatherSource: String,
    accuApiKey: String,
    userApiKey: String,
    themeSurface: Color,
    themeBorder: Color,
    themeText: Color,
    themeSecondaryText: Color,
    themeLavender: Color,
    themePillText: Color,
    isDark: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Tùy chọn hiển thị & Đơn vị đo",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = themeLavender,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        // Card 1: Temperature settings
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = themeSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Thermostat, contentDescription = null, tint = themeLavender)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đơn vị đo nhiệt độ", fontWeight = FontWeight.Bold, color = themeText, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("C" to "Độ C (°C)", "F" to "Độ F (°F)").forEach { (valStr, label) ->
                        val isSel = tempUnit == valStr
                        FilterChip(
                            selected = isSel,
                            onClick = { viewModel.setTempUnitSetting(valStr) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = themeLavender,
                                selectedLabelColor = themePillText
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Theme selection
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = themeSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = themeLavender)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chế độ giao diện (Theme)", fontWeight = FontWeight.Bold, color = themeText, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                listOf(
                    "system" to "Mặc định hệ thống 📱",
                    "light" to "Giao diện Sáng ☀️",
                    "dark" to "Giao diện Tối 🌙"
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemeSetting(mode) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = themeText, fontSize = 14.sp)
                        RadioButton(
                            selected = themeSetting == mode,
                            onClick = { viewModel.setThemeSetting(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = themeLavender
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card: Gemini API Key in Settings
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = themeSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = themeLavender)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cấu hình API Key Gemini", fontWeight = FontWeight.Bold, color = themeText, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Nhập API Key Gemini cá nhân của bạn để sử dụng tính năng cố vấn thời trang và dạo chơi từ AI. Khóa này được lưu trữ hoàn toàn bảo mật trên bộ nhớ thiết bị của bạn.",
                    color = themeSecondaryText,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                var settingsKeyInput by remember(userApiKey) { mutableStateOf(userApiKey) }
                OutlinedTextField(
                    value = settingsKeyInput,
                    onValueChange = { settingsKeyInput = it },
                    label = { Text("Gemini API Key", fontSize = 12.sp) },
                    placeholder = { Text("AIzaSy...", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeLavender,
                        unfocusedBorderColor = themeBorder,
                        focusedTextColor = themeText,
                        unfocusedTextColor = themeText
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            viewModel.setUserApiKey(settingsKeyInput.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeLavender),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.0f)
                    ) {
                        Text("Lưu thiết lập", color = themePillText, fontSize = 12.sp)
                    }

                    if (userApiKey.trim().isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.setUserApiKey("")
                                settingsKeyInput = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder)
                        ) {
                            Text("Xóa Key", color = themeText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card: Weather Source configuration
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = themeSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cloud, contentDescription = null, tint = themeLavender)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nguồn dữ liệu thời tiết", fontWeight = FontWeight.Bold, color = themeText, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Chọn nguồn thu thập dữ liệu dự báo thời tiết. Toàn cầu mặc định có Open-Meteo. MET Norway của châu Âu rất chính xác nhờ mô hình ECMWF. AccuWeather cho phép sử dụng chỉ số thực từ bộ máy thời tiết hàng đầu thế giới.",
                    color = themeSecondaryText,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                listOf(
                    "open_meteo" to "Open-Meteo (Mặc định)",
                    "met_norway" to "MET Norway (Mô hình ECMWF Châu Âu)",
                    "accuweather" to "AccuWeather (Cần nhập API Key)"
                ).forEach { (sourceKey, sourceLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setWeatherSourceSetting(sourceKey) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sourceLabel, color = themeText, fontSize = 13.sp)
                        RadioButton(
                            selected = weatherSource == sourceKey,
                            onClick = { viewModel.setWeatherSourceSetting(sourceKey) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = themeLavender
                            )
                        )
                    }
                }

                if (weatherSource == "accuweather") {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = themeBorder.copy(alpha = 0.5f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        "Cấu hình AccuWeather API Key",
                        fontWeight = FontWeight.Bold,
                        color = themeText,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Đăng ký tài khoản miễn phí tại developer.accuweather.com để lấy mã API Key hoạt động của bạn.",
                        color = themeSecondaryText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    var accuKeyInput by remember(accuApiKey) { mutableStateOf(accuApiKey) }
                    OutlinedTextField(
                        value = accuKeyInput,
                        onValueChange = { accuKeyInput = it },
                        label = { Text("AccuWeather API Key", fontSize = 12.sp) },
                        placeholder = { Text("Mã API Key cá nhân...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeLavender,
                            unfocusedBorderColor = themeBorder,
                            focusedTextColor = themeText,
                            unfocusedTextColor = themeText
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel.setAccuApiKey(accuKeyInput.trim())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeLavender),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.0f)
                        ) {
                            Text("Lưu API Key", color = themePillText, fontSize = 12.sp)
                        }

                        if (accuApiKey.trim().isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setAccuApiKey("")
                                    accuKeyInput = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder)
                            ) {
                                Text("Xóa Key", color = themeText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: Information & credits
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = themeSurface.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, themeBorder.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Thông tin ứng dụng", fontWeight = FontWeight.Bold, color = themeLavender, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Weather AI là ứng dụng tra cứu thời tiết chuyên sâu kết hợp tính toán địa chỉ GPS thực tế, biểu đồ radar toàn cầu và tư vấn thời trang thông minh từ Gemini AI.",
                    color = themeSecondaryText,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Cung cấp bởi: Open-Meteo & OpenStreetMap Nominatim", color = themeSecondaryText, fontSize = 11.sp)
            }
        }
    }
}
