import os
import tensorflow as tf
from tensorflow.keras import mixed_precision

# 1) enable mixed precision if you used it
mixed_precision.set_global_policy("mixed_float16")

# 2) re-build your model architecture exactly as before
vocab_size = 10000
max_len    = 15

# — encoder (frozen MobileNetV2) —
encoder = tf.keras.applications.MobileNetV2(
    input_shape=(224,224,3),
    include_top=False,
    pooling="avg",
    weights="imagenet")
encoder.trainable = False

# — decoder factory —
def make_decoder(vocab_size, embed_dim=128, units=256):
    feat_in = tf.keras.Input((1280,), name="image_feat")
    seq_inp = tf.keras.Input((max_len,), dtype="int32", name="seq_in")
    emb     = tf.keras.layers.Embedding(vocab_size, embed_dim)(seq_inp)
    x1      = tf.keras.layers.RepeatVector(max_len)(feat_in)
    x       = tf.keras.layers.Concatenate()([x1, emb])
    x       = tf.keras.layers.GRU(
                  units,
                  return_sequences=True,
                  recurrent_activation="sigmoid",
                  reset_after=True)(x)
    out     = tf.keras.layers.TimeDistributed(
                  tf.keras.layers.Dense(
                    vocab_size,
                    activation="softmax",
                    dtype="float32"))(x)
    return tf.keras.Model([feat_in, seq_inp], out, name="decoder")

decoder = make_decoder(vocab_size)

# — full model (image + caption in → caption out) —
img_input = tf.keras.Input((224,224,3), name="img")
cap_input = tf.keras.Input((None,),      name="cap")
feats     = encoder(img_input)
caps_out  = decoder([feats, cap_input])
model     = tf.keras.Model([img_input, cap_input], caps_out)

# 3) load your weights-only H5
model.load_weights("best_weights3.h5")
print("✅ weights loaded.")

# 4) convert **directly** from this Keras model to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]   # dynamic-range quantization
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,    # normal TFLite kernels
    tf.lite.OpsSet.SELECT_TF_OPS       # fallback to TF ops for anything else
]
converter._experimental_lower_tensor_list_ops = False
tflite_model = converter.convert()

# 5) save out
with open("model_optimized9.tflite", "wb") as f:
    f.write(tflite_model)
print("✅ wrote model✅✅✅✅✅")
