import os
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import accuracy_score, precision_score, recall_score
from sklearn.preprocessing import LabelEncoder
from sklearn.metrics import confusion_matrix, classification_report
from tqdm import tqdm


# --- Dataset Pre Processing ---
NUM_CLASSES = 4
INPUT_ROWS = 6  # lines to use in each matrix: from 2 to 7 (6 channels, line 1 is title line, 8 is channel7 not used)
# Best features selected of our app
SELECTED_FEATURES = [
    "Abs_beta_Power", "RMS", "POWER", "Theta_to_Alpha_Ratio", "Rel_delta_Power", "VAR",
    "Rel_theta_Power", "FORM FACTOR", "Abs_theta_Power", "Abs_alpha_Power", "PULSE INDICATOR",
    "Spectral_Entropy", "Rel_alpha_Power", "Theta_Alpha_to_Beta_Ratio", "Abs_delta_Power"
]
DATA_PATH = './'

def load_data_from_folder(folder_path):
    X_list = []
    y_list = []

    labels_file = os.path.join(DATA_PATH, f"{folder_path}_labels.csv")
    labels_df = pd.read_csv(labels_file, skiprows=1, header=None, names=["file", "label"])
    label_dict = dict(zip(labels_df["file"], labels_df["label"]))

    folder_full_path = os.path.join(DATA_PATH, folder_path)
    file_names = [f for f in os.listdir(folder_full_path) if f.endswith('.xlsx')]

    for file_name in tqdm(file_names, desc=f"Loading {folder_path} data"):
        file_path = os.path.join(folder_full_path, file_name)

        if file_name not in label_dict:
            print(f"{file_name} skipped (no label).")
            continue

        df_full = pd.read_excel(file_path, engine='openpyxl')

        # Taking only the  SELECTED_FEATURES (that matrix has 29 feature, we use only the best 15)
        selected_cols = [col for col in df_full.columns if col in SELECTED_FEATURES]

        # Line from 2 to 7 (this function starts count from 0, excel from 1)
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

# --- Model ---
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

    # --- Classification Report for each class ---
    print("\nClassification Report:")
    print(classification_report(y_true, y_pred, zero_division=0))


# --- Main in wich the model is addestred ---
if __name__ == "__main__":
    print("Loading data...")
    X_train, y_train_raw = load_data_from_folder('train')
    X_val, y_val_raw = load_data_from_folder('validation')
    X_test, y_test_raw = load_data_from_folder('test')

    y_train = preprocess_labels(y_train_raw, NUM_CLASSES)
    y_val = preprocess_labels(y_val_raw, NUM_CLASSES)
    y_test = preprocess_labels(y_test_raw, NUM_CLASSES)

    print(f"Train shape: {X_train.shape}, {y_train.shape}")
    print(f"Val shape  : {X_val.shape}, {y_val.shape}")
    print(f"Test shape : {X_test.shape}, {y_test.shape}")

    model = SeleneModel(inputShape=(X_train.shape[1],), numClasses=NUM_CLASSES)
    run_training(model, X_train.astype(np.float32), y_train.astype(np.float32),
                 X_val.astype(np.float32), y_val.astype(np.float32),
                 batch_size=1, epochs=10000, patience=200) # batch size = 1 because our app use one array at time

    # Freezing the layers in order to can perform fine tuning 
    model.model.get_layer('head').trainable = False
    model.model.get_layer('hl1').trainable = False
    model.model.get_layer('hl2').trainable = False

    # Test for evaluation
    evaluate_model(model, X_test.astype(np.float32), y_test.astype(np.float32))

    # Saving the model as .tflite
    tf.saved_model.save(model, "saved_model", signatures={
        "train": model.train,
        "predict": model.predict,
        "save": model.save,
        "load_weights": model.load_weights
    })
    converter = tf.lite.TFLiteConverter.from_saved_model("saved_model")
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS, tf.lite.OpsSet.SELECT_TF_OPS]
    converter.allow_custom_ops = True
    converter.experimental_enable_resource_variables = True
    tflite_model = converter.convert()
    with open("trainable_model.tflite", "wb") as f:
        f.write(tflite_model)
    print("Modello convertito in trainable_model.tflite")

#STEPS PERFORMED
# 1. Model declaration
# 2. Training with early stopping
# 3. Layer freezing
# 4. Saving the model in .tflite
