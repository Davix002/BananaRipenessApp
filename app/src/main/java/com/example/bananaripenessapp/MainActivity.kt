package com.example.bananaripenessapp

import android.util.Base64
import android.Manifest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var imageUri: MutableState<Uri>

    private val client = OkHttpClient()

    private var currentPhotoPath = ""


    private var dataState: MutableState<String> = mutableStateOf("")
    private var mensajeState: MutableState<String> = mutableStateOf("")
    private var precioState: MutableState<String> = mutableStateOf("")

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



    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageUri = mutableStateOf(Uri.EMPTY)

        setContent {
            App(dataState, mensajeState, precioState, imageUri) {
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
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                        // Explica al usuario por qué necesitas el permiso, y luego solicítalo.
                    }
                    else -> {
                        // No tienes el permiso y no necesitas explicar por qué lo necesitas; solicítalo directamente
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }
    }

    private fun sendImage(imageFile: File) {
        // Use launch to start a new coroutine
        CoroutineScope(Dispatchers.IO).launch {
            // Update UI state to be empty
            withContext(Dispatchers.Main) {
                dataState.value = ""
                mensajeState.value = ""
                precioState.value = ""
            }

            val imageBytes = imageFile.readBytes()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)
            val json = JSONObject()
            json.put("string_base", base64Image)

            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("http://ec2-18-205-229-14.compute-1.amazonaws.com:5000/prediction")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseData = response.body?.string() ?: ""
                val jsonObject = JSONObject(responseData)
                val data = jsonObject.optString("data")
                val mensaje = jsonObject.optString("mensaje") // Deserializando "mensaje"
                val precio = jsonObject.optString("precio") // Deserializando "precio"

                // Update UI on the main thread
                withContext(Dispatchers.Main) {
                    dataState.value = "Banano $data" // Actualizando estado de data
                    mensajeState.value = mensaje // Actualizando estado de mensaje
                    precioState.value = "A tan solo $$precio /lib " // Actualizando estado de precio
                }
            }
        }
    }

}

@Preview
@Composable
fun App(    dataState: MutableState<String> = mutableStateOf(""),
            mensajeState: MutableState<String> = mutableStateOf(""),
            precioState: MutableState<String> = mutableStateOf(""),
            imageUri: MutableState<Uri> = mutableStateOf(Uri.EMPTY),
            onButtonClick: () -> Unit = {}){


    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .background(Color.White)
        .padding(14.dp)){ // Agregado padding a todos los elementos en el LazyColumn
        item{
            Text(text = "Banana Ripeness Classifier",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp), // Agregado padding superior e inferior al texto
                textAlign = TextAlign.Center,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            if (imageUri.value != Uri.EMPTY) {
                Image(
                    painter = rememberImagePainter(data = imageUri.value),
                    contentDescription = "Imagen capturada",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(10.dp)), // Bordes redondeados

                    contentScale = ContentScale.Crop
                )
            } else {
                Image(painter = painterResource(id = R.drawable.bananaico),
                    contentDescription = "Bananas",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(10.dp)) // Bordes redondeados
                        .shadow(2.dp), // Sombra
                )
            }

            Column(modifier = Modifier
                .fillMaxWidth(),// Agregado padding a la columna
                horizontalAlignment = Alignment.CenterHorizontally) {

                Text(dataState.value, modifier = Modifier.padding(top = 8.dp),fontSize = 28.sp)
                Text(mensajeState.value, modifier = Modifier.padding(16.dp), fontSize = 18.sp, textAlign = TextAlign.Center)
                Text(precioState.value, fontSize = 16.sp)

                Button(
                    onClick = { onButtonClick() },
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    Text("Tomar foto")
                }
            }
        }
    }
}



