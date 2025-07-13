import tensorflow as tf
import numpy as np
from sklearn.metrics import accuracy_score, precision_score, recall_score

#Model

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
        self.optimizer = tf.keras.optimizers.SGD(learning_rate=0.001, momentum=0.8)

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
    def classify(self, x):
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
    
#function for loop training using batches
def run_training(model, x_train, y_train, x_val=None, y_val=None, batch_size=32, epochs=10):
    dataset = tf.data.Dataset.from_tensor_slices((x_train, y_train))
    dataset = dataset.shuffle(buffer_size=len(x_train)).batch(batch_size)

    for epoch in range(epochs):
        print(f"\nEpoch {epoch + 1}/{epochs}")
        epoch_loss = []

        for step, (batch_x, batch_y) in enumerate(dataset):
            result = model.train(batch_x, batch_y)
            batch_loss = result["loss"].numpy()
            epoch_loss.append(batch_loss)
            print(f"  Step {step + 1}: loss = {batch_loss:.4f}")

        avg_loss = sum(epoch_loss) / len(epoch_loss)
        print(f"Epoch {epoch + 1} average training loss: {avg_loss:.4f}")

        # Optional validation
        if x_val is not None and y_val is not None:
            # Run prediction
            logits = model.model(x_val, training=False)
            probs = tf.nn.softmax(logits)
            val_loss = tf.reduce_mean(
                tf.keras.losses.categorical_crossentropy(y_val, probs)
            ).numpy()

            # Convert to numpy for metrics
            y_true = np.argmax(y_val, axis=1)
            y_pred = np.argmax(probs.numpy(), axis=1)

            val_accuracy = accuracy_score(y_true, y_pred)
            val_precision = precision_score(y_true, y_pred, average='macro', zero_division=0)
            val_recall = recall_score(y_true, y_pred, average='macro', zero_division=0)

            print(f"Validation\n    loss    : {val_loss:.4f}\n    accuracy: {val_accuracy:.4f}\n    precision: {val_precision:.4f}\n    recall   : {val_recall:.4f}")

#operations

# Create and configure the model
model = SeleneModel()

#same as fit model
run_training(model, x_train, y_train, x_val=None, y_val=None, batch_size=32, epochs=10)

# Freeze the first few layers, must be done before converting
model.model.get_layer('flatten').trainable = False
model.model.get_layer('hl1').trainable = False
model.model.get_layer('hl2').trainable = False

# Save the model with signatures, signatures are method that can be called also from the tf lite model
tf.saved_model.save(model, "saved_model",
    signatures={
        "train": model.train,
        "predict": model.predict,
        "save": model.save,
        "load": model.load
    }
)

# Convert to TFLite with training support
converter = tf.lite.TFLiteConverter.from_saved_model("saved_model")
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
]
converter.allow_custom_ops = True
converter.experimental_enable_resource_variables = True
tflite_model = converter.convert()

with open("trainable_model.tflite", "wb") as f:
    f.write(tflite_model)


#STEPS
# 1.Dichiarare il modello
# 2. Effettuare il training
# 3. freezare i layer
# 4.salvare modello
# 5.convertirlo in tflite
