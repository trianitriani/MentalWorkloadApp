import os
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import accuracy_score, precision_score, recall_score
from sklearn.preprocessing import LabelEncoder
from sklearn.metrics import confusion_matrix, classification_report
from tqdm import tqdm
import sqlite3
from collections import defaultdict
from featureExPy import EegFeatureExtractor
from eeg_preprocessing import preprocess_matrix
import random
import json


# --- Dataset Pre Processing ---
NUM_CLASSES = 4
INPUT_ROWS = 6  # lines to use in each matrix: from 2 to 7 (6 channels, line 1 is title line, 8 is channel7 not used)
# Selected features used in the app
SELECTED_FEATURES = [
    "Abs_beta_Power", "RMS", "POWER", "Theta_to_Alpha_Ratio", "Rel_delta_Power", "VAR",
    "Rel_theta_Power", "FORM FACTOR", "Abs_theta_Power", "Abs_alpha_Power", "PULSE INDICATOR",
    "Spectral_Entropy", "Rel_alpha_Power", "Theta_Alpha_to_Beta_Ratio", "Abs_delta_Power"
]
DATA_PATH = './'

def load_data_from_folder(folder_path):
    X_list = []
    y_list = []

    # Identification of the type: train/validation/test analyzing the name
    dataset_type = os.path.basename(folder_path)  # it takes 'train', 'validation', 'test'
    labels_file = os.path.join(os.path.dirname(folder_path), f"{dataset_type}_labels.csv")

    labels_df = pd.read_csv(labels_file, skiprows=1, header=None, names=["file", "label"])
    label_dict = dict(zip(labels_df["file"], labels_df["label"]))

    file_names = [f for f in os.listdir(folder_path) if f.endswith('.xlsx')]

    for file_name in tqdm(file_names, desc=f"Loading {folder_path} data"):
        file_path = os.path.join(folder_path, file_name)

        if file_name not in label_dict:
            print(f"{file_name} skipped (no label).")
            continue

        df_full = pd.read_excel(file_path, engine='openpyxl')
        selected_cols = [col for col in df_full.columns if col in SELECTED_FEATURES]
        df_selected = df_full.loc[0:, selected_cols]

        data_array = df_selected.values
        if data_array.shape != (INPUT_ROWS, len(SELECTED_FEATURES)):
            raise ValueError(f"{file_name} shape mismatch: {data_array.shape}")

        X_list.append(data_array.flatten())
        y_list.append(label_dict[file_name])

    X = np.array(X_list)
    y = np.array(y_list)
    return X, y


# Label processing
def preprocess_labels(y, num_classes):
    le = LabelEncoder()
    y_encoded = le.fit_transform(y)
    y_encoded = y_encoded % num_classes
    y_onehot = tf.keras.utils.to_categorical(y_encoded, num_classes)
    return y_onehot

# --- The model ---
class SeleneModel(tf.Module):
    def __init__(self, inputShape=(90,), numClasses=4):
        super().__init__()
        self.model = tf.keras.Sequential([
            tf.keras.layers.Flatten(input_shape=inputShape, name='head'),
            tf.keras.layers.Dense(64, activation='relu',kernel_initializer=tf.keras.initializers.HeNormal(), name='hl1'),
            tf.keras.layers.Dense(32, activation='relu',kernel_initializer=tf.keras.initializers.HeNormal(), name='hl2'),
            tf.keras.layers.Dense(16, activation='relu',kernel_initializer=tf.keras.initializers.HeNormal(), name='hl3'),
            tf.keras.layers.Dense(numClasses, name='tail')
        ])
        self.optimizer = tf.keras.optimizers.SGD(learning_rate=0.001, momentum=0.80)

    @tf.function(input_signature=[
        tf.TensorSpec([None, 90], tf.float32),
        tf.TensorSpec([None, 4], tf.float32),
    ])
    def train(self, x, y):
        with tf.GradientTape() as tape:
            logits = self.model(x, training=True)
            loss = tf.reduce_mean(
                tf.keras.losses.categorical_crossentropy(y, logits, from_logits=True)
            )
        gradients = tape.gradient(loss, self.model.trainable_variables)
        clipped_gradients = [tf.clip_by_norm(g, 1.0) for g in gradients]
        self.optimizer.apply_gradients(zip(clipped_gradients, self.model.trainable_variables))
        reshaped_loss = tf.reshape(loss,[1])
        return {"loss": reshaped_loss}

    @tf.function(input_signature=[
        tf.TensorSpec([None, 90], tf.float32),
    ])
    def predict(self, x):
        logits = self.model(x, training=False)
        probabilities = tf.nn.softmax(logits)
        return {"output": probabilities}

    @tf.function(input_signature=[tf.TensorSpec(shape=[], dtype=tf.string)])
    def save(self, checkpoint_path):
        tensor_names = [weight.name for weight in self.model.weights]
        tensors_to_save = [weight.read_value() for weight in self.model.weights]
        tf.raw_ops.Save(
            filename=checkpoint_path,
            tensor_names=tensor_names,
            data=tensors_to_save
        )
        return {"checkpoint_path": checkpoint_path}

    @tf.function(input_signature=[tf.TensorSpec(shape=[], dtype=tf.string)])
    def load_weights(self, checkpoint_path):
        restored_tensors = {}
        for var in self.model.weights:
            restored = tf.raw_ops.Restore(
                file_pattern=checkpoint_path,
                tensor_name=var.name,
                dt=var.dtype
            )
            var.assign(restored)
            restored_tensors[var.name] = restored
        return restored_tensors
    
