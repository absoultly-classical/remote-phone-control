const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
app.use(cors());

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

const AUTH_TOKEN = 'your_secret_password'; // 建议通过环境变量设置

io.use((socket, next) => {
  const token = socket.handshake.auth.token;
  if (token === AUTH_TOKEN) {
    next();
  } else {
    next(new Error("Authentication error"));
  }
});

io.on('connection', (socket) => {
  console.log('User connected:', socket.id);

  // Generate a random 6-digit code
  socket.on('create_code', (callback) => {
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    socket.join(code);
    console.log(`Socket ${socket.id} created room: ${code}`);
    callback({ code });
  });

  // Verify and join an existing room
  socket.on('join_code', (code, callback) => {
    const room = io.sockets.adapter.rooms.get(code);
    if (room && room.size > 0) {
      socket.join(code);
      console.log(`Socket ${socket.id} joined room: ${code}`);
      callback({ success: true });
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
});

const PORT = 3000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Signaling server is running on port ${PORT}`);
  console.log(`Local access: http://localhost:${PORT}`);
});
