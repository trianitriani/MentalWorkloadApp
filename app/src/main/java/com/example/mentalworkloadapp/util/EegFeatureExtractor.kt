package com.example.mentalworkloadapp.util

import kotlin.math.*
import org.jtransforms.fft.DoubleFFT_1D
import com.example.mentalworkloadapp.util.EegPreprocessing

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

    private fun hammingWindow(N: Int): DoubleArray {
        return DoubleArray(N) { 0.54 - 0.46 * cos(2.0 * Math.PI * it / (N - 1)) }
    }

    private fun computeWelchMethodPSD(signal: DoubleArray, samplingRate: Int): Pair<DoubleArray, DoubleArray> {

        //Parameters for welch method
        val segmentLength = 2000 //500 samples per second, 4 second window
        val overlap = segmentLength / 2
        val step = segmentLength - overlap

        //Creation of the window and normalizing
        val window = hammingWindow(segmentLength)
        val windowPower = window.map { it * it }.sum() / segmentLength

        //Output array inizialization
        val numSegments = (signal.size - overlap) / step
        val psd = DoubleArray(segmentLength / 2 + 1) { 0.0 }
        val fft = DoubleFFT_1D(segmentLength.toLong())

        //For each segment
        for (i in 0 until numSegments) {
            val start = i * step
            val segment = signal.copyOfRange(start, start + segmentLength)

            // Apply window
            for (j in segment.indices) {
                segment[j] *= window[j]
            }

            // Prepare for real FFT (complex values)
            val fftData = DoubleArray(segmentLength * 2)
            for (j in segment.indices) {
                fftData[2 * j] = segment[j]
                fftData[2 * j + 1] = 0.0
            }
            //Get FFT of the windowed segment
            fft.complexForward(fftData)

            //Compute PSD of each frequency bin of the segment
            for (j in 0 until segmentLength / 2 + 1) {
                val re = fftData[2 * j]
                val im = fftData[2 * j + 1]
                psd[j] += (re * re + im * im) / (segmentLength * samplingRate * windowPower)
            }
        }

        //Averaging the psd
        for (j in psd.indices) {
            psd[j] /= numSegments
        }

        //Compute corresponding frequencies
        val freqs = DoubleArray(segmentLength / 2 + 1) { it.toDouble() * samplingRate / segmentLength }
        return Pair(freqs, psd)
    }

    fun butterworthLowPassFilter(
        signal: DoubleArray,
        cutoffFreq: Double,   // cutoff frequency in Hz
        samplingRate: Double  // sampling rate in Hz
    ): DoubleArray {
        val filtered = DoubleArray(signal.size)

        // Pre-warping for bilinear transform
        val wc = tan(Math.PI * cutoffFreq / samplingRate)

        // Calculate coefficients for 2nd order Butterworth low-pass filter
        val k1 = sqrt(2.0) * wc
        val k2 = wc * wc
        val a = k2 / (1 + k1 + k2)
        val b = 2 * a
        val c = a
        val d = 2 * (k2 - 1) / (1 + k1 + k2)
        val e = (1 - k1 + k2) / (1 + k1 + k2)

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        for (i in signal.indices) {
            val x0 = signal[i]
            val y0 = a * x0 + b * x1 + c * x2 - d * y1 - e * y2
            filtered[i] = y0

            // Shift delay line
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }

        return filtered
    }

    fun subsample(signal: DoubleArray, factor: Int): DoubleArray {
        val newSize = signal.size / factor
        return DoubleArray(newSize) { i -> signal[i * factor] }
    }

    private fun fastNotch50Hz(signal: DoubleArray, samplingRate: Int): DoubleArray {
        val f0 = 50.0  // Notch frequency
        val Q = 30.0   // Quality factor (higher = narrower notch)

        val w0 = 2 * Math.PI * f0 / samplingRate
        val alpha = sin(w0) / (2 * Q)

        val b0 = 1.0
        val b1 = -2 * cos(w0)
        val b2 = 1.0
        val a0 = 1 + alpha
        val a1 = -2 * cos(w0)
        val a2 = 1 - alpha

        // Normalize coefficients
        val normB0 = b0 / a0
        val normB1 = b1 / a0
        val normB2 = b2 / a0
        val normA1 = a1 / a0
        val normA2 = a2 / a0

        val output = DoubleArray(signal.size)
        var x1 = 0.0; var x2 = 0.0
        var y1 = 0.0; var y2 = 0.0

        for (i in signal.indices) {
            val x0 = signal[i]
            val y0 = normB0 * x0 + normB1 * x1 + normB2 * x2 - normA1 * y1 - normA2 * y2
            output[i] = y0
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
        }
        return output
    }


    // Extract a feature matrix from EEG channels
    fun extractFeaturesMatrix(channels: Array<DoubleArray>, samplingRate: Int): Array<FloatArray> {

        // Preprocessing of the voltages
        val preprocessedChannels = EegPreprocessing.preprocess(channels)

        // Create an array of FloatArrays, one per channel (6 channels)
        return Array(6) { chIdx ->
            // Select current channel signal (preprocessed)
            val signal = preprocessedChannels[chIdx]
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

    fun EXPERIMENTALextractFeaturesMatrix(channels: Array<DoubleArray>, samplingRate: Int): Array<FloatArray> {
        // Create an array of FloatArrays, one per channel (6 channels)
        return Array(6) { chIdx ->
            // Select current channel signal
            val signal = channels[chIdx]

            //Low pass filter prevent aliasing for subsampling
            val lowFilteredSignal = butterworthLowPassFilter(signal,45.0,500.0)
            //Subsampling from 500 Hz to 100 Hz
            val subsampledSignal = subsample(lowFilteredSignal,5)
            //Reducing noise in 50 Hz range
            val notchFilterdSignal = fastNotch50Hz(subsampledSignal,100)
            // Compute frequencies and PSD for the signal
            val (freqs, psd) = computeWelchMethodPSD(notchFilterdSignal, samplingRate)

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
