import requests
import socketio
import os
from ultralytics import YOLO
from pymongo import MongoClient
import base64
import shutil
from bson.objectid import ObjectId
import datetime
import pytz

SERVER_URL = "https://6cdftcxg-5000.inc1.devtunnels.ms"
DOWNLOAD_DIR = "uploads"
os.makedirs(DOWNLOAD_DIR, exist_ok=True)

def on_new_file(data):
    """Callback function to handle new file event."""
    document_id = data.get('document_id')  # Get the document ID from the event
    location = data.get('location', 'Unknown')
    print(f"New file uploaded with document_id: {document_id}")

    # Fetch the document from MongoDB to get the image
    MONGO_URI = "mongodb://localhost:27017/"
    client = MongoClient(MONGO_URI)
    db = client["GardenTest"]
    collection = db["inferenceData"]

    document = collection.find_one({"_id": ObjectId(document_id)})
    if not document or "imageData" not in document:
        print(f"No document found for ID: {document_id}")
        return

    # Decode the Base64 image and save it temporarily
    image_data = base64.b64decode(document["imageData"])
    temp_file_path = os.path.join(DOWNLOAD_DIR, f"{document_id}.jpg")
    with open(temp_file_path, "wb") as f:
        f.write(image_data)

    if os.path.exists(temp_file_path):
        print(f"File saved at {temp_file_path}")
        process_file(temp_file_path, document_id, collection)
    else:
        print(f"File {temp_file_path} not found")

def process_file(file_path, document_id, collection):
    model1 = YOLO("trash_detect.pt")  # Adjust model path as needed
    model1.info()
    results = model1(file_path, device="cpu", stream=True, save=True)

    detected_objects = []
    for result in results:
        for obj in result.boxes:
            detected_objects.append((model1.names[int(obj.cls)], int(round(obj.conf[0].item(), 2) * 100)))

    print(f"Detected objects: {detected_objects}")
    obj = [obj[0] for obj in detected_objects]
    detected_dictionary = {i: obj.count(i) for i in list(set(obj))}
    print(f"Summary: {detected_dictionary}")

    # Define paths (adjust as per your setup)
    path = "uploaded_file.avi" if os.path.basename(file_path) == "uploaded_file.mp4" else os.path.basename(file_path)
    source_file = f"C:\\Serve_IQ\\runs\\detect\\predict\\{path}"
    destination_dir = f"C:\\Serve_IQ\\{path}"
    parent_dir = r"C:\Serve_IQ\runs"

    if os.path.exists(source_file):
        if os.path.exists(destination_dir):
            os.remove(destination_dir)
        shutil.move(source_file, destination_dir)
        shutil.rmtree(parent_dir)
        print("File moved and directory deleted successfully")

    final_path = destination_dir if os.path.exists(destination_dir) else file_path
    if path == "uploaded_file.avi":
        os.rename(final_path, final_path[:-4] + ".mp4")
        final_path = final_path[:-4] + ".mp4"

    print(f"Processing file at: {final_path}")
    with open(final_path, "rb") as image_file:
        image_base64 = base64.b64encode(image_file.read()).decode('utf-8')

    # Update the existing document with inference data
    update_data = {
        "processed": True,
        "image": image_base64,  # Processed image (if different from original)
        "detected_objects": detected_objects,
        "summary": detected_dictionary,
        "timestamp": datetime.datetime.now(pytz.timezone('Asia/Kolkata')).strftime("%Y-%B-%d %I:%m:%S %p"),
    }
    collection.update_one({"_id": ObjectId(document_id)}, {"$set": update_data})
    print(f"Updated document ID: {document_id} with inference data")

    # Clean up temporary file
    if os.path.exists(file_path):
        os.remove(file_path)

if __name__ == "__main__":
    socketio_client = socketio.Client()
    socketio_client.on('new_file', on_new_file)
    socketio_client.connect(SERVER_URL)
    print("Connected to the server. Waiting for new files...")
    socketio_client.wait()