package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.FavoriteCity
import com.example.data.GeocodeResult
import com.example.data.WeatherResponse
import com.example.data.WeatherRepository
import com.example.data.CurrentWeather
import com.example.data.HourlyForecast
import com.example.data.DailyForecast
import com.example.network.GeminiClient
import com.example.network.GeminiContent
import com.example.network.GeminiPart
import com.example.network.GeminiRequest
import com.example.network.NetworkClient
import com.example.network.toWeatherResponse
import com.example.network.mapAccuWeatherIconToWmo
import com.example.network.AccuCurrentCondition
import com.example.network.AccuHourlyForecast
import com.example.network.AccuDailyForecastParent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class WeatherAlert(
    val type: String,
    val severity: String,
    val message: String,
    val isSevere: Boolean
)

sealed interface WeatherUiState {
    object Idle : WeatherUiState
    object Loading : WeatherUiState
    data class Success(val weather: WeatherResponse, val city: FavoriteCity) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = WeatherRepository(database.favoriteCityDao())

    private val prefs = application.getSharedPreferences("weather_settings", android.content.Context.MODE_PRIVATE)

    private val _themeSetting = MutableStateFlow(prefs.getString("theme", "system") ?: "system")
    val themeSetting: StateFlow<String> = _themeSetting.asStateFlow()

    private val _tempUnitSetting = MutableStateFlow(prefs.getString("temp_unit", "C") ?: "C")
    val tempUnitSetting: StateFlow<String> = _tempUnitSetting.asStateFlow()

    fun setThemeSetting(theme: String) {
        prefs.edit().putString("theme", theme).apply()
        _themeSetting.value = theme
    }

    fun setTempUnitSetting(unit: String) {
        prefs.edit().putString("temp_unit", unit).apply()
        _tempUnitSetting.value = unit
    }

    private val _weatherSourceSetting = MutableStateFlow(prefs.getString("weather_source", "open_meteo") ?: "open_meteo")
    val weatherSourceSetting: StateFlow<String> = _weatherSourceSetting.asStateFlow()

    private val _accuApiKey = MutableStateFlow(prefs.getString("accu_api_key", "") ?: "")
    val accuApiKey: StateFlow<String> = _accuApiKey.asStateFlow()

    fun setWeatherSourceSetting(source: String) {
        prefs.edit().putString("weather_source", source).apply()
        _weatherSourceSetting.value = source
        refreshCurrentWeather()
    }

    fun setAccuApiKey(key: String) {
        prefs.edit().putString("accu_api_key", key).apply()
        _accuApiKey.value = key
        if (_weatherSourceSetting.value == "accuweather") {
            refreshCurrentWeather()
        }
    }

    fun getFriendlySourceName(): String {
        return when (_weatherSourceSetting.value) {
            "met_norway" -> "MET Norway (ECMWF)"
            "accuweather" -> "AccuWeather"
            else -> "Open-Meteo"
        }
    }

    fun refreshCurrentWeather() {
        val city = _currentCity.value
        if (city != null) {
            fetchWeather(city)
        }
    }

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _favorites = MutableStateFlow<List<FavoriteCity>>(emptyList())
    val favorites: StateFlow<List<FavoriteCity>> = _favorites.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodeResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodeResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _geminiState = MutableStateFlow<String?>(null)
    val geminiState: StateFlow<String?> = _geminiState.asStateFlow()

    private val _isGeminiLoading = MutableStateFlow(false)
    val isGeminiLoading: StateFlow<Boolean> = _isGeminiLoading.asStateFlow()

    private val _userApiKey = MutableStateFlow(prefs.getString("user_gemini_api_key", "") ?: "")
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    fun setUserApiKey(key: String) {
        prefs.edit().putString("user_gemini_api_key", key).apply()
        _userApiKey.value = key
        
        val currentState = _uiState.value
        if (currentState is WeatherUiState.Success) {
            _geminiState.value = null
            generateGeminiRecommendation(currentState.city, currentState.weather)
        }
    }

