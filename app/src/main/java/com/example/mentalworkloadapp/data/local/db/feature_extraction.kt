import kotlin.math.*
import kotlinx.coroutines.*
import org.jtransforms.fft.DoubleFFT_1D

/////////////////////////////////////////////////////////////////////////  
//  We have to add in build.grandle this line for JTrasforms library:  //
//  implementation 'com.github.wendykierp:JTransforms:3.1'             //
/////////////////////////////////////////////////////////////////////////

// Interaction with SampleEeg entity
interface SampleEegDao {
    @Query("SELECT * FROM SampleEeg ORDER BY timestamp ASC")
    suspend fun getAllSamplesOrderedByTimestamp(): List<SampleEeg>
}

// Time domain features calculation
fun rms(x: DoubleArray): Double {
    return sqrt(x.map { it * it }.average())
}

fun variance(x: DoubleArray): Double {
    val mean = x.average()
    return x.map { (it - mean).pow(2) }.average()
}

fun power(x: DoubleArray): Double {
    return x.map { it * it }.average()
}

fun formFactor(x: DoubleArray): Double {
    val rmsValue = rms(x)
    val mean = x.average()
    return if (mean != 0.0) rmsValue / mean else 0.0
}

fun pulseIndicator(x: DoubleArray): Double {
    val maxAbs = x.maxOf { abs(it) }
    val mean = x.average()
    return if (mean != 0.0) maxAbs / mean else 0.0
}

// Computation of Power Spectral Density (PSD) using the library and FFT method
fun computePSD(data: DoubleArray, fs: Int): Pair<DoubleArray, DoubleArray> {
    val n = data.size
    // Creation of the window
    val window = DoubleArray(n) { i -> 0.5 - 0.5 * cos(2.0 * Math.PI * i / (n - 1)) }

    // Apply window
    val windowed = DoubleArray(n) { i -> data[i] * window[i] }

    // Zero-pad to next power of two for FFT efficiency
    val nFFT = Integer.highestOneBit(n) shl 1
    val fftInput = DoubleArray(nFFT * 2) // real + imag interleaved
    for (i in 0 until n) {
        fftInput[2 * i] = windowed[i]
        fftInput[2 * i + 1] = 0.0
    }
    for (i in n until nFFT) {
        fftInput[2 * i] = 0.0
        fftInput[2 * i + 1] = 0.0
    }

    // Perform FFT using JTransforms
    val fft = DoubleFFT_1D(nFFT)
    fft.complexForward(fftInput)

    // Calculate PSD = |FFT|^2 / (fs * sum(window^2))
    val winSumSquares = window.map { it * it }.sum()
    val scale = fs * winSumSquares

    val psdLength = nFFT / 2 + 1
    val psd = DoubleArray(psdLength)
    val freqs = DoubleArray(psdLength) { it.toDouble() * fs / nFFT }

    for (i in 0 until psdLength) {
        val re = fftInput[2 * i]
        val im = fftInput[2 * i + 1]
        val magSq = re * re + im * im
        psd[i] = magSq / scale
    }

    return Pair(psd, freqs)
}

// Calculate band power by integrating PSD between fmin and fmax
fun bandPower(psd: DoubleArray, freqs: DoubleArray, fmin: Double, fmax: Double): Double {
    var power = 0.0
    for (i in freqs.indices) {
        if (freqs[i] in fmin..fmax) {
            power += psd[i]
        }
    }
    return power
}

// Spectral Entropy calculation
fun spectralEntropy(psd: DoubleArray): Double {
    val psdSum = psd.sum()
    if (psdSum == 0.0) return 0.0
    val normPsd = psd.map { it / psdSum }
    return -normPsd.filter { it > 0 }.sumOf { it * ln(it) / ln(2.0) }
}

