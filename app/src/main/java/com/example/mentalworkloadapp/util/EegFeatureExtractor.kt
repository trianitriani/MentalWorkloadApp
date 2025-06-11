package com.example.mentalworkloadapp.domain.features

import kotlin.math.*
import org.jtransforms.fft.DoubleFFT_1D

// Object for extracting EEG features
object EegFeatureExtractor {
    // Calculate root mean square (RMS) of a signal
    private fun rms(signal: DoubleArray) = sqrt(signal.map { it * it }.average())

    // Calculate variance of a signal
    private fun variance(signal: DoubleArray): Double {
        val mean = signal.average() 
        return signal.map { (it - mean).pow(2) }.average() 
    }

    // Calculate average power of a signal
    private fun power(signal: DoubleArray) = signal.map { it * it }.sum() / signal.size

    // Calculate form factor: ratio of RMS to absolute average of signal
    private fun formFactor(signal: DoubleArray) = rms(signal) / signal.average().absoluteValue

    // Calculate pulse indicator: max value divided by RMS, or 0 if empty
    private fun pulseIndicator(signal: DoubleArray): Double = signal.maxOrNull()?.div(rms(signal)) ?: 0.0

    // Compute Power Spectral Density (PSD) and frequencies using FFT
    private fun computePSD(signal: DoubleArray, samplingRate: Int): Pair<DoubleArray, DoubleArray> {
        // Number of samples
        val n = signal.size
        val fft = DoubleFFT_1D(n.toLong())
        // Prepare array for FFT (real + imaginary parts)
        val fftData = signal.copyOf(n * 2)
        // Perform FFT on real input
        fft.realForwardFull(fftData)

        // Array to store power spectral density values (PSD)
        val psd = DoubleArray(n / 2)
        for (i in 0 until n / 2) {
            val real = fftData[2 * i]
            val imag = fftData[2 * i + 1]
            psd[i] = (real * real + imag * imag) / (n * samplingRate.toDouble())
        }

        // Calculate frequency values corresponding to PSD bins
        val freqs = DoubleArray(n / 2) { it * samplingRate.toDouble() / n }
        return Pair(freqs, psd)
    }

    // Calculate power within a specific frequency band from PSD
    private fun bandPower(freqs: DoubleArray, psd: DoubleArray, low: Double, high: Double): Double {
        var power = 0.0
        for (i in freqs.indices) {
            // Sum PSD values within frequency band
            if (freqs[i] in low..high) power += psd[i]
        }
        return power
    }

    // Calculate spectral entropy of the PSD as a measure of signal complexity
    private fun spectralEntropy(psd: DoubleArray): Double {
        // Sum of all PSD values for normalization
        val psdSum = psd.sum() 
        // Normalize PSD to probability distribution
        val normPsd = psd.map { it / psdSum }
        // Compute entropy using formula: -sum(p * ln(p)) ignoring zero values
        return -normPsd.sumOf { if (it > 0) it * ln(it) else 0.0 }
    }

    // Extract a feature matrix from EEG channels
    fun extractFeaturesMatrix(channels: Array<DoubleArray>, samplingRate: Int): Array<FloatArray> {
        // Create an array of FloatArrays, one per channel (6 channels)
        return Array(6) { chIdx ->
            // Select current channel signal
            val signal = channels[chIdx]
            // Compute frequencies and PSD for the signal
            val (freqs, psd) = computePSD(signal, samplingRate)

            // Calculate absolute and relative powers for EEG bands
            val absDelta = bandPower(freqs, psd, 0.5, 4.0)
            val relDelta = absDelta / psd.sum()
            val absTheta = bandPower(freqs, psd, 4.0, 8.0)
            val relTheta = absTheta / psd.sum()
            val absAlpha = bandPower(freqs, psd, 8.0, 13.0)
            val relAlpha = absAlpha / psd.sum()
            val absBeta = bandPower(freqs, psd, 13.0, 30.0)
            val relBeta = absBeta / psd.sum()

            // Calculate ratios between EEG bands
            val thetaAlphaToBeta = (absTheta + absAlpha) / absBeta
            val thetaToAlpha = absTheta / absAlpha
            val alphaToTheta = absAlpha / absTheta

            // Return all features as a FloatArray for the current channel. The order is very important because used in the model
            floatArrayOf(
                absBeta.toFloat(),
                rms(signal).toFloat(),
                power(signal).toFloat(),
                thetaToAlpha.toFloat(),
                relDelta.toFloat(),
                variance(signal).toFloat(),
                relTheta.toFloat(),
                formFactor(signal).toFloat(),
                absTheta.toFloat(),
                absAlpha.toFloat(),
                pulseIndicator(signal).toFloat(),
                spectralEntropy(psd).toFloat(),
                relAlpha.toFloat(),
                thetaAlphaToBeta.toFloat(),
                absDelta.toFloat()
            )
        }
    }

    // Flatten a 2D features matrix into a 1D FloatArray for the model .tflite
    fun flattenFeaturesMatrix(featuresMatrix: Array<FloatArray>): FloatArray =
        featuresMatrix.flatMap { it.asList() }.toFloatArray()
}