    fun getActiveApiKey(): String {
        val saved = _userApiKey.value.trim()
        if (saved.isNotEmpty()) {
            return saved
        }
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY" && buildKey != "YOUR_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    private val _currentCity = MutableStateFlow<FavoriteCity?>(null)
    val currentCity: StateFlow<FavoriteCity?> = _currentCity.asStateFlow()

    private val _weatherAlerts = MutableStateFlow<List<WeatherAlert>>(emptyList())
    val weatherAlerts: StateFlow<List<WeatherAlert>> = _weatherAlerts.asStateFlow()

    init {
        // Observe favorite cities and select the first one on launch
        viewModelScope.launch {
            repository.favoritesFlow.collectLatest { list ->
                _favorites.value = list
                if (_currentCity.value == null) {
                    if (list.isNotEmpty()) {
                        // Select the saved favorite city
                        selectCity(list.first())
                    } else {
                        // Fallback default city (Hanoi) if db is still loading/prepopulating
                        val defaultCity = FavoriteCity(
                            name = "Hà Nội",
                            latitude = 21.0285,
                            longitude = 105.8542,
                            country = "Việt Nam",
                            adminArea = "Thủ đô Hà Nội"
                        )
                        _currentCity.value = defaultCity
                        fetchWeather(defaultCity)
                    }
                }
            }
        }
    }

    fun selectCity(city: FavoriteCity) {
        _currentCity.value = city
        fetchWeather(city)
    }

    fun searchCity(query: String) {
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val response = NetworkClient.geocodingApi.searchCity(query)
                _searchResults.value = response.results ?: emptyList()
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun toggleFavorite(city: FavoriteCity) {
        viewModelScope.launch {
            val isFav = _favorites.value.any { it.name == city.name && Math.abs(it.latitude - city.latitude) < 0.05 }
            if (isFav) {
                val favItem = _favorites.value.find { it.name == city.name && Math.abs(it.latitude - city.latitude) < 0.05 }
                if (favItem != null) {
                    repository.deleteById(favItem.id)
                }
            } else {
                repository.insert(city)
            }
        }
    }

    fun isCityFavorite(city: FavoriteCity): Boolean {
        return _favorites.value.any { it.name == city.name && Math.abs(it.latitude - city.latitude) < 0.05 }
    }

    fun selectLocation(latitude: Double, longitude: Double, cityName: String) {
        if (cityName == "Vị trí định vị" || cityName == "Vị trí cuối cùng") {
            viewModelScope.launch(Dispatchers.IO) {
                var resolvedName = "Vị trí định vị"
                var resolvedAdmin = "Định vị GPS"
                var resolvedCountry = "Việt Nam"
                try {
                    val geocoder = android.location.Geocoder(getApplication(), java.util.Locale("vi"))
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        resolvedName = address.locality ?: address.subAdminArea ?: address.adminArea ?: address.featureName ?: "Vị trí định vị"
                        resolvedAdmin = address.adminArea ?: "Định vị GPS"
                        resolvedCountry = address.countryName ?: "Việt Nam"
                    }
                } catch (e: Exception) {
                    // fall back to OSM Nominatim API
                    try {
                        val client = okhttp3.OkHttpClient()
                        val request = okhttp3.Request.Builder()
                            .url("https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=10&addressdetails=1&accept-language=vi")
                            .header("User-Agent", "WeatherAI-Android-App")
                            .build()
                        val response = client.newCall(request).execute()
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            val addressRegex = "\"address\"\\s*:\\s*\\{([^\\}]+)\\}".toRegex()
                            val match = addressRegex.find(bodyString)
                            if (match != null) {
                                val addressJson = match.groupValues[1]
                                val city = "\"city\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(addressJson)?.groupValues?.get(1)
                                val town = "\"town\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(addressJson)?.groupValues?.get(1)
                                val village = "\"village\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(addressJson)?.groupValues?.get(1)
                                val county = "\"county\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(addressJson)?.groupValues?.get(1)
                                val state = "\"state\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(addressJson)?.groupValues?.get(1)
                                val countryVal = "\"country\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(addressJson)?.groupValues?.get(1)

                                resolvedName = city ?: town ?: village ?: county ?: state ?: "Vị trí định vị"
                                resolvedAdmin = state ?: county ?: "Định vị GPS"
                                resolvedCountry = countryVal ?: "Việt Nam"
                            }
                        }
                    } catch (ex: Exception) {
                        resolvedName = "Vị trí " + String.format("%.2f", latitude) + ", " + String.format("%.2f", longitude)
                    }
                }

                val city = FavoriteCity(
                    name = resolvedName,
                    latitude = latitude,
                    longitude = longitude,
                    country = resolvedCountry,
                    adminArea = resolvedAdmin
                )
                _currentCity.value = city
                fetchWeather(city)
            }
        } else {
            val city = FavoriteCity(
                name = cityName,
                latitude = latitude,
                longitude = longitude,
                country = "Việt Nam",
                adminArea = "Định vị GPS"
            )
            _currentCity.value = city
            fetchWeather(city)
        }
    }

