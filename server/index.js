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

  // 接收握手房间信息
  socket.on('join', (room) => {
    socket.join(room);
    console.log(`Socket ${socket.id} joined room: ${room}`);
  });

  // 转发所有信令数据
  socket.on('signal', (data) => {
    // data 预期结构: { room: 'xxx', type: 'offer/answer/candidate', payload: ... }
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
