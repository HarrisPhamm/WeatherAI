package com.example.network

import com.example.data.GeocodeResponse
import com.example.data.WeatherResponse
import com.example.data.CurrentWeather
import com.example.data.HourlyForecast
import com.example.data.DailyForecast
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeocodingApi {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "vi",
        @Query("format") format: String = "json"
    ): GeocodeResponse
}

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current_weather") currentWeather: Boolean = true,
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,precipitation_probability",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,uv_index_max,precipitation_probability_max",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

interface MetNorwayApi {
    @Headers("User-Agent: WeatherAI/1.0 harrisphammm@gmail.com")
    @GET("weatherapi/locationforecast/2.0/compact")
    suspend fun getCompact(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): MetNorwayResponse
}

interface AccuWeatherApi {
    @GET("locations/v1/cities/geoposition/search")
    suspend fun geopositionSearch(
        @Query("apikey") apiKey: String,
        @Query("q") latLon: String,
        @Query("language") language: String = "vi-vn"
    ): AccuLocationResponse

    @GET("currentconditions/v1/{locationKey}")
    suspend fun getCurrentConditions(
        @Path("locationKey") locationKey: String,
        @Query("apikey") apiKey: String,
        @Query("details") details: Boolean = true,
        @Query("language") language: String = "vi-vn"
    ): List<AccuCurrentCondition>

    @GET("forecasts/v1/hourly/12hour/{locationKey}")
    suspend fun get12HourForecast(
        @Path("locationKey") locationKey: String,
        @Query("apikey") apiKey: String,
        @Query("details") details: Boolean = true,
        @Query("metric") metric: Boolean = true,
        @Query("language") language: String = "vi-vn"
    ): List<AccuHourlyForecast>

    @GET("forecasts/v1/daily/5day/{locationKey}")
    suspend fun get5DayForecast(
        @Path("locationKey") locationKey: String,
        @Query("apikey") apiKey: String,
        @Query("metric") metric: Boolean = true,
        @Query("language") language: String = "vi-vn"
    ): AccuDailyForecastParent
}

// MET Norway Response Classes
@JsonClass(generateAdapter = true)
data class MetNorwayResponse(
    @Json(name = "properties") val properties: MetNorwayProperties?
)

@JsonClass(generateAdapter = true)
data class MetNorwayProperties(
    @Json(name = "timeseries") val timeseries: List<MetNorwayTimeSeries>?
)

@JsonClass(generateAdapter = true)
data class MetNorwayTimeSeries(
    @Json(name = "time") val time: String,
    @Json(name = "data") val data: MetNorwayData?
)

@JsonClass(generateAdapter = true)
data class MetNorwayData(
    @Json(name = "instant") val instant: MetNorwayInstant?,
    @Json(name = "next_1_hours") val next1Hours: MetNorwayNextHours?,
    @Json(name = "next_6_hours") val next6Hours: MetNorwayNextHours?,
    @Json(name = "next_12_hours") val next12Hours: MetNorwayNextHours?
)

@JsonClass(generateAdapter = true)
data class MetNorwayInstant(
    @Json(name = "details") val details: MetNorwayInstantDetails?
)

@JsonClass(generateAdapter = true)
data class MetNorwayInstantDetails(
    @Json(name = "air_temperature") val airTemperature: Double?,
    @Json(name = "relative_humidity") val relativeHumidity: Double?,
    @Json(name = "wind_from_direction") val windFromDirection: Double?,
    @Json(name = "wind_speed") val windSpeed: Double?,
    @Json(name = "ultraviolet_index_clear_sky") val ultravioletIndexClearSky: Double?
)

@JsonClass(generateAdapter = true)
data class MetNorwayNextHours(
    @Json(name = "summary") val summary: MetNorwaySummary?,
    @Json(name = "details") val details: MetNorwayPrecipitationDetails?
)

@JsonClass(generateAdapter = true)
data class MetNorwaySummary(
    @Json(name = "symbol_code") val symbolCode: String?
)

