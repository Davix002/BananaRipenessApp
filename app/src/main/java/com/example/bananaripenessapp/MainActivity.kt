package com.example.bananaripenessapp

import android.util.Base64
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberImagePainter
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import android.os.Environment
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var imageUri: MutableState<Uri>

    private val client = OkHttpClient()

    private var currentPhotoPath = ""

    private lateinit var response: MutableState<String>

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) {
            // Si la imagen se capturó con éxito, la envía
            val file = File(currentPhotoPath)
            imageUri.value = Uri.fromFile(file) // Aquí actualizamos imageUri
            sendImage(file)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permiso concedido, realiza la acción que lo requiere
                Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show()
            } else {
                // Permiso denegado, muestra un mensaje al usuario sobre por qué necesitas el permiso
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
            }
        }


    @SuppressLint("SimpleDateFormat")
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        response = mutableStateOf("")
        imageUri = mutableStateOf(Uri.EMPTY)

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                if (imageUri.value != Uri.EMPTY) {
                    Image(
                        painter = rememberImagePainter(data = imageUri.value),
                        contentDescription = "Imagen capturada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop // opcional, para recortar la imagen si es necesario
                    )
                }

                Text(response.value, modifier = Modifier.align(Alignment.Center))

                Button(
                    onClick = {
                        when {
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) ==
                                    PackageManager.PERMISSION_GRANTED -> {
                                // Ya tienes el permiso, realiza la acción que lo requiere
                                val photoFile = createImageFile()
                                val photoURI = FileProvider.getUriForFile(
                                    this@MainActivity,
                                    "com.example.bananaripenessapp.fileprovider",
                                    photoFile
                                )
                                takePicture.launch(photoURI)
                            }
                            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                                // Explica al usuario por qué necesitas el permiso, y luego solicítalo.
                            }
                            else -> {
                                // No tienes el permiso y no necesitas explicar por qué lo necesitas; solicítalo directamente
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Text("Tomar foto")
                }
            }
        }
    }

    private fun sendImage(imageFile: File) {
        // Leer los bytes del archivo de imagen
        val imageBytes = imageFile.readBytes()
        // Codificar los bytes a un string base64
        val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)
        val json = JSONObject()
        json.put("string_base", base64Image)

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            /*.url("http://ec2-54-211-202-101.compute-1.amazonaws.com:5000/prediction")*/
            /*.url("http://192.168.1.104:5000/prediction")*/
            /*.url("http://192.168.205.137:5000/prediction")*/
            .url("http://ec2-52-90-112-14.compute-1.amazonaws.com:5000/prediction")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val responseData = response.body?.string() ?: ""
                    val jsonObject = JSONObject(responseData)
                    val data = jsonObject.optString("data")

                    // Actualizar la interfaz de usuario en el hilo principal
                    runOnUiThread {
                        this@MainActivity.response.value = data // Use this to set the value
                    }
                }
            }
        })
    }
}
