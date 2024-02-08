package com.example.assistant_vocal

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.Locale

// ============================================================================================
// =========================== Classes to Handle Requests to OPENAI ===========================
// ============================================================================================

// Interface to define API's service and interact with OpenAI using Retrofit (Library for HTTP requests)
interface OpenAIApiService {
    //             **********************************************************
    //       **********************************************************************
    // ************************ ENTER THE OpenAI'S API KEY THERE ***********************
    @Headers("Authorization: Bearer API_KEY_HERE")
    // *********************************************************************************
    //       **********************************************************************
    //             **********************************************************
    // Defines a method to send a POST request to the "v1/completions" endpoint of the OpenAI API
    @POST("v1/completions")
    // Takes RequestBody as input and returns a ReponseBody
    fun getCompletion(@Body body: RequestBody): retrofit2.Call<ResponseBody>
}

// ============================================================================================
// =========================== Classes to Handle Requests to OpenAI ===========================
// ============================================================================================

// To send a request to OpenAI's API
data class RequestBody(val prompt: String, // QUestion asked to OpenAI
                       val model: String = "gpt-3.5-turbo-instruct", // Model used by the application
                       val max_tokens: Int = 700) // Max character number loaded in the request

// To retrieve OpenAI's answer
data class ResponseBody(val choices: List<Choice>)
data class Choice(val text: String)

// ============================================================================================
// =================================== Application's Class ====================================
// ============================================================================================

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var recognizedText by mutableStateOf("") // Question asked to Google's vocal recognition API
    private var answer by mutableStateOf("") // Answer gaven by OpenAI's API
    private lateinit var textToSpeech: TextToSpeech // Google's TextToSpeech API to read the answer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)

        // Content of the page
        setContent {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.padding(bottom = 15.dp).fillMaxWidth()) {
                    Text("Assistant vocal", fontSize = 20.sp)
                }
                // Button to ask a question
                Button(
                    onClick = {
                        if (::textToSpeech.isInitialized) {
                            textToSpeech.stop() // Stops the textToSpeech if it's initialized
                        }
                        startSpeechToText() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Appuyez pour poser votre question")
                }
                // TextField containing the question asked
                TextField(
                    value = recognizedText,
                    onValueChange = {},
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .focusable(false),
                    readOnly = true,
                    label = { Text("Votre question") }
                )
                // Button to stop the textToSpeech reading the answer
                Button(
                    onClick = {
                        if (::textToSpeech.isInitialized) {
                            textToSpeech.stop() // Stops the textToSpeech if it's initialized
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Arrêter la lecture")
                }
                // TextField containing the question's answer
                TextField(
                    value = answer,
                    onValueChange = {},
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .weight(1f)
                        .focusable(false),
                    readOnly = true,
                    label = { Text("Réponse") }
                )
                // Button to get a new answer to the question already asked
                Button(
                    onClick = {
                        // If the question is recognized (the TextField isn't empty), the OpenAI API will answer
                        if (recognizedText.isNotEmpty()) {
                            getOpenAIResponse(recognizedText)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Obtenir une nouvelle réponse")
                }
            }
        }
    }

    // Function to start the textToSpeech
    private fun startSpeechToText() {
        // Creates an intent to recognize the voice
        val sttIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Configure the language model to handle the voice freely
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Tells the user when he have to speak
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez maintenant...")
        }
        // Starts the textToSpeech and waits for the result
        startActivityForResult(sttIntent, REQUEST_CODE_SPEECH_INPUT)
    }

    // Function called when a result is returned from the speech recognition activity
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Checks if the result is from the speech recognition and if it's valid
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == Activity.RESULT_OK) {
            // Retrieves the speech recognition results
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            // Stores the result in recognizedText
            recognizedText = results?.get(0) ?: ""
            // Sends the spoken text to OpenAI's API for a response
            getOpenAIResponse(recognizedText)
        }
    }

    // Function to send a question to the OpenAI API and receive a response
    private fun getOpenAIResponse(question: String) {
        // Initializes Retrofit with the base URL of the OpenAI API
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // Creates an instance of the API service
        val service = retrofit.create(OpenAIApiService::class.java)

        // Sends the question to the API and handles the answer
        service.getCompletion(RequestBody(question)).enqueue(object : retrofit2.Callback<ResponseBody> {
            // Handles the successful answer from the API
            override fun onResponse(call: retrofit2.Call<ResponseBody>, response: retrofit2.Response<ResponseBody>) {
                if (response.isSuccessful) {
                    // Extracts the answer text
                    val responseBody = response.body()
                    val responseText = responseBody?.choices?.firstOrNull()?.text ?: "Sorry, I cannot answer this question."
                    // Updates the answer variable with the answer text
                    answer = responseText
                    // The textToSpeech eads the answer
                    speakOut(responseText)
                } else {
                    // Handles error in case of an unsuccessful answer
                    answer = "Error: ${response.errorBody()?.string()}"
                }
            }
            // Handles network connection errors
            override fun onFailure(call: retrofit2.Call<ResponseBody>, t: Throwable) {
                answer = "Network error"
            }
        })
    }

    // Callback function called upon TextToSpeech initialization
    override fun onInit(status: Int) {
        // Checks if TextToSpeech was successfully initialized
        if (status == TextToSpeech.SUCCESS) {
            // Sets the language for TextToSpeech
            textToSpeech.language = Locale.FRANCE
        }
    }

    // Function to read text aloud
    private fun speakOut(text: String) {
        // Checks if TextToSpeech is initialized before speaking out the text
        if (::textToSpeech.isInitialized) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // Defines a constant request code for voice input
    companion object {
        private const val REQUEST_CODE_SPEECH_INPUT = 1000
    }
}