@JsonClass(generateAdapter = true)
data class MetNorwayPrecipitationDetails(
    @Json(name = "precipitation_amount") val precipitationAmount: Double?,
    @Json(name = "probability_of_precipitation") val probabilityOfPrecipitation: Double?
)

// AccuWeather Response Classes
@JsonClass(generateAdapter = true)
data class AccuLocationResponse(
    @Json(name = "Key") val key: String,
    @Json(name = "LocalizedName") val localizedName: String?,
    @Json(name = "Country") val country: AccuCountry?
)

@JsonClass(generateAdapter = true)
data class AccuCountry(
    @Json(name = "ID") val id: String?,
    @Json(name = "LocalizedName") val localizedName: String?
)

@JsonClass(generateAdapter = true)
data class AccuCurrentCondition(
    @Json(name = "LocalObservationDateTime") val localObservationDateTime: String?,
    @Json(name = "WeatherText") val weatherText: String?,
    @Json(name = "WeatherIcon") val weatherIcon: Int?,
    @Json(name = "IsDayTime") val isDayTime: Boolean?,
    @Json(name = "Temperature") val temperature: AccuTemperature?,
    @Json(name = "RelativeHumidity") val relativeHumidity: Int?,
    @Json(name = "Wind") val wind: AccuWind?,
    @Json(name = "UVIndex") val uvIndex: Double?
)

@JsonClass(generateAdapter = true)
data class AccuTemperature(
    @Json(name = "Metric") val metric: AccuMetricValue?,
    @Json(name = "Imperial") val imperial: AccuMetricValue?
)

@JsonClass(generateAdapter = true)
data class AccuMetricValue(
    @Json(name = "Value") val value: Double?,
    @Json(name = "Unit") val unit: String?
)

@JsonClass(generateAdapter = true)
data class AccuWind(
    @Json(name = "Speed") val speed: AccuTemperature?
)

@JsonClass(generateAdapter = true)
data class AccuHourlyWind(
    @Json(name = "Speed") val speed: AccuMetricValue?
)

@JsonClass(generateAdapter = true)
data class AccuHourlyForecast(
    @Json(name = "DateTime") val dateTime: String,
    @Json(name = "WeatherIcon") val weatherIcon: Int?,
    @Json(name = "IconPhrase") val iconPhrase: String?,
    @Json(name = "Temperature") val temperature: AccuMetricValue?,
    @Json(name = "RelativeHumidity") val relativeHumidity: Int?,
    @Json(name = "Wind") val wind: AccuHourlyWind?,
    @Json(name = "PrecipitationProbability") val precipitationProbability: Int?
)

@JsonClass(generateAdapter = true)
data class AccuDailyForecastParent(
    @Json(name = "DailyForecasts") val dailyForecasts: List<AccuDailyForecast>?
)

@JsonClass(generateAdapter = true)
data class AccuDailyForecast(
    @Json(name = "Date") val date: String,
    @Json(name = "Temperature") val temperature: AccuDailyTempRange?,
    @Json(name = "Day") val day: AccuDayNightForecast?,
    @Json(name = "Night") val night: AccuDayNightForecast?
)

@JsonClass(generateAdapter = true)
data class AccuDailyTempRange(
    @Json(name = "Minimum") val minimum: AccuMetricValue?,
    @Json(name = "Maximum") val maximum: AccuMetricValue?
)

@JsonClass(generateAdapter = true)
data class AccuDayNightForecast(
    @Json(name = "Icon") val icon: Int?,
    @Json(name = "IconPhrase") val iconPhrase: String?,
    @Json(name = "PrecipitationProbability") val precipitationProbability: Int?
)

object NetworkClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    val geocodingApi: GeocodingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeocodingApi::class.java)
    }

    val weatherApi: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WeatherApi::class.java)
    }

    val metNorwayApi: MetNorwayApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.met.no/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MetNorwayApi::class.java)
    }

    val accuWeatherApi: AccuWeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://dataservice.accuweather.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AccuWeatherApi::class.java)
    }
}

