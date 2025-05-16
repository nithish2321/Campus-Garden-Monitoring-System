import { useEffect, useState } from "react"; 
import Zoom from "react-medium-image-zoom";
import "react-medium-image-zoom/dist/styles.css";
import { motion } from "framer-motion";

const API_URL = "http://localhost:5000/api/data";

function App() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedItem, setSelectedItem] = useState(null);

  useEffect(() => {
    let isMounted = true;
    fetch(API_URL)
      .then((response) => response.json())
      .then((json) => {
        if (isMounted) setData(json);
      })
      .catch((error) => console.error("❌ Error fetching data:", error))
      .finally(() => {
        if (isMounted) setLoading(false);
      });
    return () => { isMounted = false; };
  }, []);

  return (
    <div style={{ padding: "20px", textAlign: "center", background: "linear-gradient(50Deg,rgba(101, 103, 223, 0.74),rgb(255, 255, 255))", color: "#fff" }}>
      <h1 style={{ fontSize: "26px", fontWeight: "bold", marginBottom: "20px", color: "rgb(80, 83, 255)" }}>
          🤖🖥️📈 Inference Monitoring Dashboard 
      </h1>
      {loading ? (
        <div style={{ fontSize: "18px", fontWeight: "bold", color: "#1e90ff" }}>Loading...</div>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(3, 1fr)",
            gap: "20px",
            justifyContent: "center",
            alignItems: "center",
            padding: "20px",
          }}
        >
          {data.map((item, index) => (
            <motion.div 
              key={index} 
              whileHover={{ scale: 1.05 }} 
              whileTap={{ scale: 0.95 }}
              style={{
                background: "#1e1e1e",
                border: "2px solid rgb(169, 71, 255)",
                borderRadius: "10px",
                padding: "15px",
                boxShadow: "4px 4px 15px rgba(255, 71, 87, 0.4)",
                textAlign: "center",
                cursor: "pointer",
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between",
                color: "#fff"
              }}
              onClick={() => setSelectedItem(item)}
            >
              <Zoom>
                <img 
                  src={item.image ? `data:image/png;base64,${item.image}` : "https://via.placeholder.com/200"} 
                  alt="Detected Object" 
                  style={{ width: "100%", height: "200px", objectFit: "contain", borderRadius: "10px" }}
                />
              </Zoom>
              <div
                style={{
                  background: "#2c2c2c",
                  padding: "10px",
                  borderRadius: "5px",
                  textAlign: "left",
                  marginTop: "10px",
                  fontSize: "14px",
                  fontWeight: "500",
                  color: "#00ff7f"
                }}
              >
                <strong style={{ color: "#1e90ff" }}>📅 Upload Time:</strong> {new Date(item.upload_time * 1000).toLocaleString()} <br />
                <strong style={{ color: "#1e90ff" }}>📍 Location:</strong> {item.location || "Unknown"} <br />
                <strong style={{ color: "#1e90ff" }}>📝 Objects Detected:</strong> 
                {item.detected_objects
                  .filter(obj => obj[0] && obj[1]) // Filter valid pairs
                  .map(obj => ` (${obj[0]}, ${obj[1]}%)`)
                  .join(', ') || "None"}
                <br />
                <strong style={{ color: "#ff4757" }}>📊 Summary:</strong> <br />
                {item.summary.Garbage ? `Garbage: ${item.summary.Garbage}` : ""} <br />
                {item.summary.Tilted_Bin ? `Tilted Bin: ${item.summary.Tilted_Bin}` : ""} <br />
                {item.summary.Upright_Bin ? `Upright Bin: ${item.summary.Upright_Bin}` : ""} <br />
              </div>
            </motion.div>
          ))}
        </div>
      )}

      {selectedItem && (
        <div 
          style={{
            position: "fixed", 
            top: 0, left: 0, 
            width: "100%", height: "100%", 
            background: "rgba(0,0,0,0.9)", 
            display: "flex", justifyContent: "center", alignItems: "center"
            
          }} 
          onClick={() => setSelectedItem(null)}
        >
          <div 
            style={{
              background: "#1e1e1e", 
              padding: "20px", borderRadius: "10px", 
              maxWidth: "90%", maxHeight: "90%", 
              overflow: "auto", textAlign: "center", color: "#fff"
            }} 
            onClick={(e) => e.stopPropagation()}
          >
            <h2 style={{ color: "#ff4757" }}>🔎 Inference Details</h2>
            <Zoom>
              <img 
                src={selectedItem.image ? `data:image/png;base64,${selectedItem.image}` : "https://via.placeholder.com/200"} 
                alt="Detected Object" 
                style={{ width: "100%", maxHeight: "80vh", objectFit: "contain", borderRadius: "10px" }} 
              />
            </Zoom>
            <div 
              style={{ background: "#2c2c2c", padding: "10px", borderRadius: "5px", textAlign: "left", marginTop: "10px", color: "#00ff7f" }}
            >
              <strong style={{ color: "#1e90ff" }}>📅 Upload Time:</strong> {new Date(selectedItem.upload_time * 1000).toLocaleString()} <br />
              <strong style={{ color: "#1e90ff" }}>📍 Location:</strong> {selectedItem.location || "Unknown"} <br />
              <strong style={{ color: "#1e90ff" }}>📝 Objects Detected:</strong> 
              {selectedItem.detected_objects
                .filter(obj => obj[0] && obj[1])
                .map(obj => ` (${obj[0]}, ${obj[1]}%)`)
                .join(', ') || "None"} <br />
              <strong style={{ color: "#ff4757" }}>📊 Summary:</strong> <br />
              {selectedItem.summary.Garbage ? `Garbage: ${selectedItem.summary.Garbage}` : ""} <br />
              {selectedItem.summary.Tilted_Bin ? `Tilted Bin: ${selectedItem.summary.Tilted_Bin}` : ""} <br />
              {selectedItem.summary.Upright_Bin ? `Upright Bin: ${selectedItem.summary.Upright_Bin}` : ""} <br />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;