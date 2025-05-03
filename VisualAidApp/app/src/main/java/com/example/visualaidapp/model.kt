package com.example.visualaidapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

class ImageCaptioner(context: Context) {

    private var model: Interpreter? = null // TensorFlow Lite interpreter
    private val wordIndex: Map<Int, String> = loadWordIndex(context) // Token ID to word map

    init {
        try {
            // Load the model from the assets folder
            val modelFile = FileUtil.loadMappedFile(context, "model.tflite")
            model = Interpreter(modelFile)
            Log.d("ImageCaptioner", "Model loaded successfully.")

            // Log input tensor metadata
            for (i in 0 until model!!.inputTensorCount) {
                val tensor = model!!.getInputTensor(i)
                Log.d("ModelInfo", "Input[$i]: name=${tensor.name()}, shape=${tensor.shape().contentToString()}, type=${tensor.dataType()}")
            }

            // Log output tensor metadata
            for (i in 0 until model!!.outputTensorCount) {
                val tensor = model!!.getOutputTensor(i)
                Log.d("ModelInfo", "Output[$i]: name=${tensor.name()}, shape=${tensor.shape().contentToString()}, type=${tensor.dataType()}")
            }
        } catch (e: Exception) {
            Log.e("ImageCaptioner", "Failed to load model: ${e.message}")
        }
    }

    // Generate caption for a given bitmap
    fun generateCaption(bitmap: Bitmap, onProgress: (Float) -> Unit): String {
        if (model == null) return "Model not loaded"

        val imageInput = preprocess(bitmap) // Preprocess the image
        val maxLen = 15
        val startToken = 1
        val endToken = 1

        val tokens = IntArray(maxLen) { 0 } // Initialize token array
        tokens[0] = startToken
        var currentLength = 1

        for (i in 1 until maxLen) {
            val capInInput = Array(1) { FloatArray(maxLen) { 0f } }
            for (j in 0 until maxLen) {
                capInInput[0][j] = tokens.getOrElse(j) { 0 }.toFloat()
            }

            val inputs = arrayOf(capInInput, imageInput) // Model expects two inputs

            val output = HashMap<Int, Any>()
            output[0] = Array(1) { Array(maxLen) { FloatArray(10000) } } // Output buffer for logits

            model!!.runForMultipleInputsOutputs(inputs, output) // Run inference

            val predictions = output[0] as Array<Array<FloatArray>>
            val probs = predictions[0][i - 1] // Get probabilities for this step
            val nextToken = probs.indices.maxByOrNull { probs[it] } ?: break // Pick most probable token

            if (nextToken == endToken) break // Stop if end token is predicted

            tokens[currentLength] = nextToken
            currentLength++

            onProgress(currentLength.toFloat() / maxLen) // Update progress
        }

        return decodeOutput(tokens.copyOfRange(1, currentLength)) // Convert tokens to string
    }

    // Convert list of token IDs to words
    private fun decodeOutput(tokens: IntArray): String {
        return tokens
            .filter { it != 0 } // Skip padding
            .mapNotNull { wordIndex[it] } // Convert tokens to words
            .joinToString(" ") // Join into sentence
    }

    // Load word index from asset file
    private fun loadWordIndex(context: Context): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        val reader = context.assets.open("word_index.txt").bufferedReader()
        reader.forEachLine { line ->
            val parts = line.trim().split(":")
            if (parts.size == 2) {
                val word = parts[0]
                val index = parts[1].toIntOrNull()
                if (index != null) {
                    map[index] = word
                }
            }
        }
        return map
    }

    // Resize and normalize image input
    private fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val inputSize = 224 // Model input dimension
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val input = Array(inputSize) { Array(inputSize) { FloatArray(3) } }

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resized.getPixel(x, y)
                input[y][x][0] = Color.red(pixel) / 255.0f  // Red channel
                input[y][x][1] = Color.green(pixel) / 255.0f  // Green channel
                input[y][x][2] = Color.blue(pixel) / 255.0f  // Blue channel
            }
        }

        return arrayOf(input) // Add batch dimension
    }

    // Optional: unused output decoder stub for ByteArray (not used in current pipeline)
    private fun decodeOutput(output: ByteArray): String {
        return String(output).trim()
    }
}