// Mapping Utilities
fun mapMetNorwaySymbolToWmo(symbol: String): Int {
    val clean = symbol.substringBefore("_")
    return when (clean) {
        "clearsky" -> 0
        "fair" -> 1
        "partlycloudy" -> 2
        "cloudy" -> 3
        "fog" -> 45
        "lightrainshowers", "lightrain" -> 51
        "rainshowers", "rain" -> 61
        "heavyrainshowers", "heavyrain" -> 65
        "lightsleetshowers", "lightsleet" -> 71
        "sleetshowers", "sleet" -> 73
        "heavysleetshowers", "heavysleet" -> 75
        "lightsnowshowers", "lightsnow" -> 77
        "snowshowers", "snow" -> 85
        "heavysnowshowers", "heavysnow" -> 86
        "lightrainshowersandthunder", "lightrainandthunder" -> 95
        "rainshowersandthunder", "rainandthunder", "heavyrainshowersandthunder", "heavyrainandthunder" -> 96
        "lightsleetshowersandthunder", "sleetshowersandthunder", "heavysleetshowersandthunder" -> 95
        "lightsnowshowersandthunder", "snowshowersandthunder", "heavysnowshowersandthunder" -> 99
        else -> 0
    }
}

fun mapAccuWeatherIconToWmo(icon: Int): Int {
    return when (icon) {
        1, 2, 3 -> 0 // Clear/Mainly Clear
        4, 5, 6 -> 2 // Partly cloudy
        7, 8 -> 3 // Overcast
        11, 12 -> 45 // Fog
        13, 14, 18, 26 -> 61 // Rain
        15, 16, 17 -> 95 // Thunderstorms
        19, 20, 21, 22, 23 -> 71 // Snow
        24, 25 -> 73 // Sleet
        33, 34 -> 0 // Clear night
        35, 36, 37 -> 2 // Partly cloudy night
        38 -> 3 // Overcast night
        else -> 0
    }
}

fun convertUtcToLocalString(utcString: String): String {
    if (utcString.isEmpty()) return ""
    try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX", 
            "yyyy-MM-dd'T'HH:mm:ss'Z'",  
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm"
        )
        
        var date: java.util.Date? = null
        for (fmt in formats) {
            try {
                val parser = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                if (fmt.contains("'Z'")) {
                    parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                date = parser.parse(utcString)
                if (date != null) break
            } catch (e: Exception) {
                // Ignore and try next
            }
        }
        
        if (date != null) {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US)
            formatter.timeZone = java.util.TimeZone.getDefault()
            return formatter.format(date)
        }
    } catch (e: Exception) {
        // ignore and fallback
    }
    
    var clean = utcString
    if (clean.contains("+")) {
         clean = clean.substringBefore("+")
    }
    if (clean.contains("Z")) {
         clean = clean.replace("Z", "")
    }
    if (clean.contains(".")) {
         clean = clean.substringBefore(".")
    }
    if (clean.length > 16) {
         clean = clean.substring(0, 16)
    }
    return clean
}

