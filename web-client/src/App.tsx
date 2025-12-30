import React, { useEffect, useRef, useState } from 'react';
import { io, Socket } from 'socket.io-client';

const SIGNAL_SERVER = 'http://411501e9.r12.cpolar.top'; // 生产环境需替换为内网穿透后的公网URL

const App: React.FC = () => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [status, setStatus] = useState('Disconnected');
  const [inputCode, setInputCode] = useState('');
  const [roomId, setRoomId] = useState<string | null>(null);
  const socketRef = useRef<Socket | null>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);

  const connectToCode = () => {
    if (inputCode.length !== 6) {
      alert("Please enter a valid 6-digit code");
      return;
    }

    // 1. Initialize Socket
    const socket = io(SIGNAL_SERVER, {
      auth: {
        token: 'your_secret_password'
      }
    });
    socketRef.current = socket;

    socket.on('connect', () => {
      setStatus('Verifying Code...');
      // 2. Join the specific room
      socket.emit('join_code', inputCode, (response: any) => {
        if (response.success) {
          setStatus('Connected to Signaling Server');
          setRoomId(inputCode);
          console.log('Joined room:', inputCode);
        } else {
          setStatus(`Error: ${response.message}`);
          socket.disconnect();
        }
      });
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

    socket.on('disconnect', () => {
      setStatus('Disconnected');
      setRoomId(null);
    });
  };

  useEffect(() => {
    return () => {
      socketRef.current?.disconnect();
      pcRef.current?.close();
    };
  }, []);

  const initPeerConnection = () => {
    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
    });

    pc.onicecandidate = (event) => {
      if (event.candidate && roomId) {
        socketRef.current?.emit('signal', {
          room: roomId,
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

    if (roomId) {
      socketRef.current?.emit('signal', {
        room: roomId,
        type: 'answer',
        payload: answer
      });
    }
    setStatus('Streaming...');
  };

  // Send control commands
  const sendControlEvent = (type: string, data: any) => {
    if (roomId) {
      socketRef.current?.emit('signal', {
        room: roomId,
        type: 'control',
        payload: { action: type, ...data }
      });
    }
  };

  const handleCanvasClick = (e: React.MouseEvent<HTMLVideoElement>) => {
    if (!videoRef.current) return;
    const rect = videoRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;

    console.log('Click at:', x, y);
    sendControlEvent('click', { x, y });
  };

  if (!roomId) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', backgroundColor: '#1a1a1a', color: '#fff', fontFamily: 'sans-serif' }}>
        <h1>Remote Phone Control</h1>
        <div style={{ padding: '20px', border: '1px solid #333', borderRadius: '8px', backgroundColor: '#222' }}>
          <p>Enter the 6-digit code displayed on the phone:</p>
          <input
            value={inputCode}
            onChange={(e) => setInputCode(e.target.value)}
            style={{ padding: '10px', fontSize: '18px', width: '200px', textAlign: 'center', marginRight: '10px' }}
            maxLength={6}
            placeholder="123456"
          />
          <button onClick={connectToCode} style={{ padding: '10px 20px', fontSize: '18px', cursor: 'pointer', backgroundColor: '#4caf50', color: 'white', border: 'none', borderRadius: '4px' }}>
            Connect
          </button>
          <p style={{ color: '#ff9800', marginTop: '10px' }}>{status}</p>
        </div>
      </div>
    );
  }

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#1a1a1a', color: '#fff', minHeight: '100vh' }}>
      <h1>Remote Phone Control: {roomId}</h1>
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
        <button onClick={() => { socketRef.current?.disconnect(); setRoomId(null); setStatus('Disconnected'); }} style={{ marginLeft: '10px', backgroundColor: '#f44336', color: 'white' }}>Disconnect</button>
      </div>
    </div>
  );
};

export default App;
