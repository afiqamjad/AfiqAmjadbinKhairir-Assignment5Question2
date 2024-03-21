package com.example.afiqamjadbinkhairir_assignment5question2

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.speech.RecognitionListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.util.*
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener, TextToSpeech.OnInitListener {

    private val TAG = "MainActivity"

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var sensorManager: SensorManager
    private lateinit var selectedLanguage: String
    private lateinit var textToSpeech: TextToSpeech
    private var accelerometer: Sensor? = null
    private val shakethreshold = 10f
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    private var lastUpdate: Long = 0
    companion object {
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    }

    private val speechRecognitionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val recognizedText = results[0]
                val editText = findViewById<EditText>(R.id.editText)
                editText.setText(recognizedText)
            }
        }
    }

    private val languageCodeMap = mapOf(
        "Japanese" to "ja",
        "German" to "de",
        "Mandarin" to "zh",
        "English" to "en"
    )

    private val vacationSpots = mapOf(
        "Paris" to listOf("French"),
        "Tokyo" to listOf("Japanese"),
        "Berlin" to listOf("German"),
        "Beijing" to listOf("Mandarin"),
        "New York City" to listOf("English"),
        "Kyoto" to listOf("Japanese")
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val editText = findViewById<EditText>(R.id.editText)
        val spinner = findViewById<Spinner>(R.id.spinner)
        var spinnerUsed = false
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        ArrayAdapter.createFromResource(
            this,
            R.array.languagesSpinner,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (spinnerUsed) {
                    selectedLanguage = parent.getItemAtPosition(position) as String
                    startSpeechRecognition(languageCodeMap[selectedLanguage] ?: "")
                    initializeTextToSpeech()
                } else {
                    spinnerUsed = true // Set the flag to true after the Spinner is used once
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Toast.makeText(
                    applicationContext,
                    "Error occurred during speech recognition",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    editText.setText(matches[0])
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun initializeTextToSpeech() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        textToSpeech = TextToSpeech(this@MainActivity, this@MainActivity)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    override fun onDestroy() {
        // Shutdown TextToSpeech when the activity is destroyed
        textToSpeech.stop()
        textToSpeech.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language for TextToSpeech
            val result = textToSpeech.setLanguage(Locale(selectedLanguage)) // Assuming selectedLanguage is a valid language code

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported")
            }
        } else {
            Log.e(TAG, "Initialization failed")
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        val currentUpdateTime = System.currentTimeMillis()
        val timeInterval = currentUpdateTime - lastUpdate
        if (timeInterval < 100) return
        lastUpdate = currentUpdateTime

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val deltaX = x - lastX
        val deltaY = y - lastY
        val deltaZ = z - lastZ

        lastX = x
        lastY = y
        lastZ = z

        val speed = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()) / timeInterval * 10000
        if (speed > shakethreshold) {
            // Shake gesture detected
            launchGoogleMaps()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun launchGoogleMaps() {

        // Retrieve vacation spot locations associated with the selected language
        val spotsForLanguage = vacationSpots.filterValues { it.contains(selectedLanguage) }.keys.toList()

        // Randomly select a vacation spot location from the filtered list
        val random = Random()
        val randomSpot = spotsForLanguage[random.nextInt(spotsForLanguage.size)]

        // Launch Google Maps with the chosen location
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$randomSpot"))
        intent.setPackage("com.google.android.apps.maps")
        intent.resolveActivity(packageManager)?.let {
            speakHello(selectedLanguage)
            startActivity(intent)
        }
    }

    private fun speakHello(language: String) {
        val locale = Locale(languageCodeMap[language] ?: "")
        val helloText = getHelloTextInLanguage(locale)
        textToSpeech.speak(helloText, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    @SuppressLint("DiscouragedApi")
    private fun getHelloTextInLanguage(locale: Locale): String {
        val helloText = when (locale.language) {
            Locale.ENGLISH.language -> "Hello"
            Locale.FRENCH.language -> "Bonjour"
            Locale.SIMPLIFIED_CHINESE.language -> "你好"
            Locale.GERMAN.language -> "Hallo"
            Locale.JAPANESE.language -> "こんにちは"
            else -> "Hello" // Default to "Hello" for unsupported languages
        }
        return helloText
    }
    private fun startSpeechRecognition(language: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak something")
        speechRecognitionLauncher.launch(intent)
    }
}