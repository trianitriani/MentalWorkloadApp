package com.example.mentalworkloadapp.data.repository

import android.util.Log
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.util.EegFeatureExtractor

class EegRepository(private val dao: SampleEegDAO) {
    suspend fun getFeaturesMatrixForLastNSeconds(nSeconds: Int): Array<FloatArray> {
        // --- Define the RAW and TARGET sampling frequencies ---
        val rawSamplingFreq = 500 // The rate we are saving at
        val targetSamplingFreq = 100 // The rate our model and feature extractor expect
        val downsampleFactor = rawSamplingFreq / targetSamplingFreq // This will be 5
        // Define the REQUIRED number of samples for the preprocessing pipeline.
        // This MUST be a power of two for the JWave (wavelet) transform and >= 127 for the Savitzky-Golay filter.
        // 128 is the smallest number that satisfies both conditions.
        val requiredSamples = 128
        // Calculate how many raw samples we need to fetch from the DB to get the required number of downsampled samples.
        val rawSamplesNeeded = requiredSamples * downsampleFactor // 128 * 5 = 640

        val recentRawSamples = dao.getLastNRawSamples(rawSamplesNeeded).reversed()

        if (recentRawSamples.size < rawSamplesNeeded) {
            Log.w("EegRepository", "Not enough data for feature extraction. Have ${recentRawSamples.size}, need $rawSamplesNeeded.")
            return emptyArray()
        }

        //  Downsample the data before feature extraction
        // Create a new list by taking every N-th sample (where N is downsampleFactor)
        val downsampledSamples = recentRawSamples.filterIndexed { index, _ -> index % downsampleFactor == 0 }

        // Ensure we have the correct number of samples after downsampling, take the last `requiredSamples` to be safe.
        if (downsampledSamples.size < requiredSamples) {
            Log.w("EegRepository", "Downsampled data size is smaller than expected. Have ${downsampledSamples.size}, need $requiredSamples.")
            return emptyArray()
        }
        val finalSamples = downsampledSamples.takeLast(requiredSamples)

        // The size of finalSamples should now be 128
        val chData = Array(6) { DoubleArray(finalSamples.size) }

        for (i in finalSamples.indices) {
            val sample = finalSamples[i]
            chData[0][i] = sample.ch_c1
            chData[1][i] = sample.ch_c2
            chData[2][i] = sample.ch_c3
            chData[3][i] = sample.ch_c4
            chData[4][i] = sample.ch_c5
            chData[5][i] = sample.ch_c6
        }

        // Use the TARGET frequency for the feature extractor
        // The feature extractor will now receive an array of 128 samples, which is valid for the entire pipeline.
        return EegFeatureExtractor.extractFeaturesMatrix(chData, targetSamplingFreq)
    }

    // This function can be updated similarly if needed
    suspend fun getFeaturesMatrixSessionSamples(sessionSamples: List<SampleEeg>): Array<FloatArray> {
        // For now, assuming this is also used with the 100Hz model
        val targetSamplingFreq = 100
        val rawSamplingFreq = 500 // Assuming sessionSamples are raw
        val downsampleFactor = rawSamplingFreq / targetSamplingFreq

        val downsampledSamples = sessionSamples.filterIndexed { index, _ -> index % downsampleFactor == 0 }

        val chData = Array(6) { DoubleArray(downsampledSamples.size) }

        for (i in downsampledSamples.indices) {
            val sample = downsampledSamples[i]
            chData[0][i] = sample.ch_c1
            chData[1][i] = sample.ch_c2
            chData[2][i] = sample.ch_c3
            chData[3][i] = sample.ch_c4
            chData[4][i] = sample.ch_c5
            chData[5][i] = sample.ch_c6
        }

        return EegFeatureExtractor.extractFeaturesMatrix(chData, targetSamplingFreq)
    }
}