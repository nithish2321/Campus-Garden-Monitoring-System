# 🌱 Campus Garden Monitoring System

[Demo video](https://drive.google.com/file/d/1f7uCaxHLBeno2tk70h8aN1_ptb4YayYt/view?usp=sharing)

A smart and scalable waste detection system for campus environments using YOLOv11, Android, Flask, MongoDB, React.js and Node.js.

---

## 🚀 Features

### 🔍 Waste Detection with YOLOv11
- Detects **upright bins**, **tilted bins**, and **scattered trash** using a custom-trained YOLOv11 model.

### 📱 Android Client
- Captures images and uploads them to a Flask server via public port sharing (VS Code).
- Retrieves inference results after processing.

### 🧠 Server-Side Architecture
- **Image Watcher & Processor**:  
  A Python socket-based script that detects new uploads, downloads them, processes them using YOLOv11, and stores metadata and images in MongoDB.

- **File Serving & Retrieval API**:  
  Flask RESTful endpoints to fetch processed results and serve image data to clients (e.g., mobile or monitoring dashboard).

### 🌐 Real-Time Monitoring Dashboard
- Built using **Node.js** to fetch and display the latest processed results from MongoDB on a dynamic HTML interface.

---

## 🛠️ Tech Stack

| Component     | Technology       |
|---------------|------------------|
| Object Detection | YOLOv11 (custom-trained) |
| Android App  | Java + XML  |
| Backend      | Flask + Python Sockets |
| Database     | MongoDB |
| Dashboard    | React.js + Node.js |
| Communication | REST APIs + Socket Monitoring |

---
