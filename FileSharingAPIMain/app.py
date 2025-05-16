from flask import Flask, request, jsonify
from flask_socketio import SocketIO, emit
from pymongo import MongoClient
import os
import base64
import logging
from bson.objectid import ObjectId

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 32 * 1024 * 1024
socketio = SocketIO(app)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

MONGO_URI = "mongodb://localhost:27017/"
mongo_client = MongoClient(MONGO_URI)
db = mongo_client["GardenTest"]
collection = db["inferenceData"]

@app.route('/upload', methods=['POST'])
def upload_file():
    logger.info(f"Received POST request to /upload from {request.remote_addr}")
    if 'file' not in request.files:
        logger.error("No 'file' part in request")
        return {"error": "No file part"}, 400

    file = request.files['file']
    if file.filename == '':
        logger.error("No selected file")
        return {"error": "No selected file"}, 400

    try:
        location = request.form.get('location', 'Unknown')
        file_bytes = file.read()
        base64_image = base64.b64encode(file_bytes).decode('utf-8')

        inference_data = {
            "location": location,
            "imageData": base64_image,
            "upload_time": os.path.getctime(__file__),  # Replace with actual timestamp if needed
            "processed": False
        }
        result = collection.insert_one(inference_data)
        document_id = str(result.inserted_id)
        logger.info(f"Saved inference data to MongoDB with ID: {document_id}")

        socketio.emit('new_file', {'location': location, 'document_id': document_id})
        return {"message": "File uploaded successfully", "location": location, "document_id": document_id}, 200
    except Exception as e:
        logger.error(f"Error processing upload: {str(e)}")
        return {"error": f"Upload failed: {str(e)}"}, 500

@app.route('/get-inference-count', methods=['GET'])
def get_inference_count():
    try:
        count = collection.count_documents({})
        logger.info(f"Returning inference count: {count}")
        return jsonify({"count": count}), 200
    except Exception as e:
        logger.error(f"Error fetching count: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/get-inference-batch', methods=['GET'])
def get_inference_batch():
    try:
        start = int(request.args.get('start', 0))
        limit = int(request.args.get('limit', 10))
        items = list(collection.find({}, {"_id": 1, "location": 1, "upload_time": 1}).skip(start).limit(limit))
        for item in items:
            item["_id"] = str(item["_id"])  # Convert ObjectId to string
        logger.info(f"Returning batch: start={start}, limit={limit}, items={len(items)}")
        return jsonify({"items": items}), 200
    except Exception as e:
        logger.error(f"Error fetching batch: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/get-inference-item', methods=['GET'])
def get_inference_item():
    try:
        item_id = request.args.get('id')
        if not item_id:
            return jsonify({"error": "No id provided"}), 400
        item = collection.find_one({"_id": ObjectId(item_id)}, {"_id": 0})
        if not item:
            return jsonify({"error": "Item not found"}), 404
        logger.info(f"Returning item with id: {item_id}")
        return jsonify({"item": item}), 200
    except Exception as e:
        logger.error(f"Error fetching item: {str(e)}")
        return jsonify({"error": str(e)}), 500

@socketio.on('connect')
def handle_connect():
    logger.info("Client connected via Socket.IO")

@socketio.on('disconnect')
def handle_disconnect():
    logger.info("Client disconnected from Socket.IO")

if __name__ == '__main__':
    socketio.run(app, host="0.0.0.0", port=5000, debug=True)