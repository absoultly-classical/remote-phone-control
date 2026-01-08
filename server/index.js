const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const fs = require('fs');
const path = require('path');

const app = express();
app.use(cors());

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

const roomTokens = new Map(); // 用于存储每个房间对应的验证令牌

io.on('connection', (socket) => {
  console.log('User connected:', socket.id);

  // Generate a random 6-digit code
  socket.on('create_code', (callback) => {
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    const token = socket.handshake.auth.token; // 获取创建者提供的 Token

    socket.join(code);
    roomTokens.set(code, token); // 建立房间与 Token 的绑定关系

    console.log(`Socket ${socket.id} created room: ${code} with token: ${token}`);
    callback({ code });
  });

  // Verify and join an existing room
  socket.on('join_code', (code, callback) => {
    const room = io.sockets.adapter.rooms.get(code);
    const clientToken = socket.handshake.auth.token; // 获取加入者提供的 Token
    const storedToken = roomTokens.get(code); // 获取房间预设的 Token

    if (room && room.size > 0) {
      if (clientToken === storedToken) {
        socket.join(code);
        console.log(`Socket ${socket.id} joined room: ${code}`);
        callback({ success: true });
      } else {
        console.warn(`Auth failed for room ${code}: tokens do not match`);
        callback({ success: false, message: "Security Token Mismatch" });
      }
    } else {
      callback({ success: false, message: "Invalid Code or Room not found" });
    }
  });

  // Legacy support or direct join (kept for compatibility or testing if needed, but modified)
  socket.on('join', (room) => {
    socket.join(room);
    console.log(`Socket ${socket.id} joined room: ${room}`);
  });

  // Forward signals
  socket.on('signal', (data) => {
    const { room, type, payload } = data;
    console.log(`Relaying ${type} to room ${room}`);
    socket.to(room).emit('signal', {
      sender: socket.id,
      type,
      payload
    });
  });

  socket.on('disconnect', () => {
    console.log('User disconnected:', socket.id);
  });

  // Client Logging
  socket.on('client_log', (data) => {
    const { message, source } = data;
    const logMsg = `[${new Date().toISOString()}] [${source || 'UNKNOWN'}] ${message}\n`;
    console.log(`[CLIENT LOG] ${logMsg.trim()}`);

    // Append to file
    const logFile = path.join(__dirname, 'server_logs.txt');
    fs.appendFile(logFile, logMsg, (err) => {
      if (err) console.error('Failed to write log:', err);
    });
  });
});

const PORT = 3000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Signaling server is running on port ${PORT}`);
  console.log(`Local access: http://localhost:${PORT}`);
});
