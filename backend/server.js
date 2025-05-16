const express = require("express");
const mongoose = require("mongoose");
const cors = require("cors");

const app = express();
app.use(cors());
app.use(express.json());

const DB_NAME = "GardenTest";
const COLLECTION_NAME = "inferenceData";
const MONGO_URI = `mongodb://localhost:27017/${DB_NAME}`;

mongoose.connect(MONGO_URI, {
    useNewUrlParser: true,
    useUnifiedTopology: true,
})
.then(() => console.log(`✅ Connected to MongoDB: ${DB_NAME}`))
.catch(err => console.error("❌ Database connection error:", err));

// Define Schema (Matching CSV Data Structure)
const DataSchema = new mongoose.Schema({
    _id: mongoose.Schema.Types.ObjectId,
    location: String,
    imageData: String, // Placeholder or actual image data
    upload_time: Number, // Unix timestamp
    processed: Boolean,
    detected_objects: [[mongoose.Mixed]], // Array of [object, confidence] pairs
    image: String, // Placeholder or Base64 image
    summary: {
        Garbage: Number,
        Tilted_Bin: Number,
        Upright_Bin: Number
    }
}, { collection: COLLECTION_NAME });

const DataModel = mongoose.model("Data", DataSchema);

// API to Get All Data
app.get("/api/data", async (req, res) => {
    try {
        const data = await DataModel.find().sort({ upload_time: -1 }).limit(20); // Fetch last 20 inferences
        res.json(data);
    } catch (error) {
        res.status(500).json({ error: "Database error" });
    }
});

// Start Server
const PORT = 5000;
app.listen(PORT, () => console.log(`🚀 Server running on port ${PORT}`));