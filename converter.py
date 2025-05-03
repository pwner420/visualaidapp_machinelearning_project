import os
import tensorflow as tf
from tensorflow.keras import mixed_precision

# 1️⃣ Enable mixed precision training (if your model used it during training)
# This helps speed up training and reduce memory usage by using float16 where possible
mixed_precision.set_global_policy("mixed_float16")

# 2️⃣ Define model parameters
vocab_size = 10000    # Size of the vocabulary (output classes)
max_len = 15          # Maximum length of the caption sequence

# 3️⃣ Define the encoder: MobileNetV2 (pretrained on ImageNet)
#    - Include no classification head (include_top=False)
#    - Use average pooling to reduce spatial dimensions
encoder = tf.keras.applications.MobileNetV2(
    input_shape=(224, 224, 3),   # Input image shape
    include_top=False,           # Exclude the top dense layers
    pooling="avg",               # Global average pooling
    weights="imagenet"           # Load pretrained ImageNet weights
)
encoder.trainable = False  # Freeze encoder during training/inference

# 4️⃣ Define the decoder model as a function
def make_decoder(vocab_size, embed_dim=128, units=256):
    # Input: image features (from encoder)
    feat_in = tf.keras.Input(shape=(1280,), name="image_feat")

    # Input: tokenized caption sequence
    seq_inp = tf.keras.Input(shape=(max_len,), dtype="int32", name="seq_in")

    # Embedding layer: convert word indices into dense vectors
    emb = tf.keras.layers.Embedding(vocab_size, embed_dim)(seq_inp)

    # Repeat image features across the sequence length
    x1 = tf.keras.layers.RepeatVector(max_len)(feat_in)

    # Concatenate image features and embedded words along the time axis
    x = tf.keras.layers.Concatenate()([x1, emb])

    # GRU layer: process the concatenated input sequence
    x = tf.keras.layers.GRU(
        units,
        return_sequences=True,              # Keep sequence output for each time step
        recurrent_activation="sigmoid",     # Use sigmoid instead of default tanh
        reset_after=True                    # GRU optimization for newer Keras versions
    )(x)

    # Dense output layer (per time step), use softmax to predict token probabilities
    out = tf.keras.layers.TimeDistributed(
        tf.keras.layers.Dense(
            vocab_size,
            activation="softmax",
            dtype="float32"  # Ensure output stays in float32 even if model uses mixed precision
        )
    )(x)

    # Return the decoder model
    return tf.keras.Model([feat_in, seq_inp], out, name="decoder")

# Build the decoder
decoder = make_decoder(vocab_size)

# 5️⃣ Assemble the full image captioning model
# Input: image
img_input = tf.keras.Input(shape=(224, 224, 3), name="img")

# Input: caption sequence
cap_input = tf.keras.Input(shape=(None,), name="cap")  # dynamic length supported

# Pass image through encoder
feats = encoder(img_input)

# Pass image features and caption tokens to decoder
caps_out = decoder([feats, cap_input])

# Build the complete model: image and caption in → predicted caption out
model = tf.keras.Model([img_input, cap_input], caps_out)

# 6️⃣ Load pretrained weights from .h5 file (assumes matching architecture)
model.load_weights("best_weights.h5")
print("✅ weights loaded ✅")

# 7️⃣ Convert the model to TensorFlow Lite format
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# Enable optimizations (like dynamic range quantization)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

# Allow fallback to TF ops not yet supported by TFLite
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,   # Standard TFLite ops
    tf.lite.OpsSet.SELECT_TF_OPS      # TF fallback ops
]

# Optional: workaround for advanced ops
converter._experimental_lower_tensor_list_ops = False

# Perform the actual conversion
tflite_model = converter.convert()

# 8️⃣ Save the converted .tflite model to disk
with open("model.tflite", "wb") as f:
    f.write(tflite_model)

print("✅ wrote model ✅")
import os
import tensorflow as tf
from tensorflow.keras import mixed_precision

# 1️⃣ Enable mixed precision training (if your model used it during training)
# This helps speed up training and reduce memory usage by using float16 where possible
mixed_precision.set_global_policy("mixed_float16")

# 2️⃣ Define model parameters
vocab_size = 10000    # Size of the vocabulary (output classes)
max_len = 15          # Maximum length of the caption sequence

# 3️⃣ Define the encoder: MobileNetV2 (pretrained on ImageNet)
#    - Include no classification head (include_top=False)
#    - Use average pooling to reduce spatial dimensions
encoder = tf.keras.applications.MobileNetV2(
    input_shape=(224, 224, 3),   # Input image shape
    include_top=False,           # Exclude the top dense layers
    pooling="avg",               # Global average pooling
    weights="imagenet"           # Load pretrained ImageNet weights
)
encoder.trainable = False  # Freeze encoder during training/inference

# 4️⃣ Define the decoder model as a function
def make_decoder(vocab_size, embed_dim=128, units=256):
    # Input: image features (from encoder)
    feat_in = tf.keras.Input(shape=(1280,), name="image_feat")

    # Input: tokenized caption sequence
    seq_inp = tf.keras.Input(shape=(max_len,), dtype="int32", name="seq_in")

    # Embedding layer: convert word indices into dense vectors
    emb = tf.keras.layers.Embedding(vocab_size, embed_dim)(seq_inp)

    # Repeat image features across the sequence length
    x1 = tf.keras.layers.RepeatVector(max_len)(feat_in)

    # Concatenate image features and embedded words along the time axis
    x = tf.keras.layers.Concatenate()([x1, emb])

    # GRU layer: process the concatenated input sequence
    x = tf.keras.layers.GRU(
        units,
        return_sequences=True,              # Keep sequence output for each time step
        recurrent_activation="sigmoid",     # Use sigmoid instead of default tanh
        reset_after=True                    # GRU optimization for newer Keras versions
    )(x)

    # Dense output layer (per time step), use softmax to predict token probabilities
    out = tf.keras.layers.TimeDistributed(
        tf.keras.layers.Dense(
            vocab_size,
            activation="softmax",
            dtype="float32"  # Ensure output stays in float32 even if model uses mixed precision
        )
    )(x)

    # Return the decoder model
    return tf.keras.Model([feat_in, seq_inp], out, name="decoder")

# Build the decoder
decoder = make_decoder(vocab_size)

# 5️⃣ Assemble the full image captioning model
# Input: image
img_input = tf.keras.Input(shape=(224, 224, 3), name="img")

# Input: caption sequence
cap_input = tf.keras.Input(shape=(None,), name="cap")  # dynamic length supported

# Pass image through encoder
feats = encoder(img_input)

# Pass image features and caption tokens to decoder
caps_out = decoder([feats, cap_input])

# Build the complete model: image and caption in → predicted caption out
model = tf.keras.Model([img_input, cap_input], caps_out)

# 6️⃣ Load pretrained weights from .h5 file (assumes matching architecture)
model.load_weights("best_weights.h5")
print("✅ weights loaded ✅")

# 7️⃣ Convert the model to TensorFlow Lite format
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# Enable optimizations (like dynamic range quantization)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

# Allow fallback to TF ops not yet supported by TFLite
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,   # Standard TFLite ops
    tf.lite.OpsSet.SELECT_TF_OPS      # TF fallback ops
]

# Optional: workaround for advanced ops
converter._experimental_lower_tensor_list_ops = False

# Perform the actual conversion
tflite_model = converter.convert()

# 8️⃣ Save the converted .tflite model to disk
with open("model.tflite", "wb") as f:
    f.write(tflite_model)

print("✅ wrote model ✅")
