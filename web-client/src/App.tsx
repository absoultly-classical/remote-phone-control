import React, { useEffect, useRef, useState } from 'react';
import { io, Socket } from 'socket.io-client';

const SIGNAL_SERVER = 'http://411501e9.r12.cpolar.top'; // 生产环境需替换为内网穿透后的公网URL
const ROOM_ID = 'phone_remote_control';

const App: React.FC = () => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [status, setStatus] = useState('Disconnected');
  const socketRef = useRef<Socket | null>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);

  useEffect(() => {
    // 1. 初始化 Socket 连接
    const socket = io(SIGNAL_SERVER, {
      auth: {
        token: 'your_secret_password' // 需与服务器 AUTH_TOKEN 一致
      }
    });
    socketRef.current = socket;

    socket.on('connect', () => {
      setStatus('Connected to Signaling Server');
      socket.emit('join', ROOM_ID);
    });

    socket.on('signal', async (data: any) => {
      const { type, payload } = data;
      console.log('Received signal:', type);

      if (type === 'offer') {
        await handleOffer(payload);
      } else if (type === 'candidate') {
        await pcRef.current?.addIceCandidate(new RTCIceCandidate(payload));
      }
    });

    return () => {
      socket.disconnect();
      pcRef.current?.close();
    };
  }, []);

  const initPeerConnection = () => {
    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
    });

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        socketRef.current?.emit('signal', {
          room: ROOM_ID,
          type: 'candidate',
          payload: event.candidate
        });
      }
    };

    pc.ontrack = (event) => {
      console.log('Received remote track');
      if (videoRef.current) {
        videoRef.current.srcObject = event.streams[0];
      }
    };

    pcRef.current = pc;
    return pc;
  };

  const handleOffer = async (offer: RTCSessionDescriptionInit) => {
    const pc = initPeerConnection();
    await pc.setRemoteDescription(new RTCSessionDescription(offer));
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);

    socketRef.current?.emit('signal', {
      room: ROOM_ID,
      type: 'answer',
      payload: answer
    });
    setStatus('Streaming...');
  };

  // 发送控制指令
  const sendControlEvent = (type: string, data: any) => {
    socketRef.current?.emit('signal', {
      room: ROOM_ID,
      type: 'control',
      payload: { action: type, ...data }
    });
  };

  const handleCanvasClick = (e: React.MouseEvent<HTMLVideoElement>) => {
    if (!videoRef.current) return;
    const rect = videoRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;

    console.log('Click at:', x, y);
    sendControlEvent('click', { x, y });
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#1a1a1a', color: '#fff', minHeight: '100vh' }}>
      <h1>Remote Phone Control</h1>
      <div style={{ marginBottom: '10px' }}>Status: <span style={{ color: status === 'Streaming...' ? '#4caf50' : '#ff9800' }}>{status}</span></div>

      <div style={{ position: 'relative', display: 'inline-block', border: '5px solid #333', borderRadius: '10px', overflow: 'hidden' }}>
        <video
          ref={videoRef}
          autoPlay
          playsInline
          onClick={handleCanvasClick}
          style={{ maxWidth: '100%', maxHeight: '80vh', display: 'block', cursor: 'pointer', backgroundColor: '#000' }}
        />
        {status === 'Connected to Signaling Server' && (
          <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }}>
            Waiting for Phone to start stream...
          </div>
        )}
      </div>

      <div style={{ marginTop: '20px' }}>
        <h3>Controls</h3>
        <button onClick={() => sendControlEvent('home', {})}>Home</button>
        <button onClick={() => sendControlEvent('back', {})} style={{ marginLeft: '10px' }}>Back</button>
        <button onClick={() => sendControlEvent('recents', {})} style={{ marginLeft: '10px' }}>Recents</button>
      </div>
    </div>
  );
};

export default App;