#function for loop training
def run_training(model, x_train, y_train, x_val=None, y_val=None, batch_size=1, epochs=10, patience=3):
    dataset = tf.data.Dataset.from_tensor_slices((x_train, y_train))
    dataset = dataset.shuffle(buffer_size=len(x_train)).batch(batch_size)

    best_val_loss = float('inf')
    patience_counter = 0

    for epoch in range(epochs):
        print(f"\nEpoch {epoch + 1}/{epochs}")
        epoch_loss = []

        for step, (batch_x, batch_y) in enumerate(dataset):
            result = model.train(batch_x, batch_y)
            batch_loss = result["loss"].numpy()
            epoch_loss.append(batch_loss)
            print(f"  Step {step + 1}: loss = {batch_loss.item():.4f}")

        avg_loss = sum(epoch_loss) / len(epoch_loss)
        print(f"Epoch {epoch + 1} average training loss: {avg_loss.item():.4f}")

        # Early stopping code, checking for validation data
        if x_val is not None and y_val is not None:
            logits = model.model(x_val, training=False)
            probs = tf.nn.softmax(logits)
            val_loss = tf.reduce_mean(
                tf.keras.losses.categorical_crossentropy(y_val, probs)
            ).numpy()

            y_true = np.argmax(y_val, axis=1)
            y_pred = np.argmax(probs.numpy(), axis=1)

            val_accuracy = accuracy_score(y_true, y_pred)
            val_precision = precision_score(y_true, y_pred, average='macro', zero_division=0)
            val_recall = recall_score(y_true, y_pred, average='macro', zero_division=0)

            print(f"Validation\n    loss    : {val_loss:.4f}\n    accuracy: {val_accuracy:.4f}\n    precision: {val_precision:.4f}\n    recall   : {val_recall:.4f}")

            # Check for early stopping
            if val_loss < best_val_loss:
                best_val_loss = val_loss
                patience_counter = 0
                print("  Validation loss improved. Reset patience.")
            else:
                patience_counter += 1
                print(f"  No improvement. Patience {patience_counter}/{patience}")
                if patience_counter >= patience:
                    print(f"Early stopping triggered at epoch {epoch + 1}")
                    break




# Function for final test
def evaluate_model(model, x_test, y_test):
    logits = model.model(x_test, training=False)
    probs = tf.nn.softmax(logits)

    test_loss = tf.reduce_mean(
        tf.keras.losses.categorical_crossentropy(y_test, probs)
    ).numpy()

    y_true = np.argmax(y_test, axis=1)
    y_pred = np.argmax(probs.numpy(), axis=1)

    test_accuracy = accuracy_score(y_true, y_pred)
    test_precision = precision_score(y_true, y_pred, average='macro', zero_division=0)
    test_recall = recall_score(y_true, y_pred, average='macro', zero_division=0)

    print(f"\nTest Evaluation\n    loss     : {test_loss:.4f}\n    accuracy : {test_accuracy:.4f}\n    precision: {test_precision:.4f}\n    recall   : {test_recall:.4f}")

    # --- Printing Confusion Matrix ---
    cm = confusion_matrix(y_true, y_pred)

    print("\nConfusion Matrix:")
    print("          Predicted")
    print("        ", end="")
    for i in range(NUM_CLASSES):
        print(f"{i:^8}", end="")
    print("\nActual")
    for i, row in enumerate(cm):
        print(f"  {i:<5} ", end="")
        for val in row:
            print(f"{val:^8}", end="")
        print()

    # --- Classification Report for each classes ---
    print("\nClassification Report:")
    print(classification_report(y_true, y_pred, zero_division=0))


