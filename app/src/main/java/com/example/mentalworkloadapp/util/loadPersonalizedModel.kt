package com.example.mentalworkloadapp.util

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.File


fun checkPointFileExists(context: Context): Boolean {
    val file = File(context.filesDir,"checkpoint.ckpt")
    return file.exists()
}

fun restoreModelFromCheckpointFile(context: Context, interpreter: Interpreter){
    val outputFile = File(context.filesDir, "checkpoint.ckpt")
    val inputs: MutableMap<String, Any> = HashMap()
    inputs["checkpoint_path"] = outputFile.absolutePath
    val outputs: Map<String, Any> = HashMap()
    interpreter.runSignature(inputs, outputs, "load_weights")
}