    fun fetchWeather(city: FavoriteCity) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            _geminiState.value = null // reset Gemini recommendation
            _weatherAlerts.value = emptyList() // clear alerts
            try {
                val source = _weatherSourceSetting.value
                val response = when (source) {
                    "met_norway" -> {
                        NetworkClient.metNorwayApi.getCompact(city.latitude, city.longitude)
                            .toWeatherResponse(city.latitude, city.longitude)
                    }
                    "accuweather" -> {
                        fetchAccuWeather(city.latitude, city.longitude)
                    }
                    else -> {
                        val raw = NetworkClient.weatherApi.getForecast(city.latitude, city.longitude)
                        var apparentTemp = raw.hourly?.apparentTemperature?.firstOrNull()
                        try {
                            val curTimeStr = raw.currentWeather?.time
                            if (!curTimeStr.isNullOrEmpty() && raw.hourly != null) {
                                val matchIdx = raw.hourly.time.indexOfFirst { it.startsWith(curTimeStr.substring(0, 13)) }
                                if (matchIdx >= 0) {
                                    apparentTemp = raw.hourly.apparentTemperature?.getOrNull(matchIdx)
                                }
                            }
                        } catch (e: java.lang.Exception) {
                            // ignore / fallback to firstOrNull
                        }
                        val updatedCurrent = raw.currentWeather?.copy(feelsLike = apparentTemp)
                        raw.copy(currentWeather = updatedCurrent)
                    }
                }
                _uiState.value = WeatherUiState.Success(response, city)
                
                // Generate alerts
                analyzeWeatherAlerts(response)

                // Trigger Gemini recommendation
                generateGeminiRecommendation(city, response)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("Không thể tải dữ liệu thời tiết (${getFriendlySourceName()}): ${e.localizedMessage}")
            }
        }
    }

    private suspend fun fetchAccuWeather(latitude: Double, longitude: Double): WeatherResponse = coroutineScope {
        val apiKey = _accuApiKey.value.trim()
        if (apiKey.isEmpty()) {
            throw Exception("Vui lòng cấu hình AccuWeather API Key trong mục Cài đặt.")
        }
        
        // 1. Get location key
        val qString = "$latitude,$longitude"
        val locationResp = NetworkClient.accuWeatherApi.geopositionSearch(apiKey, qString)
        val locationKey = locationResp.key
        
        // 2. Fetch current, hourly, and daily forecasts in parallel using coroutine scopes
        val currentDeferred = async(Dispatchers.IO) {
            NetworkClient.accuWeatherApi.getCurrentConditions(locationKey, apiKey)
        }
        val hourlyDeferred = async(Dispatchers.IO) {
            NetworkClient.accuWeatherApi.get12HourForecast(locationKey, apiKey)
        }
        val dailyDeferred = async(Dispatchers.IO) {
            NetworkClient.accuWeatherApi.get5DayForecast(locationKey, apiKey)
        }
        
        val currentList = currentDeferred.await()
        val hourlyList = hourlyDeferred.await()
        val dailyParent = dailyDeferred.await()
        
        mapAccuWeatherToWeatherResponse(latitude, longitude, currentList, hourlyList, dailyParent)
    }

    private fun mapAccuWeatherToWeatherResponse(
        lat: Double,
        lon: Double,
        currentList: List<AccuCurrentCondition>,
        hourlyList: List<AccuHourlyForecast>,
        dailyParent: AccuDailyForecastParent
    ): WeatherResponse {
        val currentItem = currentList.firstOrNull() ?: throw Exception("Không có dữ liệu thời tiết thực tế từ AccuWeather.")
        
        val currentWeather = CurrentWeather(
            temperature = currentItem.temperature?.metric?.value ?: 25.0,
            windspeed = currentItem.wind?.speed?.metric?.value ?: 2.0,
            winddirection = 0.0,
            weathercode = com.example.network.mapAccuWeatherIconToWmo(currentItem.weatherIcon ?: 1),
            time = com.example.network.convertUtcToApproxLocalString(currentItem.localObservationDateTime ?: "", lon),
            feelsLike = currentItem.realFeelTemperature?.metric?.value ?: currentItem.temperature?.metric?.value
        )
        
        // 2. Hourly
        val hourlyTimes = hourlyList.map { com.example.network.convertUtcToApproxLocalString(it.dateTime, lon) }
        val hourlyTemps = hourlyList.map { it.temperature?.value ?: 25.0 }
        val hourlyHumids = hourlyList.map { it.relativeHumidity ?: 70 }
        val hourlyCodes = hourlyList.map { com.example.network.mapAccuWeatherIconToWmo(it.weatherIcon ?: 1) }
        val hourlyWinds = hourlyList.map { it.wind?.speed?.value ?: 2.0 }
        val hourlyPrecipProbs = hourlyList.map { it.precipitationProbability ?: 0 }
        
        val hourlyForecast = HourlyForecast(
            time = hourlyTimes,
            temperature2m = hourlyTemps,
            relativeHumidity2m = hourlyHumids,
            weatherCode = hourlyCodes,
            windSpeed10m = hourlyWinds,
            precipitationProbability = hourlyPrecipProbs
        )
        
        // 3. Daily
        val dailyList = dailyParent.dailyForecasts ?: emptyList()
        val dailyTimes = dailyList.map { com.example.network.convertUtcToApproxLocalString(it.date, lon).substringBefore("T") }
        val dailyWeatherCodes = dailyList.map { com.example.network.mapAccuWeatherIconToWmo(it.day?.icon ?: 1) }
        val dailyTempMax = dailyList.map { it.temperature?.maximum?.value ?: 25.0 }
        val dailyTempMin = dailyList.map { it.temperature?.minimum?.value ?: 18.0 }
        val dailyUv = dailyList.map { 5.0 }
        val dailyPrecipProbMax = dailyList.map { it.day?.precipitationProbability ?: 0 }
        
        val dailyForecast = DailyForecast(
            time = dailyTimes,
            weatherCode = dailyWeatherCodes,
            temperature2mMax = dailyTempMax,
            temperature2mMin = dailyTempMin,
            uvIndexMax = dailyUv,
            precipitationProbabilityMax = dailyPrecipProbMax
        )
        
        return WeatherResponse(
            latitude = lat,
            longitude = lon,
            currentWeather = currentWeather,
            hourly = hourlyForecast,
            daily = dailyForecast
        )
    }

    private fun analyzeWeatherAlerts(response: WeatherResponse) {
        val alerts = mutableListOf<WeatherAlert>()
        val current = response.currentWeather
        val daily = response.daily

        if (current != null) {
            // Windspeed alert
            if (current.windspeed > 24.0) {
                alerts.add(
                    WeatherAlert(
                        type = "Gió Giật Mạnh 🍃",
                        severity = "Cảnh báo cấp độ 1",
                        message = "Tốc độ gió hiện hành là ${current.windspeed} km/h. Hãy che chắn kỹ các vật dụng dễ bay ngoài trời và hạn chế đỗ xe dưới gốc cây cổ thụ lớn.",
                        isSevere = true
                    )
                )
            }

            // Extreme Temperature alerts
            if (current.temperature > 37.0) {
                alerts.add(
                    WeatherAlert(
                        type = "Nắng Nóng Cực Đoan 🥵🔥",
                        severity = "Cảnh báo nguy hiểm",
                        message = "Nhiệt độ đo được đạt ngưỡng cực đại ${current.temperature}°C. Nguy cơ tia UV cực cao, hãy bôi kem chống nắng, mặc bảo hộ và hạn chế ra ngoài từ 11h - 15h.",
                        isSevere = true
                    )
                )
            } else if (current.temperature < 15.0) {
                alerts.add(
                    WeatherAlert(
                        type = "Khí Lạnh Cận Hạn 🥶❄️",
                        severity = "Khuyến cáo giữ ấm",
                        message = "Nhiệt độ hạ thấp xuống ${current.temperature}°C. Chú ý giữ ấm đường hô hấp, đặc biệt là người già và trẻ nhỏ.",
                        isSevere = false
                    )
                )
            }

            // Weather Code alerts (Storms, Heavy rains)
            when (current.weathercode) {
                95, 96, 99 -> {
                    alerts.add(
                        WeatherAlert(
                            type = "Giông Sét & Lốc Xoáy ⛈️⚡",
                            severity = "Khẩn cấp cực đoan",
                            message = "Phát hiện có ổ mây dông phát triển sấm sét dồn dập. Tuyệt đối không đứng dưới cột điện, trạm sạc hay cây cao khi có tiếng sấm.",
                            isSevere = true
                        )
                    )
                }
                81, 82, 65 -> {
                    alerts.add(
                        WeatherAlert(
                            type = "Mưa Lớn Ngập Úng 🌧️🌊",
                            severity = "Cảnh báo ngập úng",
                            message = "Lượng mưa tương đương cấp độ rào lớn dồn dập. Đề phòng ngập sâu cục bộ tại các tuyến phố trũng thấp và sạt lở đối với địa hình đồi núi.",
                            isSevere = true
                        )
                    )
                }
            }
        }

        if (daily != null) {
            val maxProb = daily.precipitationProbabilityMax?.firstOrNull() ?: 0
            if (maxProb > 80 && (current?.weathercode ?: 0) < 50) {
                alerts.add(
                    WeatherAlert(
                        type = "Khả Năng Mưa Rất Cao 🌧️",
                        severity = "Khuyến cáo phòng bị",
                        message = "Khả năng mưa trong ngày hôm nay lên tới $maxProb%. Hãy trang bị sẵn ô dù và áo mưa trong cốp xe trước khi rời khỏi nhà.",
                        isSevere = false
                    )
                )
            }
            val uv = daily.uvIndexMax?.firstOrNull() ?: 1.0
            if (uv >= 8.0) {
                alerts.add(
                    WeatherAlert(
                        type = "Tia Cực Tím Rất Cao ☀️☠️",
                        severity = "Nguy hiểm sức khỏe",
                        message = "Chỉ số UV tối đa trong ngày hôm nay đạt ngưỡng $uv. Đeo kính râm bảo vệ võng mạc và mặc quần áo dài tay chống nắng tối đa.",
                        isSevere = true
                    )
                )
            }
        }

        _weatherAlerts.value = alerts
    }

    private fun generateGeminiRecommendation(city: FavoriteCity, weather: WeatherResponse) {
        val apiKey = getActiveApiKey()
        if (apiKey.isEmpty()) {
            _geminiState.value = "⚠️ Chưa cấu hình API Key Gemini.\nVui lòng nhập API Key để bắt đầu nhận phân tích quần áo thời trang, sức khỏe và dạo chơi tự động từ AI!"
            return
        }

        val current = weather.currentWeather ?: return
        val daily = weather.daily ?: return

        val weatherString = """
            Địa điểm: ${city.name}, ${city.adminArea ?: ""}, ${city.country ?: "Việt Nam"}
            Nhiệt độ hiện tại: ${current.temperature}°C
            Sức gió: ${current.windspeed} km/h
            Tình trạng thời tiết (WMO code): ${current.weathercode}
            Dự báo hôm nay: Cao nhất ${daily.temperature2mMax.firstOrNull() ?: current.temperature}°C, Thấp nhất ${daily.temperature2mMin.firstOrNull() ?: current.temperature}°C
            Chỉ số UV lớn nhất: ${daily.uvIndexMax?.firstOrNull() ?: "Không rõ"}
            Khả năng mưa lớn nhất: ${daily.precipitationProbabilityMax?.firstOrNull() ?: 0}%
        """.trimIndent()

        viewModelScope.launch(Dispatchers.IO) {
            _isGeminiLoading.value = true
            try {
                val systemInstructionText = """
                    Bạn là một vị Trợ lý Thời tiết AI chuyên nghiệp, tinh tế bằng tiếng Việt.
                    Dựa vào thông tin thời tiết được cung cấp, hãy viết một đoạn nhận xét/phân tích ngắn gọn (khoảng 3-4 câu) gồm:
                    1. Đánh giá thời tiết hôm nay thoải mái, nóng hay lạnh.
                    2. Gợi ý cụ thể trang phục phù hợp nhất (ví dụ: mang theo ô/áo mưa, đội mũ chống nắng, mặc áo khoác mỏng, mang kính râm...).
                    3. Lời khuyên ngắn về chăm sóc sức khỏe hoặc hoạt động ngoài trời.
                    Hãy viết một cách thân thiện, tự nhiên, cuốn hút và bố cục rõ ràng có bullet point hoặc icon. Không dài dòng.
                """.trimIndent()

                val prompt = "Hãy đưa ra nhận xét thời tiết và đề xuất trang phục cho ngày hôm nay:\n$weatherString"

                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText)))
                )

                // Try calling with highly stable, standard models in priority order
                val modelsToTry = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite-preview")
                var textResponse: String? = null
                var lastException: Exception? = null

                for (model in modelsToTry) {
                    try {
                        Log.d("WeatherViewModel", "Trying Gemini call with model: $model")
                        val response = GeminiClient.api.generateContentForModel(model, apiKey, request)
                        textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (!textResponse.isNullOrBlank()) {
                            Log.d("WeatherViewModel", "Gemini success using model: $model")
                            break
                        }
                    } catch (e: retrofit2.HttpException) {
                        Log.w("WeatherViewModel", "HttpException model $model: code=${e.code()}, message=${e.message()}")
                        lastException = e
                        if (e.code() == 400 || e.code() == 403) {
                            Log.e("WeatherViewModel", "Client error detected (Bad Key/Unauthorized). Aborting retries.")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w("WeatherViewModel", "Failed model $model: ${e.message}")
                        lastException = e
                    }
                }

                // Sub-fallback: If all model calls with systemInstruction fail, try calling without systemInstruction (embed in prompt)
                if (textResponse.isNullOrBlank() && !(lastException is retrofit2.HttpException && (lastException.code() == 400 || lastException.code() == 403))) {
                    Log.i("WeatherViewModel", "Attempting fallback without separate systemInstruction...")
                    val combinedPrompt = "$systemInstructionText\n\n$prompt"
                    val requestNoInstruction = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = combinedPrompt)))),
                        systemInstruction = null
                    )
                    for (model in modelsToTry) {
                        try {
                            Log.d("WeatherViewModel", "Trying Gemini (no instruction) with model: $model")
                            val response = GeminiClient.api.generateContentForModel(model, apiKey, requestNoInstruction)
                            textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (!textResponse.isNullOrBlank()) {
                                Log.d("WeatherViewModel", "Gemini success (no instruction) using model: $model")
                                break
                            }
                        } catch (e: retrofit2.HttpException) {
                            Log.w("WeatherViewModel", "HttpException (no instruction) model $model: code=${e.code()}")
                            lastException = e
                            if (e.code() == 400 || e.code() == 403) {
                                break
                            }
                        } catch (e: Exception) {
                            Log.w("WeatherViewModel", "Failed model (no instruction) $model: ${e.message}")
                            lastException = e
                        }
                    }
                }

                if (!textResponse.isNullOrBlank()) {
                    _geminiState.value = textResponse
                } else {
                    val errorMsg = lastException?.localizedMessage ?: "Không thể kết nối với máy chủ AI."
                    Log.e("WeatherViewModel", "Gemini AI completely failed: $errorMsg", lastException)
                    _geminiState.value = "⚠️ Trợ lý AI chưa phản hồi: $errorMsg\nHãy kiểm tra kết nối mạng hoặc thử lại với API Key hợp lệ trong cài đặt."
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "Chưa rõ nguyên nhân"
                Log.e("WeatherViewModel", "Gemini final catch failed: $errorMsg", e)
                _geminiState.value = "⚠️ Lỗi hệ thống AI: $errorMsg\nVui lòng thử lại sau giây lát."
            } finally {
                _isGeminiLoading.value = false
            }
        }
    }
}
