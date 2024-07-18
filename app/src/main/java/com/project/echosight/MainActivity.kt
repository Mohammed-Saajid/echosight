package com.project.echosight
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Telephony
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

import java.io.File
import java.io.IOException
import java.util.Locale
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST

import retrofit2.http.Part
import kotlin.coroutines.resume
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.type.content
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.GsonBuilder
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.FileOutputStream
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
data class YouTubeResponse(
    val items: List<YouTubeItem>
)
data class YouTubeItem(
    val id: YouTubeId,
    val snippet: YouTubeSnippet
)
data class YouTubeId(
    val videoId: String
)
data class YouTubeSnippet(
    val title: String,
    val description: String,
    val thumbnails: YouTubeThumbnails
)
data class YouTubeThumbnails(
    val default: YouTubeThumbnail
)
data class YouTubeThumbnail(
    val url: String
)
interface YouTubeApi {
    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String,
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("maxResults") maxResults: Int,
        @Query("key") apiKey: String
    ): Response<YouTubeResponse>
}
object RetrofitInstance2 {
    val api: YouTubeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YouTubeApi::class.java)
    }
}
object GoogleDirectionsRetrofit {
    private const val BASE_URL = "https://maps.googleapis.com/maps/api/"
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
data class DirectionsResponse(
    val routes: List<Route>
)
data class Route(
    val legs: List<Leg>
)
data class Leg(
    val steps: List<Step>
)
data class Step(
    val html_instructions: String,
    val distance: Distance,
    val duration: Duration,
    val travel_mode: String,
    val transit_details: TransitDetails? = null
)
data class Distance(
    val text: String
)
data class TransitDetails(
    val line: Line,
    val departure_stop: Stop,
    val arrival_stop: Stop
)
data class Line(
    val name: String,
    val short_name: String
)
data class Stop(
    val name: String
)
data class Duration(
    val text: String
)
interface DirectionsService {
    @GET("directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String,
        @Query("key") apiKey: String
    ): DirectionsResponse
}
interface ImageService {
    @GET("/capture")
    fun requestImage(): Call<ResponseBody>
    @GET("/status")
    suspend fun checkServerStatus(): String
}
object RetrofitInstance {
    private const val BASE_URL = "http://192.168.1.6"
    private val gson = GsonBuilder()
        .setLenient()
        .create()
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val imageService: ImageService by lazy {
        retrofit.create(ImageService::class.java)
    }
}
interface ApiService {
    @Multipart
    @POST("uploadImages")
    fun uploadImages(
        @Part parts: List<MultipartBody.Part>
    ): Call<ResponseBody>
    @GET("/result/{task_id}")
    fun getResult(@Path("task_id") taskId: String): Call<ResultResponse>
    @Multipart
    @POST("/upload")
    fun uploadImage(@Part image: MultipartBody.Part): Call<UploadResponse>
}
data class UploadResponse(val task_id: String)
data class ResultResponse(val status: String, val result: String?)
object RetrofitClient {
    private const val BASE_URL = "https://ba98-122-164-82-208.ngrok-free.app"
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    data class WeatherData(
        val weather: List<WeatherDescription>,
        val main: TemperatureInfo
    )
    data class WeatherDescription(
        val description:String
    )
    data class TemperatureInfo(
        val temp: Double
    )
    interface OpenWeatherMapApi {
        @GET("data/2.5/weather")
        suspend fun getCurrentWeather(
            @Query("q") city: String,
            @Query("appid") apiKey: String,
            @Query("units") units: String = "metric"
        ): Response<WeatherData>
    }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()
    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
        retrofit.create(ApiService::class.java)
    }
}
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener{
    enum class ListeningMode {
        COMMAND,
        ADD_TASK
    }
    companion object {

        const val PERMISSION_REQUEST_CODE = 1001
    }
    private lateinit var textToSpeech: TextToSpeech
    private val SMS_PERMISSION_CODE = 101
    private lateinit var speechRecognizerLauncher: ActivityResultLauncher<Intent>
    var recognizedText: String? = ""
    var geminiapikey = com.project.echosight.BuildConfig.geminiapikey
    var youtubedfapikey = com.project.echosight.BuildConfig.youtubedfapikey
    var openweatherapi = com.project.echosight.BuildConfig.openweatherapikey
    var gmapsapikey = com.project.echosight.BuildConfig.gmapsapikey
    var aiResponseText = ""
    lateinit var Translatorr : Translator
    lateinit var Translatorrr : Translator
    private var alarmTime by mutableStateOf<Calendar?>(null)
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    var tasks = mutableListOf<String>()
    var listeningMode = ListeningMode.COMMAND
    var taskId = ""
    var LanguageVariable = ""
    var LanguageName = "english"
    var LanguageCode1 = ""
    var LanguageCode2 = ""
    @RequiresApi(Build.VERSION_CODES.P)
    private val requiredPermissions = arrayOf(
        Manifest.permission.BATTERY_STATS,
        Manifest.permission.PACKAGE_USAGE_STATS,
        Manifest.permission.CAMERA,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.SET_ALARM,
        Manifest.permission.SCHEDULE_EXACT_ALARM,
        Manifest.permission.USE_EXACT_ALARM,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )
    val languageMap1 = mapOf(
        "english" to "en",
        "french" to "fr",
        "german" to "de",
        "spanish" to "es",
        "chinese" to "zh",
        "japanese" to "ja",
        "tamil" to "ta",
        "croatian" to "hr",
        "korean" to "ko",
        "marathi" to "mr",
        "russian" to "ru",
        "hungarian" to "hu",
        "swahili" to "sw",
        "thai" to "th",
        "urdu" to "ur",
        "norwegian" to "no",
        "danish" to "da",
        "turkish" to "tr",
        "estonian" to "et",
        "portuguese" to "pt",
        "vietnamese" to "vi",
        "albanian" to "sq",
        "swedish" to "sv",
        "arabic" to "ar",
        "bengali" to "bn",
        "gujarati" to "gu",
        "kannada" to "kn",
        "greek" to "el",
        "hindi" to "hi",
        "hebrew" to "he",
        "finnish" to "fi",
        "ukrainian" to "uk",
        "dutch" to "nl",
        "latvian" to "lv",
        "czech" to "cs",
        "icelandic" to "is",
        "polish" to "pl",
        "catalan" to "ca",
        "slovak" to "sk",
        "italian" to "it",
        "lithuanian" to "lt",
        "malay" to "ms",
        "bulgarian" to "bg",
        "welsh" to "cy",
        "indonesian" to "id",
        "telugu" to "te",
        "romanian" to "ro"
    )
    val languageMap2 = mapOf(
        "english" to "en-US",
        "french" to "fr-FR",
        "german" to "de-DE",
        "spanish" to "es-ES",
        "chinese" to "zh-CN",
        "japanese" to "ja-JP",
        "tamil" to "ta-IN",
        "croatian" to "hr-HR",
        "korean" to "ko-KR",
        "marathi" to "mr-IN",
        "russian" to "ru-RU",
        "hungarian" to "hu-HU",
        "swahili" to "sw-KE",
        "thai" to "th-TH",
        "urdu" to "ur-PK",
        "norwegian" to "nb-NO",
        "danish" to "da-DK",
        "turkish" to "tr-TR",
        "estonian" to "et-EE",
        "portuguese" to "pt-PT",
        "vietnamese" to "vi-VN",
        "albanian" to "sq-AL",
        "swedish" to "sv-SE",
        "arabic" to "ar",
        "bengali" to "bn-BD",
        "gujarati" to "gu-IN",
        "kannada" to "kn-IN",
        "greek" to "el-GR",
        "hindi" to "hi-IN",
        "hebrew" to "he-IL",
        "finnish" to "fi-FI",
        "ukrainian" to "uk-UA",
        "dutch" to "nl-NL",
        "latvian" to "lv-LV",
        "czech" to "cs-CZ",
        "icelandic" to "is-IS",
        "polish" to "pl-PL",
        "catalan" to "ca-ES",
        "slovak" to "sk-SK",
        "italian" to "it-IT",
        "lithuanian" to "lt-LT",
        "malay" to "ms-MY",
        "bulgarian" to "bg-BG",
        "welsh" to "cy-GB",
        "indonesian" to "id-ID",
        "telugu" to "te-IN",
        "romanian" to "ro-RO"
    )
    val languageNames = listOf(
        "english", "french", "german", "spanish", "chinese", "japanese", "tamil",
        "croatian", "korean", "marathi", "russian", "hungarian", "swahili", "thai",
        "urdu", "norwegian", "danish", "turkish", "estonian", "portuguese", "vietnamese",
        "albanian", "swedish", "arabic", "bengali", "gujarati", "kannada", "greek",
        "hindi", "hebrew", "finnish", "ukrainian", "dutch", "latvian", "czech",
        "icelandic", "polish", "catalan", "slovak", "italian", "lithuanian", "malay",
        "bulgarian", "welsh", "indonesian", "telugu", "romanian"
    )
    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNecessary()
        textToSpeech = TextToSpeech(this, this)
        speechRecognizerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val matches =
                        result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!matches.isNullOrEmpty()) {
                        if (LanguageVariable=="11") {
                            recognizedText = matches[0]
                        }
                        else {
                            var temp = matches[0]
                            otherLangToEng(temp) { text2 ->
                            recognizedText = text2
                            }
                        }
                        println("Recognized speech: ${recognizedText}")
                    }
                }
            }
        if (!doesFileExist(this,"LanguageName.txt") || !doesFileExist(this,"LanguageVariable.txt")) {
            overwriteFile(this,"LanguageName.txt","english")
            overwriteFile(this,"LanguageVariable.txt","11")
        }
        LanguageName = readFromFile(this,"LanguageName.txt")
        LanguageVariable = readFromFile(this,"LanguageVariable.txt")
        LanguageCode1 = getLanguageCode1(LanguageName).toString()
        LanguageCode2 = getLanguageCode2(LanguageName).toString()
        if (LanguageCode1!="null" && LanguageCode2!="null") {
            val options = TranslateLanguage.fromLanguageTag(LanguageCode1)?.let {
                TranslatorOptions.Builder()
                    .setSourceLanguage(it)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
            }
            sharedPreferencesHelper = SharedPreferencesHelper(this)
            tasks.addAll(sharedPreferencesHelper.getTasks())
            Translatorr = options?.let { Translation.getClient(it) }!!
            Translatorr.downloadModelIfNeeded()
                .addOnSuccessListener {
                    Log.i("Success", "OnSuccessListener")
                }
            val options1 = TranslateLanguage.fromLanguageTag(LanguageCode1)?.let {
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(it)
                    .build()
            }

            Translatorrr = options1?.let { Translation.getClient(it) }!!
            Translatorrr.downloadModelIfNeeded()

        }
        else {
            LanguageCode1 = "en"
            LanguageCode2 = "en-US"
        }
        val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        registerReceiver(smsReceiver, filter)
        setContent {
            MyApp()
        }
    }
    @SuppressLint("Range")
    suspend fun getContactName(phoneNumber: String): String? {
        val contentResolver = contentResolver
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)

        var contactName: String? = null
        cursor?.use {
            if (it.moveToFirst()) {
                contactName = it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        }
        return contactName
    }
    fun checkServerStatus(onSuccess: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.imageService.checkServerStatus()
                withContext(Dispatchers.Main) {
                    println("Server status: $response")
                    onSuccess(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    println("Failed to connect to the server: ${e.message}")
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.P)
    private fun requestPermissionsIfNecessary() {
        val permissionsToRequest = mutableListOf<String>()
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    private fun getLanguageCode1(languageName: String): String? {
        return languageMap1[languageName]
    }

    fun fetchAndSpeakWeather(city: String) {
        val apiKey = openweatherapi
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val openWeatherMapApi = retrofit.create(RetrofitClient.OpenWeatherMapApi::class.java)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = openWeatherMapApi.getCurrentWeather(city, apiKey)
                if (response.isSuccessful) {
                    val weatherData = response.body()
                    weatherData?.let {
                        val temp = it.main.temp
                        val description = it.weather[0].description
                        println(description)
                        val weatherInfo =
                            "The weather in $city is currently $description with a temperature of $temp degrees Celsius."
                    performTextToSpeech(weatherInfo)
                    println(weatherInfo)
                    }
                } else {
                    Log.e("WeatherApp", "API request failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("WeatherApp", "Error fetching weather:${e.message}")
            }
        }
    }
    fun requestImage(callback: (ByteArray) -> Unit) {
        val imageService = RetrofitInstance.imageService
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Response<ResponseBody> = imageService.requestImage().execute()

                if (response.isSuccessful) {
                    println(response.code())
                    val imageBytes = response.body()?.bytes()
                    if (imageBytes != null) {
                        Log.d("ImageReceiver", "Received image bytes: ${imageBytes.size}")
                        Log.d("ImageReceiver", "Received image data: ${imageBytes.contentToString()}")
                    }
                    else {
                        Log.e("Failed", "Request image failed ")
                    }
                    if (imageBytes != null) {
                        withContext(Dispatchers.Main) {
                            callback(imageBytes)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            println("Error ${response.code()}")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        println("Error: ${response.code()} ${response.message()}")
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    println("Network error: ${e.message}")
                }
            }
        }
    }
    suspend fun handleSpeechInput(input: String) {
        Log.d("MainActivity", "Speech input: $input")
        when (listeningMode) {
            ListeningMode.COMMAND -> {
                when {
                    input.equals("read all task", ignoreCase = true) -> {
                        if (tasks.isNotEmpty()) {
                            val listText = tasks.mapIndexed { index, task -> "${index + 1}. $task" }.joinToString(separator = ", ")
                            Log.d("MainActivity", "Reading tasks: $listText")
                            performTextToSpeech("These are your tasks: $listText")
                        } else {
                            performTextToSpeech("No tasks available.")
                        }
                    }
                    input.equals("add task", ignoreCase = true) -> {
                        listeningMode = ListeningMode.ADD_TASK
                        Log.d("MainActivity", "Switching to ADD_TASK mode")
                        performTextToSpeech("What task would you like to add?")
                    }
                    input.startsWith("delete task", ignoreCase = true) -> {
                        handleDeleteTaskCommand(input.removePrefix("delete task").trim())
                    }
                    input.startsWith("read task", ignoreCase = true) -> {
                        handleReadTaskCommand(input.removePrefix("read task").trim())
                    }
                    input.equals("delete all tasks", ignoreCase = true) -> {
                        tasks.clear()
                        sharedPreferencesHelper.clearTasks()
                        Log.d("MainActivity", "All tasks deleted")
                        performTextToSpeech("All tasks have been deleted.")
                    }
                }
            }
            ListeningMode.ADD_TASK -> {
                val task = input.trim()
                if (task.isNotBlank()) {
                    tasks.add(task)
                    Log.d("MainActivity", "Task added: $task")
                    Toast.makeText(this, "Task added: $task", Toast.LENGTH_SHORT).show()
                    listeningMode = ListeningMode.COMMAND
                    performTextToSpeech("Your task has been added successfully.")
                    sharedPreferencesHelper.saveTasks(tasks)
                } else {
                    listeningMode = ListeningMode.COMMAND
                    Log.d("MainActivity", "No task specified")
                    Toast.makeText(this, "No task specified", Toast.LENGTH_SHORT).show()
                    performTextToSpeech("No task specified")
                }
            }
        }
    }
    suspend fun saveNewContact(context: Context, displayName: String, phoneNumber: String, email: String? = null) {
        val permissions = arrayOf(android.Manifest.permission.WRITE_CONTACTS, android.Manifest.permission.READ_CONTACTS)
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )
            if (email != null) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                        .build()
                )
            }
            try {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                performTextToSpeech("Contact added successfully.")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            ActivityCompat.requestPermissions(context as Activity, permissions, 1)
        }
    }
    suspend fun handleReadTaskCommand(taskIdentifier: String) {
        val index = taskIdentifier.toIntOrNull()
        if (index != null && index in 1..tasks.size) {
            val task = tasks[index - 1]
            Log.d("MainActivity", "Reading task $index: $task")
            performTextToSpeech("Task $index is $task")
        } else {
            val task = tasks.find { it.equals(taskIdentifier, ignoreCase = true) }
            if (task != null) {
                val taskIndex = tasks.indexOf(task) + 1
                Log.d("MainActivity", "Reading task '$taskIdentifier': $task")
                performTextToSpeech("Task $taskIndex is $task")
            } else {
                performTextToSpeech("Invalid task number or name.")
            }
        }
    }
    private suspend fun handleDeleteTaskCommand(taskIdentifier: String) {
        val index = taskIdentifier.toIntOrNull()
        if (index != null && index in 1..tasks.size) {
            tasks.removeAt(index - 1)
            sharedPreferencesHelper.saveTasks(tasks)
            Log.d("MainActivity", "Task $index deleted")
            performTextToSpeech("Task $index has been deleted.")
        } else {
            val task = tasks.find { it.equals(taskIdentifier, ignoreCase = true) }
            if (task != null) {
                tasks.remove(task)
                sharedPreferencesHelper.saveTasks(tasks)
                Log.d("MainActivity", "Task '$taskIdentifier' deleted")
                performTextToSpeech("Task '$taskIdentifier' has been deleted.")
            } else {
                performTextToSpeech("Invalid task number or name.")
            }
        }
    }
    private fun getLanguageCode2(languageName: String): String? {
        return languageMap2[languageName]
    }
    fun readFromFile(context: Context, fileName: String): String {
        return context.openFileInput(fileName).use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                reader.readText().trim()
            }
        }
    }
    fun overwriteFile(context: Context, fileName: String, content: String) {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use { outputStream ->
            outputStream.write(content.toByteArray())
        }
    }
    fun doesFileExist(context: Context, fileName: String): Boolean {
        val file = context.getFileStreamPath(fileName)
        return file.exists()
    }
    fun engToOtherLang(text: String,onResult: (String?) -> Unit) {
        Translatorrr.translate(text)
            .addOnSuccessListener { translatedText ->
                onResult(translatedText)
                println(translatedText)
            }
            .addOnFailureListener { exception ->
                Log.e("Translation","Error: $exception")
                onResult("$exception")
            }
    }
    fun otherLangToEng(text:String,onResult: (String?) -> Unit) {
        Translatorr.translate(text)
            .addOnSuccessListener { translatedText ->
                onResult(translatedText)
            }
            .addOnFailureListener { exception ->
                Log.e("Translation","Error: $exception")
                onResult("$exception")
            }
    }
    suspend fun uploadImages(context: Context, textData: String, imagePaths: List<String>): String {
        val retrofitClient = RetrofitClient.instance
        val parts = mutableListOf<MultipartBody.Part>()
        val textPart = MultipartBody.Part.createFormData("textData", textData)
        parts.add(textPart)
        imagePaths.forEachIndexed { index, imagePath ->
            val file = File(imagePath)
            val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image_$index", file.name, requestBody)
            parts.add(imagePart)
        }
        val call = retrofitClient.uploadImages(parts)
        return suspendCancellableCoroutine { continuation ->
            call.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful && response.body() != null) {
                        val responseBody = response.body()?.string()
                        continuation.resume(responseBody ?: "Unknown response")
                    } else {
                        val errorMessage = response.message()
                        Log.d("UploadImages", "onResponse: $errorMessage")
                        continuation.resumeWithException(Exception("Upload failed: $errorMessage"))
                    }
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Log.d("UploadImages", "onFailure: ${t.message} ")
                    continuation.resumeWithException(t)
                }
            })
            continuation.invokeOnCancellation {
                call.cancel()
            }
        }
    }
    suspend fun uploadImage(path: String,onSuccess: (String) -> Unit): String {
        val file = File(path)
        val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
        RetrofitClient.instance.uploadImage(body).enqueue(object : Callback<UploadResponse> {
            override fun onResponse(call: Call<UploadResponse>, response: Response<UploadResponse>) {
                if (response.isSuccessful) {
                    taskId = response.body()?.task_id.toString()
                    taskId.let {
                        Toast.makeText(this@MainActivity, "Upload successful. Task ID: $taskId", Toast.LENGTH_LONG).show()
                        onSuccess(taskId)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Upload failed", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<UploadResponse>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Upload error: ${t.message}", Toast.LENGTH_LONG).show()
                println("Error ${t.message}")
            }
        })
        delay(4000)
        return taskId
    }
    fun checkStatus(taskId: String,onSuccess: (String) -> Unit): String {
        var res = ""
        RetrofitClient.instance.getResult(taskId).enqueue(object : Callback<ResultResponse> {
            override fun onResponse(call: Call<ResultResponse>, response: Response<ResultResponse>) {
                if (response.isSuccessful) {
                    val result = response.body()
                    result?.let {
                        if (it.status == "completed") {
                            Toast.makeText(this@MainActivity, "Result: ${it.result}", Toast.LENGTH_LONG).show()
                            println(it.result)
                            res = it.result.toString()
                          onSuccess(it.result.toString())
                        } else {
                            println(it.result)
                            Toast.makeText(this@MainActivity, "Status: ${it.status}", Toast.LENGTH_LONG).show()
                            res = it.result.toString()
                            onSuccess("failure")
                        }
                    }
                } else {
                    println("Failed to get status")
                    Toast.makeText(this@MainActivity, "Failed to get status", Toast.LENGTH_LONG).show()
                    onSuccess("failure")
                    res = "Failed"
                }
            }
            override fun onFailure(call: Call<ResultResponse>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_LONG).show()
                println("${t.message}")
                res = t.message.toString()
            }
        })
        return res
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array <String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (!allPermissionsGranted) {
                Log.d("Permissions", "onRequestPermissionsResult:Not granted ")
            }
        }
    }
    override fun onInit(status: Int) {
        if (LanguageVariable=="11") {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.forLanguageTag("en")
                val cc = CoroutineScope(Dispatchers.Main)
                cc.launch {
                    announceTimeAndBattery(this@MainActivity)
                }
            }
            else {
                Toast.makeText(this,"Unable to load Text To speech",Toast.LENGTH_LONG).show()
            }
        }
        else {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.forLanguageTag(LanguageCode1)
                val cc = CoroutineScope(Dispatchers.Main)
                cc.launch {
                    announceTimeAndBattery(this@MainActivity)
                }
            }
            else {
                Toast.makeText(this,"Unable to load Text To speech",Toast.LENGTH_LONG).show()
            }
        }
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.setSpeechRate(0.7F)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
        Translatorr.close()
        Translatorrr.close()
        unregisterReceiver(smsReceiver)
    }
    fun captureAndSaveImage(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        onImageCaptured: (String) -> Unit,
        onError: (ImageCaptureException) -> Unit,
        file: File
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageCapture
                )
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onImageCaptured(file.absolutePath)
                            cameraProvider.unbindAll()
                        }
                        override fun onError(exception: ImageCaptureException) {
                            onError(exception)


                            cameraProvider.unbindAll()
                        }
                    }
                )
            } catch (exc: Exception) {
                println(exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    suspend fun createFile(context: Context,fileName: String): File? {
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            val appMediaDir = File(it, context.resources.getString(R.string.app_name)).apply { mkdirs() }
            if (appMediaDir.exists()) appMediaDir else null
        }
        if (mediaDir == null) {
            Log.e("main", "Failed to create media directory")
            return null
        }
        delay(6000)
        val file = File(mediaDir, "$fileName.jpg")
        Log.d("main", "createFile: $file")
        withContext(Dispatchers.IO) {
            try {
                if (file.createNewFile()) {
                    Log.d("main", "File created: $file")
                } else {
                    Log.d("main", "File already exists: $file")
                }
            } catch (e: IOException) {
                Log.e("main", "File creation failed", e)
            }
        }
        return file
    }
    suspend fun performTextToSpeech(text: String) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                println(LanguageVariable+"tts")
                println(LanguageVariable.javaClass.name )
                if (LanguageVariable=="11") {
                    println("iff")
                    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
                }
                else {
                    println("elsee")
                    engToOtherLang(text) { text1 ->
                        Log.d("Translated result", "performTextToSpeech: $text1")
                        textToSpeech.speak(text1, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
                    }
                }
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        cont.resume(Unit)
                    }
                    override fun onError(utteranceId: String?) {
                        cont.resume(Unit)
                    }
                })
            }
        }
    }

    suspend fun performSpeechRecognition() {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    if (LanguageVariable=="11") {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    }
                    else {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LanguageCode2)
                    }
                }
                speechRecognizerLauncher.launch(intent)
                cont.resume(Unit)
            }
        }
    }
    fun checkSmsPermission(): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }
    fun requestSmsPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
    }
    fun sendSms(phoneNumber: String, message: String,onSuccess: (String) -> Unit) {
        if (checkSmsPermission()) {
            try {
                val smsManager: SmsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                println("SMS sent successfully")
                onSuccess("SMS sent Successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                println("Failed to send SMS: ${e.message}")
                onSuccess("Error occurred")
            }
        }
        else {
            println("SMS permission not granted")
            requestSmsPermission()
            onSuccess("Please give permissions to send sms")
        }
    }
    suspend fun getPhoneNumberByName(context: Context, name: String): String? {
        return withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val cursor: Cursor? = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
                arrayOf(name),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (columnIndex >= 0) {
                        return@withContext it.getString(columnIndex)
                    }
                }
            }
            return@withContext null
        }
    }
    suspend fun makePhoneCall(context: Context, phoneNumber: String) {
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(intent)
            }
        }
    }
     val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = geminiapikey,
    )
    suspend fun askAQuestion(text: String) {
        val response = generativeModel.generateContent(text)
        val responseText = response.text
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                try {
                    if (responseText != null) {

                        aiResponseText = responseText


                        Log.d("askAQuestion", "TTS: $responseText")
                    } else {
                        aiResponseText = "Error"
                        Log.d("askAQuestion", "TTS: Error")
                    }
                    cont.resume(Unit)
                } catch (e: Exception) {
                    Log.e("askAQuestion", "Exception: ${e.message}")
                    cont.resume(Unit)
                }
            }
        }
    }
    fun getCurrentLocation(
        fusedLocationClient: FusedLocationProviderClient,
        onLocationReceived: (String) -> Unit
    ) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLocation = "${location.latitude},${location.longitude}"
                    onLocationReceived(currentLocation)
                }
            }
        } catch (e: SecurityException) {
            println(e)
        }
    }
  fun deleteFileIfExists(context: Context, fileName: String): Boolean {
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, context.resources.getString(R.string.app_name))
        }
      if (mediaDir != null && mediaDir.exists()) {
            val file = File(mediaDir, fileName)
            return if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d("main", "File deleted: $file")
                } else {
                    Log.e("main", "Failed to delete file: $file")
                }
                deleted
            } else {
                Log.d("main", "File does not exist: $file")
                false
            }
        } else {
            Log.e("main", "Media directory does not exist")
            return false
        }
    }
    suspend fun detectFace(imagePath: String): Boolean {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        var faceDetected = false
        detector.process(image)
            .addOnSuccessListener { faces ->
                println(faces)
                faceDetected = faces.isNotEmpty()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
        delay(3000)
        Log.e("Face", "detectFace: No Face $faceDetected", )
        return faceDetected
    }
    suspend fun detectFace(bitmap: Bitmap): Boolean {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        var faceDetected = false
        detector.process(image)
            .addOnSuccessListener { faces ->
                println(faces)
                faceDetected = faces.isNotEmpty()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
        delay(4000)
        Log.e("Face", "detectFace: No Face $faceDetected", )
        return faceDetected
    }
    suspend fun describeTheScene(imagee:Bitmap): String? {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = geminiapikey
        )
        val inputContent = content() {
            image(imagee)
            text("Describe the scene for a blind person. Don't include any punctuation marks other than full stops and commas")
        }
        val response = generativeModel.generateContent(inputContent)
        return response.text
    }
    suspend fun captureImage(imagee: Bitmap,prompt: String): String? {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = geminiapikey
        )
        val inputContent = content() {
            image(imagee)
            text("$prompt. Don't include any punctuation marks other than full stops and commas")
        }
        val response = generativeModel.generateContent(inputContent)
        return response.text
    }
    suspend fun announceTimeAndBattery(context: Context) {
        val currentTime = Calendar.getInstance().time
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault()) // Use 'a' for AM/PM
        val formattedTime = formatter.format(currentTime)
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null,ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level == -1 || scale == -1) {
            50 // Return 50 if battery information is unavailable
        } else {
            (level * 100 / scale.toFloat()).toInt()
        }
        val announcement = "The current time is $formattedTime. The battery level is $percentage percent."
        performTextToSpeech(announcement)
    }
    suspend fun processSpokenTime(spokenText: String) {
        Log.d("VoiceAlarm", "Spoken Text: $spokenText")
        val parts = spokenText.split(" ")
        if (parts.size == 2) {
            val timeParts = parts[0].split(":")
            if (timeParts.size == 2) {
                try {
                    val hour = timeParts[0].toInt()
                    val minute = timeParts[1].toInt()
                    val isAM = parts[1].lowercase(Locale.ROOT) == "am"
                    alarmTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, if (isAM) hour else hour + 12)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                    }
                    alarmTime?.let {
                        setAlarm(it)
                        textToSpeech.speak(
                            "Alarm set for ${
                                SimpleDateFormat(
                                    "hh:mm a",
                                    Locale.getDefault()
                                ).format(it.time)
                            }",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            null
                        )
                    }
                } catch (e: NumberFormatException) {
                    performTextToSpeech("Sorry, I didn't understand the time. Please try again.")
                }
            } else {
                performTextToSpeech("Please say the time in the format of 'hours:minutes AM/PM.")
            }
        } else {
            performTextToSpeech("Please say the time in the format of 'hours:minutes AM/PM.")
        }
    }
    fun convertByteArrayToBitmap(byteArray: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    fun playYouTubeVideo(context: Context, videoId: String) {
        val videoUri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
        val intent = Intent(Intent.ACTION_VIEW, videoUri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    fun saveBitmapToExternalStorage(bitmap: Bitmap, fileName: String): String? {
        val externalStorageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val file = File(externalStorageDirectory, "$fileName.png")
        return try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
    suspend fun howMuchMoney(imagee:Bitmap) : String? {

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = geminiapikey
        )
        val inputContent = content() {
            image(imagee)
            text("Tell how much money is this.If possible include currency name. Don't include any punctuation marks other than full stops and commas")
        }
        val response = generativeModel.generateContent(inputContent)
        return response.text
    }
    @SuppressLint("ScheduleExactAlarm")
    private fun setAlarm(time: Calendar) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.timeInMillis,
            pendingIntent
        )
        val formattedTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(time.time)
        Log.d("VoiceAlarm", "Alarm set for: $formattedTime")
        Toast.makeText(this, "Alarm sector $formattedTime", Toast.LENGTH_SHORT).show()
    }
    suspend fun readUnreadSms()  {
        val smsUri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val selection = "${Telephony.Sms.Inbox.READ} = 0"
        val cursor: Cursor? = contentResolver.query(
            smsUri,
            arrayOf(Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.BODY),
            selection,
            null,
            Telephony.Sms.Inbox.DEFAULT_SORT_ORDER
        )
        cursor?.use {
            val smsList = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY))
                var contact = getContactName(address)
                if (contact!= null) {
                    smsList.add("From: $contact. Message: $body\n")
                }
                else {
                    smsList.add("From: $address. Message: $body\n")
                }
            }
            var text = if (smsList.isNotEmpty()) smsList.toString() else "No unread SMS found"
           smsList.forEach {
              performTextToSpeech(it)
                println(it)
            }
        }
    }
     val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val bundle = intent?.extras
            if (bundle != null) {
                val pdus = bundle["pdus"] as Array<*>
                for (pdu in pdus) {
                    val message = SmsMessage.createFromPdu(pdu as ByteArray)
                    val sender = message.displayOriginatingAddress
                    val content = message.messageBody
                    println("kumsr")
                    val cc = CoroutineScope(Dispatchers.Main)
                    cc.launch {
                         var contactname = getContactName(sender)
                        if (contactname!=null) {
                            performTextToSpeech("SMS received from: $contactname. Content: $content")
                        }
                        else {
                            performTextToSpeech("SMS received from: $sender. Content: $content")
                        }
                    }
                }
            }
        }
    }

}
@Composable
fun MyApp() {
    var phoneNumber by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current as MainActivity
    var glassServer = "Server is running"
    var glassStatus = false
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(LocalContext.current)
    Box(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = { coroutineScope.launch {
                context.checkServerStatus { s ->
                    println("server Status $s")
                    if (s==glassServer) {
                        glassStatus = true
                    }
                    println(glassStatus)
                }
                println(context.LanguageVariable)
                context.performTextToSpeech("Say Something")
                context.performSpeechRecognition()
                delay(4000)
                Log.d("main", "MyApp: ${context.recognizedText}")
                context.recognizedText?.let {
                    Log.d("main", "MyApp: ${context.recognizedText}2")
                    context.performTextToSpeech(it)
                }
                if (context.recognizedText?.lowercase() in listOf("answer my question","answer question","question answer","question my answer","my question answer")) {
                    context.performSpeechRecognition()
                    delay(5000)
                    Log.d("Gemini", "MyApp: ${context.recognizedText}")
                    context.recognizedText?.let { context.askAQuestion(it) }
                    delay(2000)
                    context.performTextToSpeech(context.aiResponseText)
                }
                else if (context.recognizedText?.lowercase() in listOf("make a call","do a call","call","call someone","call do","do call","make call")) {
                    context.performTextToSpeech("Whom do you want to call?")
                    context.performSpeechRecognition()
                    delay(5000)
                    try {
                        phoneNumber = context.getPhoneNumberByName(context, context.recognizedText!!)
                        phoneNumber?.let {
                            context.makePhoneCall(context, it)
                        }
                    }
                    catch (e : Exception) {
                       context.performTextToSpeech("Sorry Making Phone call Failed due to $e")
                    }
                }
                else if (context.recognizedText?.lowercase() in listOf("send a message","send message","message","do a message","message someone","write a message","do message","make message","make a message")) {
                    context.performTextToSpeech("Whom do you want to message")
                    context.performSpeechRecognition()
                    delay(3000)
                    try {
                        phoneNumber = context.getPhoneNumberByName(context,context.recognizedText!!)
                        Log.d("Mainnn", "MyApp: $phoneNumber")
                        context.performTextToSpeech("What message do you want to send")
                        context.performSpeechRecognition()
                        delay(3000)
                        Log.d("Mainnn", "MyApp: $phoneNumber")
                        Log.d("Mainnn", "MyApp: $phoneNumber")
                        var text1 = ""
                        context.performTextToSpeech("Sending")
                        phoneNumber?.let { context.sendSms(it,context.recognizedText!!) { text ->
                            text1 = text
                           }
                        }
                        context.performTextToSpeech(text1)
                    }
                    catch (e : Exception) {
                        context.performTextToSpeech("Sorry Making Phone call Failed due to $e")
                    }
                }
                else if (context.recognizedText?.lowercase() in listOf("describe the scene","to describe the scene","tell about the scene","describe the view","to describe the view","describe scene","describe view","tell view","tell scene",)) {
                    if (glassStatus) {
                        var img : Bitmap?
                        context.requestImage { result ->
                            img = context.convertByteArrayToBitmap(result)
                            println(img)
                            val cc = CoroutineScope(Dispatchers.Main)
                            cc.launch {
                                delay(2000)
                                var response = img?.let { context.describeTheScene(it) }
                                delay(3000)
                                if (response != null) {
                                    context.performTextToSpeech(response)
                                }
                            }
                        }
                    }
                    else {
                        var a = ""
                        val newfile = context.createFile(context, "capturedimage")
                        Log.d("File", "MyApp: File $newfile")
                        delay(1000)
                        if (newfile != null) {
                            context.captureAndSaveImage(
                                context, context, onImageCaptured = { filePath ->
                                    println("Sucessful $filePath")
                                    a = filePath
                                },
                                {
                                    println("error")
                                }, file = newfile
                            )
                        } else {
                            Log.e("Error","Error")
                        }
                        delay(2000)
                        val bitmap = BitmapFactory.decodeFile(a)
                        val response = context.describeTheScene(bitmap)
                        if (response != null) {
                            context.performTextToSpeech(response)
                        }
                        context.deleteFileIfExists(context, "capturedimage.jpg")
                        bitmap.recycle()
                    }
                }
                else if (context.recognizedText?.lowercase() in listOf("how much money","how money","money","how much currency","how money")) {
                    if (glassStatus) {
                        var img : Bitmap?
                        context.requestImage { result ->
                            img = context.convertByteArrayToBitmap(result)
                            println(img)
                            val cc = CoroutineScope(Dispatchers.Main)
                            cc.launch {
                                delay(2000)
                                var response = img?.let { context.howMuchMoney(it) }
                                delay(3000)
                                if (response != null) {
                                    context.performTextToSpeech(response)
                                }
                            }
                        }
                    }
                    else {
                        var a = ""
                        val newfile = context.createFile(context, "capturedimage")
                        Log.d("File", "MyApp: File $newfile")
                        delay(1000)
                        if (newfile != null) {
                            context.captureAndSaveImage(
                                context, context, onImageCaptured = { filePath ->
                                    println("Sucessful $filePath")
                                    a = filePath
                                },
                                {
                                    println("error")
                                }, file = newfile
                            )
                        } else {
                            Log.e("Error","Error")
                        }
                        delay(2000)
                        val bitmap = BitmapFactory.decodeFile(a)
                        val response = context.describeTheScene(bitmap)
                        if (response != null) {
                            context.performTextToSpeech(response)
                        }
                        context.deleteFileIfExists(context, "capturedimage.jpg")
                        bitmap.recycle()
                    }
                }
                else if (context.recognizedText?.lowercase() in listOf("capture image","capture photo","take photo","take image","photo take","image take") ) {
                    context.performTextToSpeech("What is the question you want to ask?")
                    context.performSpeechRecognition()
                    delay(3000)
                    println(context.recognizedText)
                    val prompt = context.recognizedText
                    println(prompt)
                    if (glassStatus) {
                        var img : Bitmap?
                        context.requestImage { result ->
                            img = context.convertByteArrayToBitmap(result)
                            println(img)
                            val cc = CoroutineScope(Dispatchers.Main)
                            cc.launch {
                                delay(2000)
                               var response = prompt?.let { img?.let { it1 ->
                                   context.captureImage(
                                       it1, it)
                                   }
                               }
                                delay(3000)
                                if (response != null) {
                                    context.performTextToSpeech(response)
                                }
                            }
                        }
                    }
                    var a = ""
                    val newfile = context.createFile(context,"capturedimage")
                    Log.d("hello", "MyApp: hellooo $newfile")
                    delay(1000)
                    if (newfile != null) {
                        context.captureAndSaveImage(context,context, onImageCaptured = { filePath ->
                            println("Sucessful $filePath" )
                            a = filePath
                        },
                            {
                                println("error")
                            }, file = newfile
                        )
                    }
                    else {
                        Log.e("Error","Error")
                    }
                    delay(2000)
                    val bitmap = BitmapFactory.decodeFile(a)
                    println(bitmap)
                    println(prompt)
                    val response = context.recognizedText?.let { context.captureImage(bitmap, it) }
                    if (response != null) {
                        context.performTextToSpeech(response)
                    }
                    context.deleteFileIfExists(context,"capturedimage.jpg")
                    bitmap.recycle()
                }
                else if(context.recognizedText?.lowercase()in listOf("remember this face","remember face","face remember","face this remember","memory this face","memory face","face memory","face this memory")) {
                    if (glassStatus) {
                        context.requestImage { bytes ->
                            val img = context.convertByteArrayToBitmap(bytes)
                            val cc = CoroutineScope(Dispatchers.Main)
                            cc.launch {
                                if (img?.let { context.detectFace(it) } == true) {
                                    context.performTextToSpeech("Face detected. What is the name of this person?.")
                                    context.performSpeechRecognition()
                                    delay(5000)
                                    var name = context.recognizedText
                                    println("name is $name")
                                    var imagePaths = mutableListOf<String>()
                                    for (i in 1..3) {
                                        context.requestImage {
                                            bytes
                                            var bitmap = context.convertByteArrayToBitmap(bytes)
                                            if (bitmap != null) {
                                                var imgpath = context.saveBitmapToExternalStorage(
                                                    bitmap,
                                                    "$name" + "$i"
                                                )
                                                if (imgpath != null) {
                                                    imagePaths.add(imgpath)
                                                }
                                            }
                                        }
                                    }
                                        context.performTextToSpeech("Images captured Succesfully. You can lower your camera.What note do you want to add to this person?.")
                                        context.performSpeechRecognition()
                                        delay(5000)
                                        var test = ""
                                        test = context.uploadImages(
                                            context,
                                            "['rememberface','$name','${context.recognizedText}']",
                                            imagePaths
                                        )
                                        delay(3000)
                                        context.performTextToSpeech(test)
                                }
                                else {
                                    context.performTextToSpeech("Face Not Detected")
                                }
                            }
                        }
                    }
                    else {
                        val newfile = context.createFile(context, "capturedimage")
                        delay(1000)
                        var a = ""
                        if (newfile != null) {

                            context.captureAndSaveImage(
                                context, context, onImageCaptured = { filePath ->
                                    println("Sucessful $filePath")
                                    a = filePath
                                },
                                {
                                    println("error")
                                }, file = newfile
                            )
                        } else {
                            Log.e("Error","Error")
                        }
                        delay(3000)
                        var imagespaths = mutableListOf<String>()
                        if (context.detectFace(a)) {
                            context.performTextToSpeech("Face detected. What is the name of this person?.")
                            context.performSpeechRecognition()
                            delay(5000)
                            var name = context.recognizedText
                            println("name is $name")
                            for (i in 1..3) {
                                val newfile = context.createFile(context, "$name" + "$i")
                                delay(1000)
                                var a = ""
                                if (newfile != null) {
                                    context.captureAndSaveImage(
                                        context, context, onImageCaptured = { filePath ->
                                            println("Sucessful $filePath")
                                            a = filePath
                                        },
                                        {
                                            println("error")
                                        }, file = newfile
                                    )
                                } else {
                                    Log.e("Error","Error")
                                }
                                delay(3000)
                                imagespaths.add(a)
                                println("Image path $i : $a")
                            }
                            context.performTextToSpeech("Images captured Succesfully. You can lower your camera.What note do you want to add to this person?.")
                            context.performSpeechRecognition()
                            delay(5000)
                            var test = ""
                            test = context.uploadImages(
                                context,
                                "['rememberface','$name','${context.recognizedText}']",
                                imagespaths
                            )
                            delay(3000)
                            context.performTextToSpeech(test)
                        } else {
                            context.performTextToSpeech("Face Not Detected")
                        }
                        delay(2000)
                        context.deleteFileIfExists(context, "capturedimage.jpg")
                    }
                }
                else if(context.recognizedText?.lowercase() in listOf("check for faces","check faces","faces check","check for face","check face","face check","look for face","look for faces","look faces")) {
                    if (glassStatus) {
                        context.requestImage { bytes ->
                            var bitmap = context.convertByteArrayToBitmap(bytes)
                            val cc = CoroutineScope(Dispatchers.Main)
                            cc.launch {
                                if (bitmap?.let { context.detectFace(it) } == true) {
                                    context.performTextToSpeech("Face detected. Processing. Kindly wait. This may take a while")
                                    var img = bitmap?.let {
                                        var path = context.saveBitmapToExternalStorage(
                                            it,
                                            "capturedimage.jpg"
                                        )
                                        var h = ""
                                        if (path != null) {
                                            context.uploadImage(path) { taskId ->

                                                h = taskId
                                            }
                                        }
                                        var resu = ""
                                        delay(25000)
                                        context.checkStatus(h) { res ->
                                            resu = res
                                        }
                                        delay(2000)
                                        if (resu.lowercase() == "failure") {
                                            context.performTextToSpeech("Sorry unable to access the server properly")
                                        }
                                        else {
                                            context.performTextToSpeech(resu)
                                        }
                                    }
                                }
                                else {
                                    context.performTextToSpeech("No face has been detected in the image")
                                }
                            }
                        }
                    }
                    else {
                        val newfile = context.createFile(context, "capturedimage")
                        delay(1000)
                        var a = ""
                        if (newfile != null) {
                            context.captureAndSaveImage(
                                context, context, onImageCaptured = { filePath ->
                                    println("Sucessful $filePath")
                                    a = filePath
                                },
                                {
                                    println("error")
                                }, file = newfile
                            )
                        } else {
                            Log.e("Error","Error")
                        }
                        delay(3000)
                        var imagespaths = mutableListOf<String>()
                        imagespaths.add(a)
                        if (context.detectFace(a)) {
                            context.performTextToSpeech("Face detected. Processing. Kindly wait. This may take a while")
                            var h = ""
                            context.uploadImage(a) { taskId ->
                                h = taskId
                            }
                            var resu = ""
                            delay(25000)
                            context.checkStatus(h) { res ->
                                resu = res
                            }
                            delay(2000)
                            if (resu.lowercase() == "failure") {
                                context.performTextToSpeech("Sorry unable to access the server properly")
                            } else {
                                context.performTextToSpeech(resu)
                            }
                            println(context.taskId)
                        } else {
                            context.performTextToSpeech("No face has been detected in the image")
                        }
                        delay(10000)
                    }
                }
                else if(context.recognizedText?.lowercase()=="set an alarm") {
                    context.performTextToSpeech("Please say the time in the format of hours minutes AM or PM.")
                    context.performSpeechRecognition()
                    delay(4000)
                    context.processSpokenTime(context.recognizedText!!)
                }
                else if(context.recognizedText?.lowercase() in listOf("navigation","route","path")) {
                    var origin  = ""
                    var destination = ""
                    var directions = ""
                    val directionsService = GoogleDirectionsRetrofit.retrofit.create(DirectionsService::class.java)
                    val apiKey = context.gmapsapikey
                    val modes = listOf("driving", "walking", "bicycling", "transit")
                    var currentLocation = ""
                    var selectedMode = 0
                    context.performTextToSpeech("Do you want to navigate from your current location?. Say okay Or no.")
                    context.performSpeechRecognition()
                    delay(5000)
                    if (context.recognizedText?.lowercase()=="okay" || context.recognizedText?.lowercase()=="ok") {
                        context.getCurrentLocation(fusedLocationClient) { location ->
                            currentLocation = location
                            println(currentLocation)
                            origin = location
                        }
                    }
                    else if (context.recognizedText?.lowercase()=="no") {
                        context.performTextToSpeech("Tell me the Starting point from where you want to start.")
                        context.performSpeechRecognition()
                        delay(3000)
                        origin = context.recognizedText!!
                    }
                    else {
                        context.performTextToSpeech("Unable to recognize what you said. Taking your current location as the starting point.")
                        context.getCurrentLocation(fusedLocationClient) { location ->
                            currentLocation = location
                            println(currentLocation)
                            origin = location
                        }
                    }
                    context.performTextToSpeech("What is your destination?.")
                    context.performSpeechRecognition()
                    delay(4000)
                    destination = context.recognizedText!!
                    context.performTextToSpeech("What is the mode of transportation, you want to take?. Say Public Transport. or Four Wheeler. or Two wheeler. or walk")
                    context.performSpeechRecognition()
                    delay(5000)
                    if (context.recognizedText?.lowercase()=="public transport") {
                        selectedMode = 3
                    }
                    else if (context.recognizedText?.lowercase()=="four wheeler") {
                        selectedMode = 0
                    }
                    else if (context.recognizedText?.lowercase()=="two wheeler") {
                        selectedMode = 2
                    }
                    else if (context.recognizedText?.lowercase()=="walk") {
                        selectedMode = 1
                    }
                    else {
                        context.performTextToSpeech("Didnt hear what you said. Taking Public Transport as the default option")
                        selectedMode = 3
                    }
                    val response = directionsService.getDirections(origin, destination,modes[selectedMode] ,apiKey)
                    if (response.routes.isNotEmpty()) {
                        val steps = response.routes[0].legs[0].steps.joinToString("\n") { step ->
                            if (step.travel_mode == "TRANSIT") {
                                var transitDetails = step.transit_details
                                "Take ${transitDetails?.line?.name} (${transitDetails?.line?.short_name}) " +
                                        "from ${transitDetails?.departure_stop?.name} to ${transitDetails?.arrival_stop?.name}: " +
                                        "${step.distance.text}, ${step.duration.text}"
                            } else {
                                "${step.html_instructions}: ${step.distance.text}, ${step.duration.text}"
                            }
                        }
                        directions = steps
                    } else {
                        directions = "No directions found"
                    }
                    delay(5000)
                    if ("null" in directions) {
                        directions = directions.replace("null","bus")
                    }
                    println(directions)
                    println(response.routes)
                    println(response)
                    context.performTextToSpeech(directions)
                }
                else if(context.recognizedText?.lowercase() in listOf("emergency call","call emergency","emergency")) {
                    var currentLocation = ""
                    context.getCurrentLocation(fusedLocationClient) { location ->
                        currentLocation = location
                        println(currentLocation)
                    }
                    var text1 = ""
                    context.sendSms("112","This is an emergency generated message coming from a blind person. These are my coordinates : $currentLocation ") { text ->
                        text1 = text
                    }
                    context.performTextToSpeech("Calling 112")
                    delay(1000)
                    context.makePhoneCall(context,"112")
                    delay(2000)
                    context.performTextToSpeech(text1)
                }
                else if(context.recognizedText?.lowercase() in listOf("what can you do","what you can do","what you do","what do","do what","do what you")) {
                    context.performTextToSpeech("There are various features that i can provide you to make your day better.")
                    context.performTextToSpeech("If you need to know what is in front of you. Just tell me.   Describe the scene.    I'll provide you with the description of the scene in front of you.")
                    context.performTextToSpeech("If you need to capture a face and ask me to remember just tell me. Remember this face.")
                    context.performTextToSpeech("If you need to check whether a remembered face is present in front of you. just ask me. Check for faces.")
                    context.performTextToSpeech("If you want to ask a creative question with related to a scene ask me. capture image, After that I will ask you the question you want to ask me.")
                    context.performTextToSpeech("If you need to ask a creative question just tell.answer my question.")
                    context.performTextToSpeech("if you need to know how much money is in your hand, just ask me, How much money.")
                    context.performTextToSpeech("If you need to make an emergency call. Say. emergency call.")
                    context.performTextToSpeech("If you need to set an alarm. say. set an alarm.")
                    context.performTextToSpeech("If you need to play anything in youtube. Say. Youtube.")
                    context.performTextToSpeech("If you need to change language. say. change language.")
                    context.performTextToSpeech("If you need to make a call. say. make a call.")
                    context.performTextToSpeech("If you need to send an sms.say.send a message.")
                    context.performTextToSpeech("If you need to Get the route for someplace. Say. navigation")
                    context.performTextToSpeech("If you need to save a new contact. say. add a new contact.")
                    context.performTextToSpeech("If you need to get weather report.say. get weather report.")
                    context.performTextToSpeech("If you need to remember some task to do. say. to do list")
                    context.performTextToSpeech("If you need to read all unread sms messages. say. read all unread messages.")
                    context.performTextToSpeech("If you need to list out all features, just say. what can you do.")
                }
                else if (context.recognizedText?.lowercase() in listOf("change language","language change","language")) {
                    context.performTextToSpeech("What is the name of the language you want to change?")
                    context.performSpeechRecognition()
                    delay(6000)
                    if (context.recognizedText?.lowercase() in context.languageNames) {
                        context.overwriteFile(context,"LanguageName.txt", context.recognizedText!!.lowercase())
                        context.LanguageName = context.recognizedText?.lowercase()!!
                        if (context.recognizedText?.lowercase()=="english") {
                            context.overwriteFile(context,"LanguageVariable.txt","11")
                            context.LanguageVariable = "11"
                        }
                        else {
                            context.overwriteFile(context,"LanguageVariable.txt","0")
                            context.LanguageVariable = "0"
                        }
                        context.performTextToSpeech("Language Successfully Changed.Restart the appication to start using the app in new language. ")
                    }
                    else {
                        context.performTextToSpeech("Sorry. The language name you said is either invalid or not yet supported. Kindly try again.")
                    }
                }
                else if (context.recognizedText?.lowercase() in listOf("to do list","do to list","to do","do to")) {
                    context.performTextToSpeech("Say a command. To list out all commands say list command")
                    context.performSpeechRecognition()
                    delay(4000)
                    if (context.recognizedText?.lowercase()=="list command") {
                        context.performTextToSpeech("To read all tasks. Say. Read all task.")
                        context.performTextToSpeech("To read a specific task using index.Say. Read task,task number")
                        context.performTextToSpeech("To add a new task. Say. add task")
                        context.performTextToSpeech("To delete a task using index. Say. delete task, task number")
                        context.performTextToSpeech("To delete task using task name. say. delete task, task name")
                    }
                    else {
                        context.handleSpeechInput(context.recognizedText!!)
                        if (context.listeningMode==MainActivity.ListeningMode.ADD_TASK)  {
                            context.performTextToSpeech("Tell me the task you want to add.")
                            context.performSpeechRecognition()
                            delay(3000)
                            context.handleSpeechInput(context.recognizedText!!)
                        }
                    }
                }
                else if (context.recognizedText?.lowercase() in listOf("add a contact","add contact","contact add","add this contact")) {
                    context.performTextToSpeech("What is the name of the contact?")
                    context.performSpeechRecognition()
                    delay(4000)
                    var name = context.recognizedText
                    context.performTextToSpeech("Say the digits of the phone number continuously.")
                    context.performSpeechRecognition()
                    delay(10000)
                    var phno = context.recognizedText
                    context.performTextToSpeech("This is the phone number you said. $phno. This is the name you said. $name. do you want to save this contact?. Say okay or no.")
                    context.performSpeechRecognition()
                    delay(4000)
                    if (context.recognizedText?.lowercase()=="okay") {
                        if (phno != null) {
                            if (name != null) {
                                Log.d("Trace", "saveNewContact: -1 ")
                                context.saveNewContact(context,name,phno,null)
                            }
                        }
                    }
                    else {
                        context.performTextToSpeech("Saving a new contact cancelled. Kindly try again")
                    }
                }
                else if (context.recognizedText?.lowercase() in listOf("get weather report","get weather information","get weather data","get data weather","weather","get information weather")) {
                    context.getCurrentLocation(fusedLocationClient) { location ->
                        var Location = location
                        context.fetchAndSpeakWeather(Location)
                    }
                }
                else if (context.recognizedText?.lowercase()=="youtube") {
                    val repository = YouTubeRepository()
                    val viewModel = YouTubeViewModel(repository)
                    context.performTextToSpeech("Say What You want to play")
                    context.performSpeechRecognition()
                    delay(5000)
                    val query = context.recognizedText
                    val apiKey = context.youtubedfapikey
                  var youtubeItems = query?.let { viewModel.searchVideos(it, apiKey) }
                    println(youtubeItems)
                    if (youtubeItems != null) {
                       context.performTextToSpeech("These are the results fetched in from youtube.")
                        youtubeItems.forEachIndexed { index, (title, videoId) ->
                            val serialNumber = index + 1  // Index starts from 0, so add 1 for serial number
                            println("[$serialNumber] Title: $title")
                            println("Video ID: $videoId")
                            println("-----------------------")
                            context.performTextToSpeech("$serialNumber. $title")
                        }
                        context.performTextToSpeech("Tell me the serial number of the desired video.")
                        context.performSpeechRecognition()
                        delay(3000)
                        var indexx = 1
                        if (context.recognizedText in listOf("1","2","3","4","5","6","7","8","9","10")) {
                            indexx = context.recognizedText!!.toInt()
                        }
                        else {
                            context.performTextToSpeech("No number detected in your speech. So playing the first video.")
                        }
                      context.performTextToSpeech("This action is going to take you to the youtube app. You will have to comeback to Echo Sight app to continue using other features.")
                        context.playYouTubeVideo(context,youtubeItems[indexx].second)
                    }
                }
                else if (context.recognizedText?.lowercase() in listOf("read all unread messages","read all messages","read messages")) {
                   context.readUnreadSms()
                }
                else {
                    context.performTextToSpeech("Sorry unable to recognise what you said")
                }
            }
                      },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(13.dp) // Add padding to keep the button away from the edge
        ) {
            Text(text = "Ask Me !!")
        }
    }
}
class SharedPreferencesHelper(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("Tasks", Context.MODE_PRIVATE)
    private val editor = sharedPreferences.edit()
    fun getTasks(): MutableList<String> {
        val tasksSet = sharedPreferences.getStringSet("tasks", setOf<String>()) ?: setOf()
        return tasksSet.toMutableList()
    }
    fun saveTasks(tasks: List<String>) {
        editor.putStringSet("tasks", tasks.toSet())
        editor.apply()
    }
    fun clearTasks() {
        editor.remove("tasks")
        editor.apply()
    }
}
class YouTubeRepository {
    private val api = RetrofitInstance2.api
    suspend fun searchVideos(query: String, apiKey: String): YouTubeResponse? {
        return try {
            val response = api.searchVideos("snippet", query, "video", 10, apiKey)
            if (response.isSuccessful) {
                response.body()
            } else {
                println("API request failed with response code: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            println("Error fetching videos: ${e.message}")
            null
        }
    }
}
class YouTubeViewModel(private val repository: YouTubeRepository) : ViewModel() {
   suspend fun searchVideos(query: String, apiKey: String) :  List<Pair<String, String>> {
        viewModelScope.launch {
            val result = repository.searchVideos(query, apiKey)
            result?.items?.forEach { item ->
                println("Title: ${item.snippet.title}")
                println("Description: ${item.snippet.description}")
                println("Video ID: ${item.id.videoId}")
                println("-----------------------")
            }
        }
       return withContext(Dispatchers.IO) {
           val result = repository.searchVideos(query, apiKey)
           result?.items?.map { it.snippet.title to it.id.videoId } ?: emptyList()
       }
    }
}
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val bundle: Bundle? = intent?.extras
        try {
            if (bundle != null) {
                val pdus = bundle["pdus"] as Array<*>?
                if (pdus != null) {
                    for (pdu in pdus) {
                        val format = bundle.getString("format")
                        val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray, format)
                        val phoneNumber = smsMessage.displayOriginatingAddress
                        val message = smsMessage.displayMessageBody
                        Log.d("SmsReceiver", "Received SMS from $phoneNumber: $message")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Exception in onReceive: ${e.message}")
        }
    }
}
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyApp()
}
