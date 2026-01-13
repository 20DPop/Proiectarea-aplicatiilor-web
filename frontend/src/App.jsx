import "./App.css";
import React, { useState, useEffect, useRef } from "react";
import { Routes, Route, Navigate, useNavigate, useParams } from "react-router-dom";

import WelcomePage from "./components/WelcomePage";
import Login from "./components/Login";
import Register from "./components/Register";
import Header from "./components/Header";
import GlobalChat from "./components/chat/GlobalChat";
import PrivateChat from './components/chat/PrivateChat';
import RoomChat from "./components/chat/RoomChat";
import PokerLobby from "./components/poker/PokerLobby";
import PokerTable from "./components/poker/PokerTable";
import HangmanLobby from "./components/hangman/HangmanLobby";
import HangmanGame from "./components/hangman/HangmanGame";

const PrivateChatWrapper = ({ messages, users, username, sendMessage, connectionStatus, onChatPartnerChange }) => {
    const { chatPartner } = useParams();

    useEffect(() => {
        if (chatPartner) {
            localStorage.setItem('lastPrivateChatPartner', chatPartner);
            if (onChatPartnerChange) {
                onChatPartnerChange(chatPartner);
            }
        }
    }, [chatPartner, onChatPartnerChange]);

    const filteredMessages = messages.filter(msg =>
        msg.type === 'private_message' &&
        ((msg.sender === username && msg.to === chatPartner) || (msg.sender === chatPartner && msg.to === username))
    );

    return (
        <PrivateChat
            messages={filteredMessages}
            users={users}
            username={username}
            sendMessage={sendMessage}
            connectionStatus={connectionStatus}
        />
    );
};

const RoomChatWrapper = ({
                             messages, username, connectionStatus, sendMessage,
                             availableRooms, joinedRooms, usersInRooms,
                             onJoinRoom, onCreateRoom, onLeaveRoom, onRoomChange
                         }) => {
    const { roomName } = useParams();

    useEffect(() => {
        if (roomName) {
            localStorage.setItem('lastRoomChat', roomName);
            if (onRoomChange) {
                onRoomChange(roomName);
            }
        }
    }, [roomName, onRoomChange]);

    return (
        <RoomChat
            messages={messages}
            username={username}
            connectionStatus={connectionStatus}
            sendMessage={sendMessage}
            availableRooms={availableRooms}
            joinedRooms={joinedRooms}
            usersInRooms={usersInRooms}
            onJoinRoom={onJoinRoom}
            onCreateRoom={onCreateRoom}
            onLeaveRoom={onLeaveRoom}
        />
    );
};

// --- Main App Component ---

