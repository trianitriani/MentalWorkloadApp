import numpy as np
from scipy.fft import fft
from numpy import log as ln
from math import cos, pi, sqrt, tan, sin

# Python translation of EegFeatureExtractor.kt
class EegFeatureExtractor:

    @staticmethod
    def rms(signal):
        return np.sqrt(np.mean(np.square(signal)))

    @staticmethod
    def variance(signal):
        mean = np.mean(signal)
        return np.mean(np.square(signal - mean))

    @staticmethod
    def power(signal):
        return np.sum(np.square(signal)) / len(signal)

    @staticmethod
    def form_factor(signal):
        return EegFeatureExtractor.rms(signal) / np.abs(np.mean(signal))

    @staticmethod
    def pulse_indicator(signal):
        rms_val = EegFeatureExtractor.rms(signal)
        return np.max(signal) / rms_val if rms_val != 0 else 0.0

    @staticmethod
    def compute_psd(signal, sampling_rate):
        n = len(signal)
        fft_result = fft(signal, n*2)
        psd = np.abs(fft_result[:n])**2 / (n * sampling_rate)
        freqs = np.linspace(0, sampling_rate / 2, n, endpoint=False)
        return freqs, psd

    @staticmethod
    def band_power(freqs, psd, low, high):
        indices = np.logical_and(freqs >= low, freqs <= high)
        return np.sum(psd[indices])

    @staticmethod
    def spectral_entropy(psd):
        psd_sum = np.sum(psd)
        if psd_sum == 0:
            return 0.0
        norm_psd = psd / psd_sum
        norm_psd = norm_psd[norm_psd > 0]  # Avoid log(0)
        return -np.sum(norm_psd * ln(norm_psd))

    @staticmethod
    def extract_features_matrix(preprocessed_channels, sampling_rate):

        # In this python evaluation scripts, preprocessing is performed in addest.py before the use of this function
        features_matrix = []

        for ch_idx, signal in enumerate(preprocessed_channels):
            # Subsempling
            low_filtered_signal = EegFeatureExtractor.butterworth_lowpass_filter(signal, cutoff_freq=50.0, sampling_rate=sampling_rate)
            subsampled_signal = EegFeatureExtractor.subsample(low_filtered_signal, factor=5)
            notch_filtered_signal = EegFeatureExtractor.fast_notch_50hz(subsampled_signal, sampling_rate // 5)

            freqs, psd = EegFeatureExtractor.compute_welch_psd(notch_filtered_signal, sampling_rate // 5)

            # Features extraction
            abs_delta = EegFeatureExtractor.band_power(freqs, psd, 0.5, 4.0)
            rel_delta = abs_delta / np.sum(psd)
            abs_theta = EegFeatureExtractor.band_power(freqs, psd, 4.0, 8.0)
            rel_theta = abs_theta / np.sum(psd)
            abs_alpha = EegFeatureExtractor.band_power(freqs, psd, 8.0, 13.0)
            rel_alpha = abs_alpha / np.sum(psd)
            abs_beta = EegFeatureExtractor.band_power(freqs, psd, 13.0, 30.0)
            rel_beta = abs_beta / np.sum(psd)

            theta_alpha_to_beta = (abs_theta + abs_alpha) / abs_beta if abs_beta != 0 else 0
            theta_to_alpha = abs_theta / abs_alpha if abs_alpha != 0 else 0
            alpha_to_theta = abs_alpha / abs_theta if abs_theta != 0 else 0

            # Creation of 6x15
            features = np.array([
                abs_beta,
                EegFeatureExtractor.rms(signal),
                EegFeatureExtractor.power(signal),
                theta_to_alpha,
                rel_delta,
                EegFeatureExtractor.variance(signal),
                rel_theta,
                EegFeatureExtractor.form_factor(signal),
                abs_theta,
                abs_alpha,
                EegFeatureExtractor.pulse_indicator(signal),
                EegFeatureExtractor.spectral_entropy(psd),
                rel_alpha,
                theta_alpha_to_beta,
                abs_delta
            ], dtype=np.float32)
            features_matrix.append(features)

        return np.array(features_matrix, dtype=np.float32)

    @staticmethod
    def flatten_features_matrix(features_matrix):
        return features_matrix.flatten()

    @staticmethod
    def hamming_window(N: int) -> np.ndarray:
        return np.array([0.54 - 0.46 * cos(2.0 * pi * i / (N - 1)) for i in range(N)])

    @staticmethod
    def compute_welch_psd(signal: np.ndarray, sampling_rate: int):
        segment_length = 2000  # 4 second window at 500 Hz sampling rate
        overlap = segment_length // 2
        step = segment_length - overlap

        window = EegFeatureExtractor.hamming_window(segment_length)
        window_power = np.sum(window ** 2) / segment_length

        num_segments = (len(signal) - overlap) // step
        psd = np.zeros(segment_length // 2 + 1, dtype=np.float64)

        for i in range(num_segments):
            start = i * step
            segment = signal[start:start + segment_length].copy()

            segment *= window

            fft_result = fft(segment, n=segment_length * 2)

            for j in range(segment_length // 2 + 1):
                re = fft_result[j].real
                im = fft_result[j].imag
                psd[j] += (re * re + im * im) / (segment_length * sampling_rate * window_power)

        psd /= num_segments

        freqs = np.array([j * sampling_rate / segment_length for j in range(segment_length // 2 + 1)])
        return freqs, psd

    @staticmethod
    def butterworth_lowpass_filter(signal: np.ndarray, cutoff_freq: float, sampling_rate: float) -> np.ndarray:
        filtered = np.zeros_like(signal)

        wc = tan(pi * cutoff_freq / sampling_rate)

        k1 = sqrt(2.0) * wc
        k2 = wc * wc
        a = k2 / (1 + k1 + k2)
        b = 2 * a
        c = a
        d = 2 * (k2 - 1) / (1 + k1 + k2)
        e = (1 - k1 + k2) / (1 + k1 + k2)

        x1 = x2 = y1 = y2 = 0.0

        for i, x0 in enumerate(signal):
            y0 = a * x0 + b * x1 + c * x2 - d * y1 - e * y2
            filtered[i] = y0

            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0

        return filtered

    @staticmethod
    def subsample(signal: np.ndarray, factor: int) -> np.ndarray:
        new_size = len(signal) // factor
        return np.array([signal[i * factor] for i in range(new_size)])

    @staticmethod
    def fast_notch_50hz(signal: np.ndarray, sampling_rate: int) -> np.ndarray:
        f0 = 50.0
        Q = 30.0

        w0 = 2 * pi * f0 / sampling_rate
        alpha = sin(w0) / (2 * Q)

        b0 = 1.0
        b1 = -2 * cos(w0)
        b2 = 1.0
        a0 = 1 + alpha
        a1 = -2 * cos(w0)
        a2 = 1 - alpha

        norm_b0 = b0 / a0
        norm_b1 = b1 / a0
        norm_b2 = b2 / a0
        norm_a1 = a1 / a0
        norm_a2 = a2 / a0

        output = np.zeros_like(signal)
        x1 = x2 = y1 = y2 = 0.0

        for i, x0 in enumerate(signal):
            y0 = norm_b0 * x0 + norm_b1 * x1 + norm_b2 * x2 - norm_a1 * y1 - norm_a2 * y2
            output[i] = y0

            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0

        return output

