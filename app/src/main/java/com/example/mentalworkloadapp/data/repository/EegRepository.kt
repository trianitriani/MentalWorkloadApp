package com.example.mentalworkloadapp.data.repository

import android.util.Log
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.util.EegFeatureExtractor

class EegRepository(private val dao: SampleEegDAO) {
    // Repository class that encapsulates data access via the DAO

    // This function returns the feature matrix for the last n seconds
    suspend fun getFeaturesMatrixForLastNSeconds(nSeconds: Int): Array<FloatArray> {
        // Calculates how many samples are needed to cover n seconds this (or some other constant) likely must be a power of two for the JWave library.  2^14 = 16384. This is close to the intended 32 seconds * 500 Hz = 16000 samples.
        val samplesNeeded = 16384

        // Use the efficient DAO method to get only the last N samples
        val recentSamplesDescending = dao.getLastNRawSamples(samplesNeeded)

        // Check if we have enough data
        if (recentSamplesDescending.size < samplesNeeded) {
            Log.e("EegRepository", "Not enough data samples in database. Needed: $samplesNeeded, Found: ${recentSamplesDescending.size}")
            // Return an empty array
            return emptyArray()
        }

        // Reverse the list to have samples in ascending chronological order for processing
        val recentSamples = recentSamplesDescending.reversed()

        // Initializes a 2D array: 6 EEG channels, each with samplesNeeded double values
        val chData = Array(6) { DoubleArray(samplesNeeded) }

        // Extracts the values of the 6 channels for each sample and stores them in chData matrix
        for (i in 0 until samplesNeeded) {
            val sample = recentSamples[i]
            chData[0][i] = sample.ch_c1
            chData[1][i] = sample.ch_c2
            chData[2][i] = sample.ch_c3
            chData[3][i] = sample.ch_c4
            chData[4][i] = sample.ch_c5
            chData[5][i] = sample.ch_c6
        }

        // Pass the data matrix and sampling frequency to the EegFeatureExtractor
        return EegFeatureExtractor.extractFeaturesMatrix(chData, 100) // samplingFreq is 100
    }

    suspend fun getFeaturesMatrixSessionSamples(sessionSamples: List<SampleEeg>): Array<FloatArray> {

        // Initializes a 2D array: 6 EEG channels, each with samplesNeeded double values
        val chData = Array(6) { DoubleArray(sessionSamples.size) }

        // Extracts the values of the 6 channels for each sample and stores them in chData matrix
        for (i in 0 until sessionSamples.size) {
            val sample = sessionSamples[i]

            chData[0][i] = sample.ch_c1
            chData[1][i] = sample.ch_c2
            chData[2][i] = sample.ch_c3
            chData[3][i] = sample.ch_c4
            chData[4][i] = sample.ch_c5
            chData[5][i] = sample.ch_c6
        }

        return EegFeatureExtractor.extractFeaturesMatrix(chData, 100)
    }
}
