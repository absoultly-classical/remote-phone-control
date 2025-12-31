import React, { useEffect, useRef, useState } from 'react';
import { io, Socket } from 'socket.io-client';

const App: React.FC = () => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [status, setStatus] = useState('Disconnected');
  const [inputCode, setInputCode] = useState('');
  const [serverUrl, setServerUrl] = useState(() => localStorage.getItem('remote_control_server') || 'http://localhost:3000');
  const [authToken, setAuthToken] = useState(() => localStorage.getItem('remote_control_token') || 'your_secret_password');
  const [isServerConnected, setIsServerConnected] = useState(false);
  const [roomId, setRoomId] = useState<string | null>(null);
  const socketRef = useRef<Socket | null>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);

  // Step 1: Initialize connection to the signaling server
  const initializeServerConnection = () => {
    if (!serverUrl || !authToken) {
      alert("Please enter both server URL and Security Token");
      return;
    }

    if (socketRef.current) {
      socketRef.current.disconnect();
    }

    setStatus('Connecting to server...');
    localStorage.setItem('remote_control_server', serverUrl);
    localStorage.setItem('remote_control_token', authToken);

    const socket = io(serverUrl, {
      auth: { token: authToken },
      reconnectionAttempts: 3
    });

    socketRef.current = socket;

    socket.on('connect', () => {
      setIsServerConnected(true);
      setStatus('Server Connected');
    });

    socket.on('connect_error', (err) => {
      setIsServerConnected(false);
      setStatus(`Server Error: ${err.message}`);
    });

    socket.on('disconnect', () => {
      setIsServerConnected(false);
      if (!roomId) setStatus('Disconnected from server');
    });

    socket.on('signal', async (data: any) => {
      const { type, payload } = data;
      if (type === 'offer') await handleOffer(payload);
      else if (type === 'candidate') await pcRef.current?.addIceCandidate(new RTCIceCandidate(payload));
    });
  };

  // Step 2: Join a specific device room using the 6-digit code
  const pairWithDevice = () => {
    if (!isServerConnected || !socketRef.current) {
      alert("Please connect to the server first");
      return;
    }

    if (inputCode.length !== 6) {
      alert("Please enter a valid 6-digit code");
      return;
    }

    setStatus('Verifying device code...');
    socketRef.current.emit('join_code', inputCode, (response: any) => {
      if (response.success) {
        setStatus('Paired with device');
        setRoomId(inputCode);
        // Trigger the phone to start the WebRTC handshake
        socketRef.current?.emit('signal', {
          room: inputCode,
          type: 'request_offer'
        });
      } else {
        setStatus(`Pairing Failed: ${response.message}`);
      }
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
    sendControlEvent('click', { x, y });
  };

  if (!roomId) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', backgroundColor: '#1a1a1a', color: '#fff', fontFamily: 'sans-serif' }}>
        <h1 style={{ marginBottom: '30px' }}>Remote Phone Control</h1>

        <div style={{ width: '450px', padding: '25px', borderRadius: '12px', backgroundColor: '#222', boxShadow: '0 4px 20px rgba(0,0,0,0.5)' }}>

          {/* Section 1: Server Configuration */}
          <div style={{ marginBottom: '25px', opacity: isServerConnected ? 0.6 : 1 }}>
            <h3 style={{ marginTop: 0, color: '#4caf50' }}>1. Server Setup</h3>
            <p style={{ fontSize: '14px', color: '#aaa' }}>Enter your signaling server details:</p>
            <input
              value={serverUrl}
              onChange={(e) => setServerUrl(e.target.value)}
              disabled={isServerConnected}
              style={{ padding: '10px', fontSize: '15px', width: '100%', boxSizing: 'border-box', marginBottom: '10px', backgroundColor: '#333', color: '#fff', border: '1px solid #444' }}
              placeholder="http://xxxx.cpolar.top"
            />
            <input
              value={authToken}
              onChange={(e) => setAuthToken(e.target.value)}
              disabled={isServerConnected}
              style={{ padding: '10px', fontSize: '15px', width: '100%', boxSizing: 'border-box', marginBottom: '15px', backgroundColor: '#333', color: '#fff', border: '1px solid #444' }}
              placeholder="Device ID (e.g. RC-A1B2)"
            />
            {!isServerConnected ? (
              <button onClick={initializeServerConnection} style={{ width: '100%', padding: '12px', fontSize: '16px', cursor: 'pointer', backgroundColor: '#2196f3', color: 'white', border: 'none', borderRadius: '4px', fontWeight: 'bold' }}>
                Connect to Server
              </button>
            ) : (
              <button onClick={() => setIsServerConnected(false)} style={{ width: '100%', padding: '10px', fontSize: '14px', cursor: 'pointer', backgroundColor: '#444', color: '#bbb', border: 'none', borderRadius: '4px' }}>
                Modify Server Settings
              </button>
            )}
          </div>

          {/* Section 2: Device Pairing */}
          <div style={{ borderTop: '1px solid #444', paddingTop: '20px', opacity: isServerConnected ? 1 : 0.3, pointerEvents: isServerConnected ? 'auto' : 'none' }}>
            <h3 style={{ marginTop: 0, color: '#4caf50' }}>2. Device Pairing</h3>
            <p style={{ fontSize: '14px', color: '#aaa' }}>Enter the 6-digit code from your phone:</p>
            <div style={{ display: 'flex', gap: '10px' }}>
              <input
                value={inputCode}
                onChange={(e) => setInputCode(e.target.value)}
                style={{ padding: '12px', fontSize: '20px', flex: 1, textAlign: 'center', backgroundColor: '#333', color: '#fff', border: '1px solid #444', letterSpacing: '2px' }}
                maxLength={6}
                placeholder="000000"
              />
              <button onClick={pairWithDevice} style={{ padding: '0 25px', fontSize: '16px', cursor: 'pointer', backgroundColor: '#4caf50', color: 'white', border: 'none', borderRadius: '4px', fontWeight: 'bold' }}>
                Pair
              </button>
            </div>
          </div>

          {/* Status Display */}
          <div style={{ marginTop: '20px', padding: '10px', borderRadius: '4px', backgroundColor: '#111', borderLeft: `4px solid ${status.includes('Error') || status.includes('Failed') ? '#f44336' : (isServerConnected ? '#4caf50' : '#ff9800')}` }}>
            <span style={{ fontSize: '13px', color: '#888' }}>Status:</span>
            <div style={{ fontSize: '15px', fontWeight: 'bold', color: status.includes('Error') || status.includes('Failed') ? '#f44336' : '#eee' }}>{status}</div>
          </div>

        </div>
      </div>
    );
  }

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#1a1a1a', color: '#fff', minHeight: '100vh' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h1>Connected: {roomId}</h1>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '14px', color: '#888' }}>Status: <span style={{ color: status === 'Streaming...' ? '#4caf50' : '#ff9800' }}>{status}</span></div>
          <button onClick={() => { socketRef.current?.disconnect(); setRoomId(null); setStatus('Disconnected'); setIsServerConnected(false); }} style={{ marginTop: '5px', padding: '5px 15px', backgroundColor: '#f44336', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>Exit Session</button>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '30px', alignItems: 'flex-start' }}>
        {/* Video Stream */}
        <div style={{ flex: 1, position: 'relative', border: '5px solid #333', borderRadius: '15px', overflow: 'hidden', boxShadow: '0 10px 30px rgba(0,0,0,0.8)' }}>
          <video
            ref={videoRef}
            autoPlay
            playsInline
            onClick={handleCanvasClick}
            style={{ width: '100%', display: 'block', cursor: 'pointer', backgroundColor: '#000' }}
          />
          {status === 'Paired with device' && (
            <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', color: '#aaa' }}>
              Waiting for device to start stream...
            </div>
          )}
        </div>

        {/* Remote Controls Pad */}
        <div style={{ width: '200px', padding: '20px', backgroundColor: '#222', borderRadius: '12px', textAlign: 'center' }}>
          <h3 style={{ margin: '0 0 20px 0' }}>Remote Keys</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '10px' }}>
            <button onClick={() => sendControlEvent('home', {})} style={{ padding: '15px', fontSize: '16px', backgroundColor: '#333', color: 'white', border: '1px solid #444', borderRadius: '8px', cursor: 'pointer' }}>Home</button>
            <button onClick={() => sendControlEvent('back', {})} style={{ padding: '15px', fontSize: '16px', backgroundColor: '#333', color: 'white', border: '1px solid #444', borderRadius: '8px', cursor: 'pointer' }}>Back</button>
            <button onClick={() => sendControlEvent('recents', {})} style={{ padding: '15px', fontSize: '16px', backgroundColor: '#333', color: 'white', border: '1px solid #444', borderRadius: '8px', cursor: 'pointer' }}>Recents</button>
          </div>
          <p style={{ marginTop: '20px', fontSize: '12px', color: '#666' }}>Click video to simulate touch events</p>
        </div>
      </div>
    </div>
  );
};

export default App;