function App() {
    // 1. State-ul de Autentificare
    const [isLoggedIn, setIsLoggedIn] = useState(()=>{
        return localStorage.getItem("isLoggedIn") === "true";
    });

    const [username, setUsername] = useState(()=>{
        return localStorage.getItem("username") || "";
        }
    );

    // State-uri pentru Chat
    const [users, setUsers] = useState([]);
    const [messages, setMessages] = useState([]);
    const [connectionStatus, setConnectionStatus] = useState("disconnected");
    const [availableRooms, setAvailableRooms] = useState([]);
    const [joinedRooms, setJoinedRooms] = useState([]);
    const [usersInRooms, setUsersInRooms] = useState(new Map());

    // State-uri pentru Poker
    const [pokerGames, setPokerGames] = useState([]);
    const [currentPokerGame, setCurrentPokerGame] = useState(null);
    const [myPokerHand, setMyPokerHand] = useState([]);

    // State-uri pentru Hangman
    const [hangmanGames, setHangmanGames] = useState([]);
    const [currentHangmanGame, setCurrentHangmanGame] = useState(null);

    // Persistenta navigare
    const [lastPrivateChatPartner, setLastPrivateChatPartner] = useState(() => localStorage.getItem('lastPrivateChatPartner'));
    const [lastRoomChat, setLastRoomChat] = useState(() => localStorage.getItem('lastRoomChat'));

    const websocketRef = useRef(null);
    const navigate = useNavigate();

    // 2. Conectare WebSocket cand userul se logheaza
    useEffect(() => {
        if (isLoggedIn && username) {
            connectWebSocket();
            fetchHangmanGames();
            fetchPokerGames(); // Fetch initial pentru Poker
        } else {
            disconnectWebSocket();
        }
        return () => {
            disconnectWebSocket();
        };
    }, [isLoggedIn, username]);

    const handleLoginSuccess = (loggedInUsername) => {
        setUsername(loggedInUsername);
        setIsLoggedIn(true);
    };

    const handleLogout = () => {
        localStorage.removeItem("username");
        localStorage.removeItem("isLoggedIn");
        setIsLoggedIn(false);
        setUsername("");
        disconnectWebSocket();
        navigate("/");
    };

    const connectWebSocket = () => {
        if (websocketRef.current && websocketRef.current.readyState !== WebSocket.CLOSED) {
            return;
        }

        const wsUrl = `ws://localhost:3000/ws?username=${username}`;
        websocketRef.current = new WebSocket(wsUrl);

        websocketRef.current.onopen = () => {
            console.log("WebSocket Connected");
            setConnectionStatus("connected");
        };

        websocketRef.current.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);

                switch (data.type) {
                    // --- CHAT ---
                    case "broadcast":
                        setMessages((prev) => [...prev, {type:'broadcast', content: data.content, username: data.username }]);
                        break;
                    case "private_message":
                        setMessages((prev) => [...prev, {type:'private_message', sender: data.sender, to: data.to, text: data.text}]);
                        break;
                    case "usernames":
                        setUsers(data.content);
                        break;
                    case 'available_rooms':
                        setAvailableRooms(data.content);
                        break;
                    case 'joined_rooms':
                        setJoinedRooms(data.content);
                        break;
                    case 'room_message':
                        setMessages((prev) => [...prev, {type: 'room_message', room: data.room, sender: data.sender, text: data.text, timestamp: data.timestamp}]);
                        break;
                    case 'room_user_count':
                        setUsersInRooms(prev => new Map(prev).set(data.room, data.count));
                        break;

                    // --- HANGMAN ---
                    case 'hangman_lobby_update':
                        setHangmanGames(data.games);
                        break;
                    case 'hangman_game_state':
                        setCurrentHangmanGame(data.gameState);
                        if (window.location.pathname !== `/home/hangman/game/${data.gameState.gameId}`) {
                            navigate(`/home/hangman/game/${data.gameState.gameId}`);
                        }
                        break;

                    // --- POKER (NOU) ---
                    case 'poker_lobby_update':
                        setPokerGames(data.games);
                        break;
                    case 'poker_game_state':
                        setCurrentPokerGame(data.gameState);
                        break;
                    case 'poker_hand':
                        // Primim cartile noastre private
                        setMyPokerHand(data.hand);
                        break;

                    case 'error':
                        alert(`Eroare server: ${data.message}`);
                        break;
                    default:
                        console.log("Mesaj necunoscut:", data.type);
                }
            } catch (e) {
                console.error("Eroare parsare mesaj WS:", e);
            }
        };

        websocketRef.current.onclose = () => {
            setConnectionStatus("disconnected");
        };
    };

    const disconnectWebSocket = () => {
        if (websocketRef.current) {
            websocketRef.current.close();
            websocketRef.current = null;
        }
        setConnectionStatus("disconnected");
    };

    const sendMessage = (message) => {
        if (websocketRef.current?.readyState === WebSocket.OPEN) {
            const messageToSend = typeof message === 'string' ? {type: "broadcast", content: message} : message;
            websocketRef.current.send(JSON.stringify(messageToSend));
        }
    };

    // --- Chat Handlers ---
    const handleCreateRoom = (roomName) => {
        sendMessage({ type: "create_room", room: roomName });
        setLastRoomChat(roomName);
        navigate(`/home/rooms/${roomName}`);
    };

    const handleJoinRoom = (roomName) => {
        sendMessage({ type: "join_room", room: roomName });
        setLastRoomChat(roomName);
        navigate(`/home/rooms/${roomName}`);
    };

    const handleLeaveRoom = (roomName) => {
        sendMessage({ type: "leave_room", room: roomName });
    };

    // --- HANGMAN Functions ---
    const fetchHangmanGames = async () => {
        try {
            const response = await fetch('/api/hangman/games');
            const data = await response.json();
            if (data.success) setHangmanGames(data.games);
        } catch (error) { console.error(error); }
    };

    const createHangmanGame = async (gameId) => {
        try {
            await fetch('/api/hangman/create', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ gameId, username })
            });
            navigate(`/home/hangman/game/${gameId}`);
        } catch (e) { console.error(e); }
    };

    const joinHangmanGame = async (gameId) => {
        try {
            await fetch('/api/hangman/join', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ gameId, username })
            });
            navigate(`/home/hangman/game/${gameId}`);
        } catch (e) { console.error(e); }
    };

    const setHangmanWord = (word) => {
        if(currentHangmanGame){
            sendMessage({ type: 'hangman_set_word', gameId: currentHangmanGame.gameId, word });
        }
    };

    const guessHangmanLetter = (letter) => {
        if(currentHangmanGame){
            sendMessage({ type: 'hangman_guess_letter', gameId: currentHangmanGame.gameId, letter });
        }
    };

    // --- POKER Functions (NOU) ---
    const fetchPokerGames = async () => {
        try {
            const response = await fetch('/api/poker/games');
            const data = await response.json();
            if (data.success) setPokerGames(data.games);
        } catch (error) { console.error(error); }
    };

    const createPokerGame = async (gameId, password, smallBlind, bigBlind, maxPlayers, stack) => {
        try {
            const res = await fetch('/api/poker/create', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    gameId, username, password,
                    smallBlind, bigBlind, maxPlayers, stack
                })
            });
            const data = await res.json();
            if(data.success) {
                navigate(`/home/poker/table/${gameId}`);
            } else {
                alert("Eroare creare joc: " + data.message);
            }
        } catch (e) { console.error(e); }
    };

    const joinPokerGame = async (gameId, password, stack) => {
        try {
            const res = await fetch('/api/poker/join', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ gameId, username, password, stack })
            });
            const data = await res.json();
            if(data.success) {
                navigate(`/home/poker/table/${gameId}`);
            } else {
                alert("Eroare alaturare: " + data.message);
            }
        } catch (e) { console.error(e); }
    };

    // Actiuni in timpul jocului de Poker (WebSocket)
    const sendPokerAction = (action, amount = 0) => {
        if (currentPokerGame) {
            sendMessage({
                type: "poker_action",
                gameId: currentPokerGame.gameId,
                action,
                amount
            });
        }
    };

    const startPokerGame = () => {
        console.log("Se incearca pornirea jocului...", currentPokerGame); // <--- LOG DEBUG
        if (currentPokerGame) {
            sendMessage({ type: "poker_start_game", gameId: currentPokerGame.gameId });
        } else {
            console.error("Nu exista joc curent selectat!"); // <--- LOG ERROR
        }
    };

    const startNewHand = () => {
        if (currentPokerGame) {
            sendMessage({ type: "poker_start_new_hand", gameId: currentPokerGame.gameId });
        }
    };

    const leavePokerGame = () => {
        if (currentPokerGame) {
            sendMessage({ type: "poker_leave_game", gameId: currentPokerGame.gameId });
            navigate('/home/poker');
            setCurrentPokerGame(null);
            setMyPokerHand([]);
        }
    };

    // --- Navigare Meniu ---
    const handlePrivateNavigation = () => lastPrivateChatPartner ? navigate(`/home/private/${lastPrivateChatPartner}`) : navigate('/home/private');
    const handleRoomNavigation = () => lastRoomChat ? navigate(`/home/rooms/${lastRoomChat}`) : navigate('/home/rooms');
    const handleHangmanNavigation = () => navigate('/home/hangman');
    const handlePokerNavigation = () => {
        if (currentPokerGame && currentPokerGame.gameId) {
            navigate(`/home/poker/table/${currentPokerGame.gameId}`);
        }else{

            navigate('/home/poker');

        }

    };

    // Componente "Enhanced"
    const EnhancedPrivateChat = ({ messages, users, username, sendMessage, connectionStatus }) => {
        if (window.location.pathname === '/home/private' && lastPrivateChatPartner) {
            return <Navigate to={`/home/private/${lastPrivateChatPartner}`} replace />;
        }
        return <PrivateChat messages={messages.filter(msg => msg.type === 'private_message')} users={users} username={username} sendMessage={sendMessage} connectionStatus={connectionStatus} />;
    };

    const EnhancedRoomChat = ({ messages, username, connectionStatus, sendMessage, availableRooms, joinedRooms, usersInRooms }) => {
        if (window.location.pathname === '/home/rooms' && lastRoomChat) {
            return <Navigate to={`/home/rooms/${lastRoomChat}`} replace />;
        }
        return <RoomChat messages={messages} username={username} connectionStatus={connectionStatus} sendMessage={sendMessage} availableRooms={availableRooms} joinedRooms={joinedRooms} usersInRooms={usersInRooms} onJoinRoom={handleJoinRoom} onCreateRoom={handleCreateRoom} onLeaveRoom={handleLeaveRoom} />;
    };

    return (
        <Routes>
            {/* RUTE PUBLICE */}
            <Route path="/" element={!isLoggedIn ? <WelcomePage /> : <Navigate to="/home" />} />
            <Route path="/login" element={<Login onLogin={handleLoginSuccess} />} />
            <Route path="/register" element={<Register />} />

            {/* RUTE PROTEJATE */}
            <Route
                path="/home"
                element={
                    isLoggedIn ? (
                        <Header
                            username={username}
                            connectionStatus={connectionStatus}
                            users={users}
                            onPrivateNavigation={handlePrivateNavigation}
                            onRoomNavigation={handleRoomNavigation}
                            onPokerNavigation={handlePokerNavigation}
                            onHangmanNavigation={handleHangmanNavigation}
                            onLogout={handleLogout}
                        />
                    ) : (
                        <Navigate to="/login" />
                    )
                }
            >
                <Route index element={<Navigate to="global" replace />} />

                <Route path="global" element={<GlobalChat messages={messages.filter(msg => msg.type === 'broadcast')} sendMessage={sendMessage} username={username} connectionStatus={connectionStatus} />} />

                <Route path="rooms" element={<EnhancedRoomChat messages={messages} username={username} connectionStatus={connectionStatus} sendMessage={sendMessage} availableRooms={availableRooms} joinedRooms={joinedRooms} usersInRooms={usersInRooms} />} />
                <Route path="rooms/:roomName" element={<RoomChatWrapper messages={messages} username={username} connectionStatus={connectionStatus} sendMessage={sendMessage} availableRooms={availableRooms} joinedRooms={joinedRooms} usersInRooms={usersInRooms} onJoinRoom={handleJoinRoom} onCreateRoom={handleCreateRoom} onLeaveRoom={handleLeaveRoom} onRoomChange={setLastRoomChat} />} />

                <Route path="private" element={<EnhancedPrivateChat messages={messages} users={users} username={username} sendMessage={sendMessage} connectionStatus={connectionStatus} />} />
                <Route path="private/:chatPartner" element={<PrivateChatWrapper messages={messages} users={users} username={username} sendMessage={sendMessage} connectionStatus={connectionStatus} onChatPartnerChange={setLastPrivateChatPartner} />} />

                {/* POKER */}
                <Route
                    path="poker"
                    element={
                        <PokerLobby
                            availableGames={pokerGames}
                            onCreateGame={createPokerGame}
                            onJoinGame={joinPokerGame}
                            onRefresh={fetchPokerGames}
                        />
                    }
                />
                <Route
                    path="poker/table/:gameId"
                    element={
                        <PokerTable
                            pokerState={currentPokerGame}
                            myHand={myPokerHand}
                            username={username}
                            onPokerAction={sendPokerAction}
                            onStartGame={startPokerGame}
                            onNewHand={startNewHand}
                            onLeaveGame={leavePokerGame}
                        />
                    }
                />

                {/* HANGMAN */}
                <Route
                    path="hangman"
                    element={
                        <HangmanLobby
                            availableGames={hangmanGames}
                            onCreateGame={createHangmanGame}
                            onJoinGame={joinHangmanGame}
                            onRefresh={fetchHangmanGames}
                        />
                    }
                />
                <Route
                    path="hangman/game/:gameId"
                    element={
                        <HangmanGame
                            gameState={currentHangmanGame}
                            username={username}
                            onSetWord={setHangmanWord}
                            onGuessLetter={guessHangmanLetter}
                        />
                    }
                />
            </Route>

            <Route path="*" element={<Navigate to="/" />} />
        </Routes>
    );
}

export default App;