fun MetNorwayResponse.toWeatherResponse(lat: Double, lon: Double): WeatherResponse {
    val rawTimeseries = this.properties?.timeseries ?: emptyList()
    
    // Map UTC times to local system time zone strings
    val timeseries = rawTimeseries.map { series ->
        series.copy(time = convertUtcToLocalString(series.time))
    }
    
    // 1. Current Weather
    val currentSeries = timeseries.firstOrNull()
    val currentDetails = currentSeries?.data?.instant?.details
    val currentSymbol = currentSeries?.data?.next1Hours?.summary?.symbolCode 
        ?: currentSeries?.data?.next6Hours?.summary?.symbolCode
        ?: currentSeries?.data?.next12Hours?.summary?.symbolCode
        ?: "clearsky"
    
    val rawWind = (currentDetails?.windSpeed ?: 2.0) * 3.6
    val roundedWind = kotlin.math.round(rawWind * 100.0) / 100.0

    val currentWeather = CurrentWeather(
        temperature = currentDetails?.airTemperature ?: 25.0,
        windspeed = roundedWind, // m/s to km/h rounded to 2 decimal places
        winddirection = currentDetails?.windFromDirection ?: 0.0,
        weathercode = mapMetNorwaySymbolToWmo(currentSymbol),
        time = currentSeries?.time ?: ""
    )
    
    // 2. Hourly Forecast
    val hourlyTimes = mutableListOf<String>()
    val hourlyTemps = mutableListOf<Double>()
    val hourlyHumids = mutableListOf<Int>()
    val hourlyCodes = mutableListOf<Int>()
    val hourlyWinds = mutableListOf<Double>()
    val hourlyPrecipProbs = mutableListOf<Int>()
    
    // Take up to 24 hours
    timeseries.take(24).forEach { series ->
        val details = series.data?.instant?.details
        val hourSymbol = series.data?.next1Hours?.summary?.symbolCode
            ?: series.data?.next6Hours?.summary?.symbolCode
            ?: "clearsky"
        
        hourlyTimes.add(series.time)
        hourlyTemps.add(details?.airTemperature ?: 25.0)
        hourlyHumids.add((details?.relativeHumidity ?: 70.0).toInt())
        hourlyCodes.add(mapMetNorwaySymbolToWmo(hourSymbol))
        val rawHourWind = (details?.windSpeed ?: 2.0) * 3.6
        val roundedHourWind = kotlin.math.round(rawHourWind * 100.0) / 100.0
        hourlyWinds.add(roundedHourWind) // m/s to km/h rounded to 2 decimal places
        val precipProb = series.data?.next1Hours?.details?.probabilityOfPrecipitation
            ?: series.data?.next6Hours?.details?.probabilityOfPrecipitation
            ?: 0.0
        hourlyPrecipProbs.add(precipProb.toInt())
    }
    
    val hourlyForecast = HourlyForecast(
        time = hourlyTimes,
        temperature2m = hourlyTemps,
        relativeHumidity2m = hourlyHumids,
        weatherCode = hourlyCodes,
        windSpeed10m = hourlyWinds,
        precipitationProbability = hourlyPrecipProbs
    )
    
    // 3. Daily Forecast (Aggregate hourly by day, up to 7 days)
    val dailyTimes = mutableListOf<String>()
    val dailyWeatherCodes = mutableListOf<Int>()
    val dailyTempMax = mutableListOf<Double>()
    val dailyTempMin = mutableListOf<Double>()
    val dailyUv = mutableListOf<Double>()
    val dailyPrecipProbMax = mutableListOf<Int>()
    
    // Group series by date string (YYYY-MM-DD)
    val groupedByDay = timeseries.groupBy { series ->
        if (series.time.length >= 10) series.time.substring(0, 10) else series.time
    }
    
    groupedByDay.keys.sorted().take(7).forEach { dateString ->
        val daySeries = groupedByDay[dateString] ?: emptyList()
        val temps = daySeries.mapNotNull { it.data?.instant?.details?.airTemperature }
        val maxTemp = temps.maxOrNull() ?: 25.0
        val minTemp = temps.minOrNull() ?: 18.0
        
        // Find weather symbol for around noon (12:00) or most common
        val middleIndex = daySeries.size / 2
        val repSeries = if (daySeries.isNotEmpty()) daySeries[middleIndex] else null
        val repSymbol = repSeries?.data?.next6Hours?.summary?.symbolCode
            ?: repSeries?.data?.next1Hours?.summary?.symbolCode
            ?: "clearsky"
        
        val maxUv = daySeries.mapNotNull { it.data?.instant?.details?.ultravioletIndexClearSky }.maxOrNull() ?: 5.0
        
        val maxPrecipProb = daySeries.map { series ->
            val p = series.data?.next1Hours?.details?.probabilityOfPrecipitation
                ?: series.data?.next6Hours?.details?.probabilityOfPrecipitation
                ?: 0.0
            p.toInt()
        }.maxOrNull() ?: 0
        
        dailyTimes.add(dateString)
        dailyWeatherCodes.add(mapMetNorwaySymbolToWmo(repSymbol))
        dailyTempMax.add(maxTemp)
        dailyTempMin.add(minTemp)
        dailyUv.add(maxUv)
        dailyPrecipProbMax.add(maxPrecipProb)
    }
    
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
