package com.example.mentalworkloadapp.data.repository

import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.util.EegFeatureExtractor

class EegRepository(private val dao: SampleEegDAO) {
    // Repository class that encapsulates data access via the DAO

    // This function returns the feature matrix for the last n seconds
    suspend fun getFeaturesMatrixForLastNSeconds(nSeconds: Int): Array<FloatArray> {

        // Sampling frequency of the EEG data (100 Hz)
        val samplingFreq = 100

        // Calculates how many samples are needed to cover n seconds (e.g., 10s * 100Hz = 1000 samples)
        val samplesNeeded = nSeconds * samplingFreq
        
        // Retrieves all EEG samples from the database ordered by ascending timestamp
        val allSamples = dao.getAllSamplesOrderedByTimestamp()

        // Throws an exception if there aren't enough samples to cover the requested period
        if (allSamples.size < samplesNeeded) {
            throw IllegalArgumentException("Not enough data samples in database")
        }

        // Takes only the last samplesNeeded samples, i.e., the last n seconds of data
        val recentSamples = allSamples.takeLast(samplesNeeded)
        
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

        // Passes the data matrix and sampling frequency to the EegFeatureExtractor
        return EegFeatureExtractor.extractFeaturesMatrix(chData, samplingFreq)
    }
}
