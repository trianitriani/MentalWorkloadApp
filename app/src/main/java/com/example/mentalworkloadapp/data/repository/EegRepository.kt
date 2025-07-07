package com.example.mentalworkloadapp.data.repository

import android.util.Log
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.util.EegFeatureExtractor

class EegRepository(private val dao: SampleEegDAO) {

    suspend fun getFeaturesMatrixForLastNSeconds(nSeconds: Int): Array<FloatArray> {
        // --- FIX 1: Define the RAW and TARGET sampling frequencies ---
        val rawSamplingFreq = 500 // The rate we are now saving at
        val targetSamplingFreq = 100 // The rate our model and feature extractor expect
        val downsampleFactor = rawSamplingFreq / targetSamplingFreq // This will be 5

        // Calculate how many raw samples we need to fetch from the DB
        val rawSamplesNeeded = nSeconds * rawSamplingFreq

        // The number of samples after downsampling
        val targetSamplesNeeded = nSeconds * targetSamplingFreq

        // Retrieve the latest raw samples
        val allSamples = dao.getAllSamplesOrderedByTimestamp()

        if (allSamples.size < rawSamplesNeeded) {
            // Not enough data yet, return empty or handle gracefully
            Log.w("EegRepository", "Not enough data for feature extraction. Have ${allSamples.size}, need $rawSamplesNeeded.")
            return emptyArray()
        }

        val recentRawSamples = allSamples.takeLast(rawSamplesNeeded)

        // --- FIX 2: Downsample the data before feature extraction ---
        // Create a new list by taking every N-th sample (where N is downsampleFactor)
        val downsampledSamples = recentRawSamples.filterIndexed { index, _ -> index % downsampleFactor == 0 }

        // Ensure we have the correct number of samples after downsampling
        if (downsampledSamples.size < targetSamplesNeeded) {
            Log.w("EegRepository", "Downsampled data size is smaller than expected. Have ${downsampledSamples.size}, need $targetSamplesNeeded.")
            return emptyArray()
        }

        // Take the last `targetSamplesNeeded` just in case of rounding
        val finalSamples = downsampledSamples.takeLast(targetSamplesNeeded)

        val chData = Array(6) { DoubleArray(targetSamplesNeeded) }

        for (i in 0 until targetSamplesNeeded) {
            val sample = finalSamples[i]
            chData[0][i] = sample.ch_c1
            chData[1][i] = sample.ch_c2
            chData[2][i] = sample.ch_c3
            chData[3][i] = sample.ch_c4
            chData[4][i] = sample.ch_c5
            chData[5][i] = sample.ch_c6
        }

        // --- FIX 3: Use the TARGET frequency for the feature extractor ---
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