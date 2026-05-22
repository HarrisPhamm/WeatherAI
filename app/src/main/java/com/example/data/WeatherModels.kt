package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeocodeResponse(
    @Json(name = "results") val results: List<GeocodeResult>?
)

@JsonClass(generateAdapter = true)
data class GeocodeResult(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "country") val country: String?,
    @Json(name = "admin1") val admin1: String?
)

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "current_weather") val currentWeather: CurrentWeather?,
    @Json(name = "hourly") val hourly: HourlyForecast?,
    @Json(name = "daily") val daily: DailyForecast?
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @Json(name = "temperature") val temperature: Double,
    @Json(name = "windspeed") val windspeed: Double,
    @Json(name = "winddirection") val winddirection: Double,
    @Json(name = "weathercode") val weathercode: Int,
    @Json(name = "time") val time: String
)

@JsonClass(generateAdapter = true)
data class HourlyForecast(
    @Json(name = "time") val time: List<String>,
    @Json(name = "temperature_2m") val temperature2m: List<Double>,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: List<Int>?,
    @Json(name = "weather_code") val weatherCode: List<Int>?,
    @Json(name = "wind_speed_10m") val windSpeed10m: List<Double>?,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int>?
)

@JsonClass(generateAdapter = true)
data class DailyForecast(
    @Json(name = "time") val time: List<String>,
    @Json(name = "weather_code") val weatherCode: List<Int>,
    @Json(name = "temperature_2m_max") val temperature2mMax: List<Double>,
    @Json(name = "temperature_2m_min") val temperature2mMin: List<Double>,
    @Json(name = "uv_index_max") val uvIndexMax: List<Double>?,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int>?
)