def balanced_session_split(cursor, num_classes=4, total_test_sessions=14):
    cursor.execute("SELECT DISTINCT session_id FROM SampleEeg ORDER BY session_id")
    all_session_ids = [row[0] for row in cursor.fetchall()]

    class_to_sessions = defaultdict(list)
    for session_id in all_session_ids:
        cursor.execute("SELECT tiredness FROM SampleEeg WHERE session_id = ? LIMIT 1", (session_id,))
        tiredness = cursor.fetchone()[0]
        if tiredness < num_classes:
            class_to_sessions[tiredness].append(session_id)

    test_sessions = []
    train_sessions = []

    for tiredness in range(num_classes):
        sessions = class_to_sessions[tiredness]
        if len(sessions) < 2:
            raise ValueError(f"Classe {tiredness} ha meno di 2 sessioni disponibili.")
        selected_test = random.choice(sessions)
        test_sessions.append(selected_test)
        remaining = [s for s in sessions if s != selected_test]
        train_sessions.extend(remaining)

    remaining_test_needed = total_test_sessions - num_classes
    if remaining_test_needed > 0:
        extra_test = random.sample(train_sessions, remaining_test_needed)
        for s in extra_test:
            train_sessions.remove(s)
        test_sessions.extend(extra_test)

    return train_sessions, test_sessions

#For the test, in selene-db, 34 16000-samples sessions were created
def load_finetune_and_test_data_updated(db_path, block_size=16000):
    x_finetune, y_finetune = [], []
    x_test, y_test = [], []

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT DISTINCT session_id FROM SampleEeg ORDER BY session_id")
    session_ids = [row[0] for row in cursor.fetchall()]

    # This function guarantees at least one session for each label in train and test sets
    training_sessions, testing_sessions = balanced_session_split(cursor)

    for session_id in session_ids:
        cursor.execute("SELECT tiredness FROM SampleEeg WHERE session_id = ? LIMIT 1", (session_id,))
        tiredness = cursor.fetchone()[0]

        cursor.execute("""
            SELECT channel_c1, channel_c2, channel_c3, channel_c4, channel_c5, channel_c6
            FROM SampleEeg
            WHERE session_id = ?
            ORDER BY timestamp
            LIMIT ?
        """, (session_id, block_size))
        all_rows = cursor.fetchall()  

        raw_data = np.array(all_rows)
        if raw_data.shape[0] < block_size:
            continue

        block = raw_data.T  # shape is (6, 16000)
        # Preprocessing using Savitzky-Golay filter and Wavelet trasform
        block = preprocess_matrix(block)
        # Features extraction
        features_matrix = EegFeatureExtractor.extract_features_matrix(block, 500)
        flat_vector = EegFeatureExtractor.flatten_features_matrix(features_matrix)

        if session_id in training_sessions:
            x_finetune.append(flat_vector)
            y_finetune.append(tiredness)
        elif session_id in testing_sessions:
            x_test.append(flat_vector)
            y_test.append(tiredness)

    conn.close()
    return np.array(x_finetune), np.array(y_finetune), np.array(x_test), np.array(y_test)