// Extract features matrix 6x15 given raw channel data and sampling freq
fun extractFeaturesMatrix(chData: Array<DoubleArray>, fs: Int): Array<FloatArray> {
    val nChannels = chData.size
    val nFeatures = 15

    val bands = mapOf(
        "delta" to (0.5 to 4.0),
        "theta" to (4.0 to 8.0),
        "alpha" to (8.0 to 12.0),
        "beta" to (12.0 to 30.0)
    )

    val featuresMatrix = Array(nChannels) { FloatArray(nFeatures) }

    for (chIndex in 0 until nChannels) {
        val x = chData[chIndex]

        // Compute PSD and freq vector
        val (psd, freqs) = computePSD(x, fs)

        val totalPower = psd.sum().takeIf { it > 0 } ?: 1.0

        val absPowers = mutableMapOf<String, Double>()
        val relPowers = mutableMapOf<String, Double>()

        for ((band, range) in bands) {
            val absP = bandPower(psd, freqs, range.first, range.second)
            absPowers[band] = absP
            relPowers[band] = absP / totalPower
        }

        val absBetaPower = absPowers["beta"] ?: 0.0
        val rmsValue = rms(x)
        val powerValue = power(x)
        val thetaToAlphaRatio = if ((absPowers["alpha"] ?: 0.0) > 0) (absPowers["theta"]
            ?: 0.0) / (absPowers["alpha"] ?: 1.0) else 0.0
        val relDeltaPower = relPowers["delta"] ?: 0.0
        val varianceValue = variance(x)
        val relThetaPower = relPowers["theta"] ?: 0.0
        val formFactorValue = formFactor(x)
        val absThetaPower = absPowers["theta"] ?: 0.0
        val absAlphaPower = absPowers["alpha"] ?: 0.0
        val pulseIndicatorValue = pulseIndicator(x)
        val spectralEntropyValue = spectralEntropy(psd)
        val relAlphaPower = relPowers["alpha"] ?: 0.0
        val thetaAlphaToBetaRatio =
            if (absBetaPower > 0) ((absPowers["theta"] ?: 0.0) + (absPowers["alpha"] ?: 0.0)) / absBetaPower else 0.0
        val absDeltaPower = absPowers["delta"] ?: 0.0

        // Matrix struct: for each channel, these ones are the coloumns 
        featuresMatrix[chIndex][0] = absBetaPower.toFloat()
        featuresMatrix[chIndex][1] = rmsValue.toFloat()
        featuresMatrix[chIndex][2] = powerValue.toFloat()
        featuresMatrix[chIndex][3] = thetaToAlphaRatio.toFloat()
        featuresMatrix[chIndex][4] = relDeltaPower.toFloat()
        featuresMatrix[chIndex][5] = varianceValue.toFloat()
        featuresMatrix[chIndex][6] = relThetaPower.toFloat()
        featuresMatrix[chIndex][7] = formFactorValue.toFloat()
        featuresMatrix[chIndex][8] = absThetaPower.toFloat()
        featuresMatrix[chIndex][9] = absAlphaPower.toFloat()
        featuresMatrix[chIndex][10] = pulseIndicatorValue.toFloat()
        featuresMatrix[chIndex][11] = spectralEntropyValue.toFloat()
        featuresMatrix[chIndex][12] = relAlphaPower.toFloat()
        featuresMatrix[chIndex][13] = thetaAlphaToBetaRatio.toFloat()
        featuresMatrix[chIndex][14] = absDeltaPower.toFloat()
    }

    return featuresMatrix
}

// Function to get the data of the last N seconds from the DB and compute the features
suspend fun getFeaturesMatrixForLastNSeconds(
    dao: SampleEegDao,
    nSeconds: Int
): Array<FloatArray> {
    val samplingFreq = 100 // Hz (I think it's our frequency, i found it in an another script)

    val samplesNeeded = nSeconds * samplingFreq

    val allSamples = dao.getAllSamplesOrderedByTimestamp()

    if (allSamples.size < samplesNeeded) {
        throw IllegalArgumentException("Not enough data samples in database")
    }

    val recentSamples = allSamples.takeLast(samplesNeeded)

    val chData = Array(6) { DoubleArray(samplesNeeded) }
    for (i in 0 until samplesNeeded) {
        val sample = recentSamples[i]
        chData[0][i] = sample.ch_c1
        chData[1][i] = sample.ch_c2
        chData[2][i] = sample.ch_c3
        chData[3][i] = sample.ch_c4
        chData[4][i] = sample.ch_c5
        chData[5][i] = sample.ch_c6
    }

    return extractFeaturesMatrix(chData, samplingFreq)
}

// Flatten 6x15 features matrix into a 1D float array because .tflite loves this input format
fun flattenFeaturesMatrix(featuresMatrix: Array<FloatArray>): FloatArray {
    val nChannels = featuresMatrix.size
    val nFeatures = featuresMatrix[0].size
    val flat = FloatArray(nChannels * nFeatures)
    var index = 0
    for (ch in 0 until nChannels) {
        for (f in 0 until nFeatures) {
            flat[index++] = featuresMatrix[ch][f]
        }
    }
    return flat
}
