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
  const [forceRelay, setForceRelay] = useState(false);
  const socketRef = useRef<Socket | null>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);
  const roomIdRef = useRef<string | null>(null); // 用于在异步回调中获取最新的 roomId
  const remoteStreamRef = useRef<MediaStream | null>(null); // 保存远程流引用

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

    const pendingCandidates: RTCIceCandidateInit[] = [];

    socket.on('signal', async (data: any) => {
      const { type, payload } = data;
      if (type === 'offer') {
        await handleOffer(payload);
        while (pendingCandidates.length > 0) {
          const cand = pendingCandidates.shift();
          if (cand) await pcRef.current?.addIceCandidate(new RTCIceCandidate(cand)).catch(e => console.warn('Buffered candidate error:', e));
        }
      } else if (type === 'candidate') {
        // 过滤掉可能导致浏览器报错或挂起的无效 .local 地址（如果环境不支持 mDNS）
        if (payload.candidate && payload.candidate.includes('.local') && !window.location.hostname.includes('localhost')) {
          console.log('Skipping mDNS candidate for non-local environment');
          return;
        }
        
        console.log('Received remote ICE candidate:', payload.candidate);
        if (pcRef.current && pcRef.current.remoteDescription) {
          await pcRef.current.addIceCandidate(new RTCIceCandidate(payload)).catch(e => console.warn('Add candidate error:', e));
        } else {
          pendingCandidates.push(payload);
        }
      }
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
        roomIdRef.current = inputCode; // 同步更新 ref
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

  // 当进入流媒体视图时，确保视频元素连接到流
  useEffect(() => {
    if (roomId && videoRef.current && remoteStreamRef.current) {
      console.log('Re-attaching stream after view change');
      videoRef.current.srcObject = remoteStreamRef.current;
      videoRef.current.play().catch(err => console.error('Re-attach play failed:', err));
    }
  }, [roomId]);

  const initPeerConnection = () => {
    console.log('Initializing PeerConnection...');
    const pc = new RTCPeerConnection({
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:stun.anyfirewall.com:3478' },
        {
          urls: [
            'turn:a.relay.metered.ca:443?transport=tcp',
            'turn:a.relay.metered.ca:443?transport=udp',
            'turn:a.relay.metered.ca:80?transport=tcp',
            'turn:a.relay.metered.ca:80?transport=udp'
          ],
          username: 'e8dd65c92f6067e7e3c2c6e0',
          credential: 'uWdWNmkhvyqTmFPm'
        }
      ],
      iceCandidatePoolSize: 10,
      iceTransportPolicy: forceRelay ? 'relay' : 'all',
      bundlePolicy: 'max-bundle',
      rtcpMuxPolicy: 'require'
    });

    pc.onicecandidate = (event) => {
      if (event.candidate && roomIdRef.current) {
        // 打印 candidate 类型以便调试
        const candidateType = event.candidate.candidate.split(' ')[7] || 'unknown';
        console.log(`Sending ICE candidate: type=${candidateType}, protocol=${event.candidate.protocol}, address=${event.candidate.address}`);
        socketRef.current?.emit('signal', {
          room: roomIdRef.current,
          type: 'candidate',
          payload: event.candidate
        });
      } else if (!event.candidate) {
        console.log('ICE gathering completed');
      }
    };

    // 添加 ICE 收集状态监听
    pc.onicegatheringstatechange = () => {
      console.log('ICE gathering state:', pc.iceGatheringState);
    };

    pc.ontrack = (event) => {
      console.log('ontrack event received!', event.streams);
      console.log('Track kind:', event.track.kind, 'Track readyState:', event.track.readyState);
      if (event.streams[0]) {
        remoteStreamRef.current = event.streams[0];
        console.log('Stored remote stream, tracks:', event.streams[0].getTracks().map(t => `${t.kind}:${t.readyState}`));
        if (videoRef.current) {
          console.log('Attaching stream to video element');
          videoRef.current.srcObject = event.streams[0];
          // 确保视频播放
          videoRef.current.play().then(() => {
            console.log('Video playback started successfully');
          }).catch((err) => {
            console.error('Video playback failed:', err);
            // 如果自动播放失败，可能需要用户交互
          });
        }
      }
    };

    pc.oniceconnectionstatechange = () => {
      console.log('ICE connection state:', pc.iceConnectionState);
    };

    pc.onconnectionstatechange = () => {
      console.log('Connection state:', pc.connectionState);
      if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected') {
        console.warn(`WebRTC connection ${pc.connectionState}, attempting to restart...`);
        setStatus(`Connection ${pc.connectionState}, retrying...`);
        
        setTimeout(() => {
          if (roomIdRef.current && socketRef.current?.connected) {
            // 重置 PC 状态
            pc.close();
            pcRef.current = null;
            socketRef.current?.emit('signal', {
              room: roomIdRef.current,
              type: 'request_offer'
            });
          }
        }, 3000);
      }
    };

    pcRef.current = pc;
    return pc;
  };

  const preferCodec = (sdp: string, codec: string) => {
    const lines = sdp.split('\r\n');
    let videoMLineIndex = -1;
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].indexOf('m=video') === 0) {
        videoMLineIndex = i;
        break;
      }
    }
    if (videoMLineIndex === -1) return sdp;

    const payloadRegex = new RegExp(`a=rtpmap:(\\d+) ${codec}/90000`);
    let payload: string | null = null;
    for (let i = 0; i < lines.length; i++) {
      const match = lines[i].match(payloadRegex);
      if (match) {
        payload = match[1];
        break;
      }
    }
    if (!payload) return sdp;

    const elements = lines[videoMLineIndex].split(' ');
    const mLinePayloads = elements.slice(3);
    const index = mLinePayloads.indexOf(payload);
    if (index !== -1) {
      mLinePayloads.splice(index, 1);
      mLinePayloads.unshift(payload);
    }
    elements.splice(3, elements.length - 3, ...mLinePayloads);
    lines[videoMLineIndex] = elements.join(' ');
    return lines.join('\r\n');
  };

  const handleOffer = async (offer: RTCSessionDescriptionInit) => {
    console.log('Received offer, creating answer...');
    const pc = initPeerConnection();
    
    // 尝试在 Remote SDP 中优先选择 H264
    if (offer.sdp) {
      offer.sdp = preferCodec(offer.sdp, 'H264');
    }
    
    await pc.setRemoteDescription(new RTCSessionDescription(offer));
    const answer = await pc.createAnswer();
    
    // 同样在 Local SDP 中优先选择 H264
    if (answer.sdp) {
      answer.sdp = preferCodec(answer.sdp, 'H264');
    }
    
    await pc.setLocalDescription(answer);

    const currentRoom = roomIdRef.current;
    console.log('Sending answer to room:', currentRoom);
    if (currentRoom) {
      socketRef.current?.emit('signal', {
        room: currentRoom,
        type: 'answer',
        payload: answer
      });
    } else {
      console.error('Cannot send answer: roomIdRef.current is null');
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

  const touchStartRef = useRef<{ x: number, y: number, time: number } | null>(null);

  const handleMouseDown = (e: React.MouseEvent<HTMLVideoElement>) => {
    if (!videoRef.current) return;
    const rect = videoRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    touchStartRef.current = { x, y, time: Date.now() };
  };

  const handleMouseUp = (e: React.MouseEvent<HTMLVideoElement>) => {
    if (!videoRef.current || !touchStartRef.current) return;
    const rect = videoRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    
    const dx = x - touchStartRef.current.x;
    const dy = y - touchStartRef.current.y;
    const duration = Date.now() - touchStartRef.current.time;
    
    // If movement is very small, treat as click
    if (Math.sqrt(dx * dx + dy * dy) < 0.01) {
      sendControlEvent('click', { x, y });
    } else {
      sendControlEvent('swipe', {
        x1: touchStartRef.current.x,
        y1: touchStartRef.current.y,
        x2: x,
        y2: y,
        duration: Math.max(duration, 100) // Minimum 100ms for swipe
      });
    }
    touchStartRef.current = null;
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
            <div style={{ marginBottom: '15px', display: 'flex', alignItems: 'center', gap: '10px' }}>
              <input 
                type="checkbox" 
                id="forceRelay" 
                checked={forceRelay} 
                onChange={(e) => setForceRelay(e.target.checked)}
                style={{ cursor: 'pointer' }}
              />
              <label htmlFor="forceRelay" style={{ fontSize: '14px', color: '#aaa', cursor: 'pointer' }}>Force Relay Mode (Use TURN)</label>
            </div>
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
            muted
            onMouseDown={handleMouseDown}
            onMouseUp={handleMouseUp}
            onLoadedMetadata={() => {
              console.log('Video metadata loaded, attempting to play...');
              videoRef.current?.play().catch(err => console.error('Play on metadata failed:', err));
            }}
            style={{ width: '100%', height: 'auto', display: 'block', cursor: 'pointer', backgroundColor: '#000', objectFit: 'contain' }}
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
          <p style={{ marginTop: '20px', fontSize: '12px', color: '#666' }}>Click to tap, drag to swipe</p>
        </div>
      </div>
    </div>
  );
};

export default App;