# --- Main in wich the code is addestred ---
if __name__ == "__main__":
    all_accuracies = []
    all_precisions = []
    all_recalls = []
    all_f1s = []
    fine_tune_accuracies = []
    fine_tune_precisions = []
    fine_tune_recalls = []
    fine_tune_f1s = []
    fine_tune_cms = []
    fine_tune_reports = []

    # Cross validation over 10 folds (stages)
    for fold in range(10):
        stage_path = f"stage_{fold}"
        print(f"\n\n========= FOLD {fold} ({stage_path}) =========")

        # Loading data
        print("Loading data...")
        X_train, y_train_raw = load_data_from_folder(os.path.join(stage_path, "train"))
        X_val, y_val_raw = load_data_from_folder(os.path.join(stage_path, "validation"))
        X_test, y_test_raw = load_data_from_folder(os.path.join(stage_path, "test"))

        y_train = preprocess_labels(y_train_raw, NUM_CLASSES)
        y_val = preprocess_labels(y_val_raw, NUM_CLASSES)
        y_test = preprocess_labels(y_test_raw, NUM_CLASSES)

        print(f"Train shape: {X_train.shape}, {y_train.shape}")
        print(f"Val shape  : {X_val.shape}, {y_val.shape}")
        print(f"Test shape : {X_test.shape}, {y_test.shape}")

        # Creation of the model
        model = SeleneModel(inputShape=(X_train.shape[1],), numClasses=NUM_CLASSES)

        # Training
        run_training(model, X_train.astype(np.float32), y_train.astype(np.float32),
                     X_val.astype(np.float32), y_val.astype(np.float32),
                     batch_size=1, epochs=100, patience=10)

        # Test of the base model
        logits = model.model(X_test.astype(np.float32), training=False)
        probs = tf.nn.softmax(logits)
        y_true = np.argmax(y_test, axis=1)
        y_pred = np.argmax(probs.numpy(), axis=1)

        acc = accuracy_score(y_true, y_pred)
        prec = precision_score(y_true, y_pred, average='macro', zero_division=0)
        rec = recall_score(y_true, y_pred, average='macro', zero_division=0)
        f1 = 2 * (prec * rec) / (prec + rec + 1e-7)

        print(f"\nFOLD {fold} results:")
        print(f"  Accuracy : {acc:.4f}")
        print(f"  Precision: {prec:.4f}")
        print(f"  Recall   : {rec:.4f}")
        print(f"  F1-Score : {f1:.4f}")

        all_accuracies.append(acc)
        all_precisions.append(prec)
        all_recalls.append(rec)
        all_f1s.append(f1)

        print("\n--- FINE TUNING EEG SESSIONS ---")
        x_ft, y_ft_raw, x_test_eeg, y_test_eeg_raw = load_finetune_and_test_data_updated("selene-db")

        # Freezing layers before fine tuning
        model.model.get_layer('head').trainable = False
        model.model.get_layer('hl1').trainable = False
        model.model.get_layer('hl2').trainable = False

        # Fine tuning train
        y_ft = tf.keras.utils.to_categorical(np.array(y_ft_raw) % NUM_CLASSES, NUM_CLASSES)
        run_training(model, x_ft.astype(np.float32), y_ft.astype(np.float32), batch_size=1, epochs=50)

        # Evaluation post fine tuning
        print("\nTest EEG dopo fine tuning:")
        y_pred_ft = []
        losses = []
        y_test_cat = tf.keras.utils.to_categorical(np.array(y_test_eeg_raw) % NUM_CLASSES, NUM_CLASSES)
        for i in range(x_test_eeg.shape[0]):
            input_vector = x_test_eeg[i].astype(np.float32).reshape(1, -1)
            true_label = y_test_cat[i].reshape(1, -1)
            output = model.predict(input_vector)["output"]
            pred_class = tf.argmax(output, axis=1).numpy()[0]
            y_pred_ft.append(pred_class)
            loss = tf.keras.losses.categorical_crossentropy(true_label, output, from_logits=False).numpy()[0]
            losses.append(loss)

        y_pred_ft = np.array(y_pred_ft)
        y_true_ft = np.array(y_test_eeg_raw) % NUM_CLASSES

        val_loss = np.mean(losses)
        val_accuracy = accuracy_score(y_true_ft, y_pred_ft)
        val_precision = precision_score(y_true_ft, y_pred_ft, average='macro', zero_division=0)
        val_recall = recall_score(y_true_ft, y_pred_ft, average='macro', zero_division=0)
        val_f1 = 2 * (val_precision * val_recall) / (val_precision + val_recall + 1e-7)

        print(f"\nFine Tuning - Fold {fold}")
        print(f"  Loss     : {val_loss:.4f}")
        print(f"  Accuracy : {val_accuracy:.4f}")
        print(f"  Precision: {val_precision:.4f}")
        print(f"  Recall   : {val_recall:.4f}")
        print(f"  F1-Score : {val_f1:.4f}")

        fine_tune_accuracies.append(val_accuracy)
        fine_tune_precisions.append(val_precision)
        fine_tune_recalls.append(val_recall)
        fine_tune_f1s.append(val_f1)
        fine_tune_cms.append(confusion_matrix(y_true_ft, y_pred_ft))
        fine_tune_reports.append(classification_report(y_true_ft, y_pred_ft, output_dict=True, zero_division=0))


    '''
    all_confusion_matrices = []
    all_classification_reports = []

    # Calcolo confusion matrix e classification report per ogni fold
    for i in range(10):
        print(f"\n===== CONFUSION MATRIX & REPORT - FOLD {i} =====")
        stage_path = f"stage_{i}"

        X_test, y_test_raw = load_data_from_folder(os.path.join(stage_path, "test"))
        y_test = preprocess_labels(y_test_raw, NUM_CLASSES)

        model = SeleneModel(inputShape=(X_test.shape[1],), numClasses=NUM_CLASSES)
        # (Riaddestra o carica il modello del fold se salvato. Qui si suppone che venga ricalcolato direttamente.)

        # Qui dovresti ricaricare i pesi del modello del fold i se li hai salvati
        # Altrimenti puoi salvarli durante il ciclo prima, oppure ricalcolare il modello anche qui.

        logits = model.model(X_test.astype(np.float32), training=False)
        probs = tf.nn.softmax(logits)
        y_true = np.argmax(y_test, axis=1)
        y_pred = np.argmax(probs.numpy(), axis=1)

        cm = confusion_matrix(y_true, y_pred)
        all_confusion_matrices.append(cm)
        report = classification_report(y_true, y_pred, output_dict=True, zero_division=0)
        all_classification_reports.append(report)

        # Stampa confusion matrix
        print("Confusion Matrix:")
        print("          Predicted")
        print("        ", end="")
        for col in range(NUM_CLASSES):
            print(f"{col:^8}", end="")
        print("\nActual")
        for row_idx, row in enumerate(cm):
            print(f"  {row_idx:<5} ", end="")
            for val in row:
                print(f"{val:^8}", end="")
            print()

        # Stampa classification report
        print("\nClassification Report:")
        print(classification_report(y_true, y_pred, zero_division=0))

    # Media delle confusion matrix
    mean_cm = np.mean(all_confusion_matrices, axis=0).round(2).astype(np.float32)

    print("\n===== MEAN CONFUSION MATRIX OVER 10 FOLDS =====")
    print("          Predicted")
    print("        ", end="")
    for col in range(NUM_CLASSES):
        print(f"{col:^10}", end="")
    print("\nActual")
    for row_idx, row in enumerate(mean_cm):
        print(f"  {row_idx:<5} ", end="")
        for val in row:
            print(f"{val:^10.2f}", end="")
        print()
    '''

    # Aggregated results
    print("\n\n===== FINAL AVERAGED RESULTS OVER 10 FOLDS OF BASE MODEL =====")
    print(f"Mean Accuracy : {np.mean(all_accuracies):.4f} ± {np.std(all_accuracies):.4f}")
    print(f"Mean Precision: {np.mean(all_precisions):.4f} ± {np.std(all_precisions):.4f}")
    print(f"Mean Recall   : {np.mean(all_recalls):.4f} ± {np.std(all_recalls):.4f}")
    print(f"Mean F1-Score : {np.mean(all_f1s):.4f} ± {np.std(all_f1s):.4f}")

    print("\n===== FINE TUNING RESULTS OVER 10 FOLDS =====")
    for i in range(10):
        print(f"\n--- FOLD {i} ---")
        print("Confusion Matrix:")
        cm = fine_tune_cms[i]
        for row in cm:
            print("  ", row)
        print("Classification Report:")
        print(json.dumps(fine_tune_reports[i], indent=2))


    print("\n===== FINAL AVERAGED FINE TUNING METRICS =====")
    print(f"Mean Accuracy : {np.mean(fine_tune_accuracies):.4f} ± {np.std(fine_tune_accuracies):.4f}")
    print(f"Mean Precision: {np.mean(fine_tune_precisions):.4f} ± {np.std(fine_tune_precisions):.4f}")
    print(f"Mean Recall   : {np.mean(fine_tune_recalls):.4f} ± {np.std(fine_tune_recalls):.4f}")
    print(f"Mean F1-Score : {np.mean(fine_tune_f1s):.4f} ± {np.std(fine_tune_f1s):.4f}")