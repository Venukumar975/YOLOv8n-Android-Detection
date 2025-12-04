# 📷 Multi-Class Object Detection on Android (YOLOv8n)

An offline, lightweight Android application capable of detecting **20 classes** of objects (Vehicles, Animals, Furniture) in real-time without internet connectivity. Built using **YOLOv8 Nano** and **TensorFlow Lite**.

## 🚀 Features
- **Offline Inference:** Runs entirely on-device using TFLite (FP16 Quantization).
- **20 Detectable Classes:** Including Person, Car, Dog, Cat, Chair, Bottle, etc.
- **Multilingual Support:** Interface available in English, Hindi, Tamil, Telugu, Marathi, and Bengali.
- **Privacy First:** No images are sent to the cloud.

## 🛠️ Tech Stack
- **Model Training:** YOLOv8n (PyTorch) trained on PASCAL VOC dataset.
- **Optimization:** Exported to TFLite with FP16 quantization for speed.
- **Android App:** Native Android (Kotlin) with CameraX API.

## 📂 Project Structure
- `YOLO_Training_Notebook.ipynb` -> The Google Colab file used for training & conversion.
- `app/` -> Source code for the Android application.
- `models/` -> Contains the `yolov8n.tflite` file.

## 📸 Screenshots
*(You can upload the 4 images from your paper here later)*

## 👨‍💻 Authors
- **P. Venu Kumar**
- **P. Revanth Reddy**
