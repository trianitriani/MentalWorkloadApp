package com.example.mentalworkloadapp.util

import jwave.transforms.FastWaveletTransform
import jwave.transforms.wavelets.daubechies.Daubechies2
import kotlin.math.*
import com.example.mentalworkloadapp.util.SavitzkyGolay

// This code is the kotlin translation of the MATLAB preprocessing script used for preprocessing
// the samples of the pre-training dataset we used

object EegPreprocessing {

    // Initialize the wavelet transform using Daubechies2 wavelet
    private val wavelet = FastWaveletTransform(Daubechies2())

    // Main method to preprocess a matrix of EEG channels
    fun preprocess(channels: Array<DoubleArray>): Array<DoubleArray> {
        return Array(channels.size) { chIdx ->
            // Original EEG signal for the current channel
            val original = channels[chIdx]
            // Remove low-frequency trend using Savitzky-Golay filtering
            val detrended = subtractTrend(original)
            // Apply wavelet thresholding to clean the signal
            val thresholded = thresholdWavelet(detrended)
            // Return the processed signal
            thresholded
        }
    }

    // Removes the trend from the signal using Savitzky-Golay filtering
    private fun subtractTrend(signal: DoubleArray): DoubleArray {
        val trend = SavitzkyGolay.filter(signal)
        return signal.zip(trend) { x, t -> x - t }.toDoubleArray()
    }

    // Applies wavelet transform and thresholding to remove noise
    private fun thresholdWavelet(signal: DoubleArray): DoubleArray {
        // Decompose the signal using wavelet transform
        val coeffs = wavelet.decompose(signal)

        // Check if we have enough components (at least 5)
        if (coeffs.size < 5) {
            throw IllegalArgumentException("The signal is too short for 4 levels wavelet decomposition.")
        }

        // Extract wavelet components
        val approx = coeffs[0]
        val cd4 = coeffs[1]
        val cd3 = coeffs[2]
        val cd2 = coeffs[3]
        val cd1 = coeffs[4]

        // Compute the threshold based on the standard deviation of cd3
        val t = std(cd3) * 0.8

        // Define a soft thresholding function
        fun applyThreshold(data: DoubleArray): DoubleArray = data.map {
            val s = sign(it)
            val abs = abs(it)
            if (abs >= t) s * t else s * abs
        }.toDoubleArray()

        // Apply the thresholding to each wavelet component
        val approxT = applyThreshold(approx)
        val cd1T = applyThreshold(cd1)
        val cd2T = applyThreshold(cd2)
        val cd3T = applyThreshold(cd3)
        val cd4T = applyThreshold(cd4)

        // Reconstruct the signal using the thresholded components
        // The order must match the original script structure: [approx, cd4, cd3, cd2, cd1]
        val thresholdedCoeffs = arrayOf(
            approxT,
            cd4T,
            cd3T,
            cd2T,
            cd1T
        )

        return wavelet.recompose(thresholdedCoeffs)
    }

    // Computes the standard deviation of a signal
    private fun std(data: DoubleArray): Double {
        val mean = data.average()
        return sqrt(data.map { (it - mean).pow(2) }.average())
    }
}
