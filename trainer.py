import os
import tensorflow as tf
from tensorflow.keras import mixed_precision
import numpy as np
from tensorflow.keras.optimizers import Adam

# Allow GPU memory growth
os.environ["TF_FORCE_GPU_ALLOW_GROWTH"] = "true"
# Use all visible GPUs
os.environ.pop("CUDA_VISIBLE_DEVICES", None)

# Set mixed-precision policy
mixed_precision.set_global_policy("mixed_float16")
print("Compute dtype:", mixed_precision.global_policy().compute_dtype)  # float16
print("Variable dtype:", mixed_precision.global_policy().variable_dtype)  # float32

# Paths & caption loading (run this first)
images_dir    = '/content/flickr8k/Images'
captions_file = '/content/flickr8k/captions.txt'

def load_captions(fn):
    caps = {}
    with open(fn) as f:
        next(f)
        for line in f:
            img, cap = line.strip().split(',', 1)
            caps.setdefault(img, []).append(cap)
    return caps

captions_dict = load_captions(captions_file)

vocab_size = 10000  

# 2) Tokenize & build your lists
tokenizer = tf.keras.preprocessing.text.Tokenizer(
    num_words=vocab_size, oov_token="<unk>"
)
all_caps = [c for caps in captions_dict.values() for c in caps]
tokenizer.fit_on_texts(all_caps)

max_len = 15
image_paths, captions_input, captions_output = [], [], []

for img_id, caps in captions_dict.items():
    for c in caps:
        seq = tokenizer.texts_to_sequences([c])[0]
        seq_in  = [tokenizer.word_index["<unk>"]] + seq
        seq_out = seq + [tokenizer.word_index["<unk>"]]
        seq_in  = tf.keras.preprocessing.sequence.pad_sequences(
                      [seq_in], maxlen=max_len, padding='post'
                  )[0]
        seq_out = tf.keras.preprocessing.sequence.pad_sequences(
                      [seq_out], maxlen=max_len, padding='post'
                  )[0]
        image_paths.append(os.path.join(images_dir, img_id))
        captions_input.append(seq_in)
        captions_output.append(seq_out)

captions_input  = np.array(captions_input)
captions_output = np.expand_dims(captions_output, -1)


# Define the loader + pipeline
batch_size = 1024

def load_pair(path, cap_in, cap_out):
    img = tf.io.read_file(path)
    img = tf.image.decode_jpeg(img, channels=3)
    img = tf.image.resize(img, (224,224)) / 255.0
    return (img, cap_in), cap_out

ds = (
    tf.data.Dataset
      .from_tensor_slices((image_paths, captions_input, captions_output))
      .shuffle(buffer_size=5000)
      .map(load_pair, num_parallel_calls=tf.data.AUTOTUNE)
      # .cache()            # remove or replace with .cache("disk_cache.tf-data")
      .batch(batch_size)
      .prefetch(tf.data.AUTOTUNE)
)
total = ds.cardinality().numpy()
val_ds = ds.take(total//10)

strategy = tf.distribute.MirroredStrategy()
with strategy.scope():
    # Encoder — MobileNetV2 frozen
    encoder = tf.keras.applications.MobileNetV2(
        input_shape=(224,224,3),
        include_top=False,
        pooling="avg",
        weights="imagenet")
    encoder.trainable = False

    # Decoder
    def make_decoder(vocab_size, embed_dim=128, units=256):
        feat_in = tf.keras.Input((1280,), name="image_feat")
        seq_inp = tf.keras.Input((None,), dtype="int32", name="seq_in")
        emb     = tf.keras.layers.Embedding(vocab_size, embed_dim)(seq_inp)
        # repeat image feature across time
        x1      = tf.keras.layers.RepeatVector(max_len)(feat_in)
        x       = tf.keras.layers.Concatenate()([x1, emb])
        x       = tf.keras.layers.GRU(units,
                                      return_sequences=True,
                                      recurrent_activation="sigmoid",
                                      reset_after=True)(x)
        out     = tf.keras.layers.TimeDistributed(
                      tf.keras.layers.Dense(vocab_size, activation="softmax", dtype="float32")
                  )(x)
        return tf.keras.Model([feat_in, seq_inp], out, name="decoder")

    decoder = make_decoder(vocab_size)

    # Full model
    img_input = tf.keras.Input((224,224,3), name="img")
    cap_input = tf.keras.Input((None,), name="cap")
    feats     = encoder(img_input)
    caps_out  = decoder([feats, cap_input])
    model     = tf.keras.Model([img_input, cap_input], caps_out)

    # Compile
    model.compile(
        optimizer="adam",
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )


ckpt_cb = tf.keras.callbacks.ModelCheckpoint(
    "best_weights.h5",
    save_best_only=True,
    monitor="loss",
    verbose=1
)
early = tf.keras.callbacks.EarlyStopping(
    monitor="val_loss", patience=3, restore_best_weights=True)
reduce_lr = tf.keras.callbacks.ReduceLROnPlateau(
    monitor="val_loss", factor=0.5, patience=2)
model.fit(ds, epochs=1000, callbacks=[ckpt_cb, early, reduce_lr])
# Load best weights
model.load_weights("best_weights.h5")
with strategy.scope():
    model.compile(
        optimizer=Adam(learning_rate=1e-5),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

# 3) Fine-tune
history_finetune = model.fit(
    ds,
    validation_data=val_ds,
    epochs=400,
    callbacks=[ckpt_cb, early, reduce_lr]
)