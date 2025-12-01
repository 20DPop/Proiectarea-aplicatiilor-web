import "./App.css";
import React, { useState, useEffect, useRef } from "react";
import { Routes, Route, Navigate, useNavigate, useParams } from "react-router-dom";

// Importam componentele
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

// --- Wrapper Components (Pentru a pasa params din URL) ---

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
    // 1. State-ul de Autentificare (initial false)
    const [isLoggedIn, setIsLoggedIn] = useState(false);
    const [username, setUsername] = useState("");

    // State-uri pentru Chat si Jocuri
    const [users, setUsers] = useState([]);
    const [messages, setMessages] = useState([]);
    const [connectionStatus, setConnectionStatus] = useState("disconnected");

    const [availableRooms, setAvailableRooms] = useState([]);
    const [joinedRooms, setJoinedRooms] = useState([]);
    const [usersInRooms, setUsersInRooms] = useState(new Map());

    const [pokerGames, setPokerGames] = useState([]);
    const [currentPokerGame, setCurrentPokerGame] = useState(null);
    const [myPokerHand, setMyPokerHand] = useState([]);

    const [hangmanGames, setHangmanGames] = useState([])
    const [currentHangmanGame, setCurrentHangmanGame] = useState(null)

    const [lastPrivateChatPartner, setLastPrivateChatPartner] = useState(() => localStorage.getItem('lastPrivateChatPartner'));
    const [lastRoomChat, setLastRoomChat] = useState(() => localStorage.getItem('lastRoomChat'));

    const websocketRef = useRef(null);
    const navigate = useNavigate();

    // 2. Conectare WebSocket cand userul se logheaza
    useEffect(() => {
        if (isLoggedIn && username) {
            connectWebSocket();
            fetchHangmanGames(); // Fetch initial pentru jocuri
        } else {
            disconnectWebSocket();
        }
        return () => {
            disconnectWebSocket();
        };
    }, [isLoggedIn, username]);

    // Funcția apelată din componenta Login.jsx când login-ul reușește
    const handleLoginSuccess = (loggedInUsername) => {
        setUsername(loggedInUsername);
        setIsLoggedIn(true);
    };

    const handleLogout = () => {
        setIsLoggedIn(false);
        setUsername("");
        disconnectWebSocket();
        navigate("/");
    };

    const connectWebSocket = () => {
        if (websocketRef.current && websocketRef.current.readyState !== WebSocket.CLOSED) {
            return;
        }

        // URL-ul backend-ului Java (Port 3000)
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
                        // Backend-ul trimite lista de jocuri cand se schimba ceva
                        setHangmanGames(data.games);
                        break;
                    case 'hangman_game_state':
                        setCurrentHangmanGame(data.gameState);
                        // Daca suntem in joc si primim update, ne asiguram ca suntem pe pagina corecta
                        if (window.location.pathname !== `/home/hangman/game/${data.gameState.gameId}`) {
                            navigate(`/home/hangman/game/${data.gameState.gameId}`);
                        }
                        break;

                    case 'error':
                        alert(`Eroare server: ${data.message}`);
                        break;
                    default:
                        // Alte tipuri (poker etc)
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

    // --- Handlers pentru Navigare si Actiuni ---

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

    // API Calls (Prin Proxy catre Java)
    const fetchHangmanGames = async () => {
        try {
            const response = await fetch('/api/hangman/games');
            const data = await response.json();
            if (data.success) setHangmanGames(data.games);
        } catch (error) {
            console.error('Error fetching hangman games:', error);
        }
    };

    const createHangmanGame = (gameId) => {
        // Pentru simplitate, trimitem cererea prin WebSocket (sau ai putea face POST fetch)
        // Dar cum GameService-ul tau asteapta WS pentru gameplay,
        // e mai bine sa definim un tip nou in Java sau sa folosim un POST endpoint.
        // Momentan, hai sa presupunem ca il creezi doar in memorie cand intri in el
        // SAU, cel mai bine: folosim un mesaj WS custom pe care l-ai putea adauga in Java.
        // Pentru acum: Backend-ul Java nu are 'create_hangman' pe WS explicit,
        // deci ar fi ideal sa folosesti un endpoint POST in controller daca vrei persistenta inainte de join.

        // SOLUTIE RAPIDA: Trimite un mesaj de setare a jocului (chiar daca nu exista inca)
        // Backend-ul Java pe care l-am scris are metoda createHangmanGame() publica, dar neapelata din WS.
        // Putem adauga un endpoint in HangmanController pentru CREATE.
        alert("Pentru a crea un joc, avem nevoie de un endpoint POST in Java. Momentan poti vedea jocurile create de altii.");
    };

    const joinHangmanGame = (gameId) => {
        // Trimitem mesaj de join prin WS (trebuie suportat in Java sau gestionat aici)
        // In Java Service avem joinHangmanGame, dar trebuie apelat.
        // Poti adauga un case in MainWebSocketHandler pentru 'join_hangman_game'.

        // Pentru DEMO: Navigam si speram ca WS se ocupa de update-uri
        navigate(`/home/hangman/game/${gameId}`);
    };

    // Functii Gameplay Hangman
    const setHangmanWord = (word) => {
        if(currentHangmanGame){
            sendMessage({
                type: 'hangman_set_word',
                gameId: currentHangmanGame.gameId,
                word
            })
        }
    }

    const guessHangmanLetter = (letter) => {
        if(currentHangmanGame){
            sendMessage({
                type: 'hangman_guess_letter',
                gameId: currentHangmanGame.gameId,
                letter
            })
        }
    }

    // --- Navigare Meniu ---
    const handlePrivateNavigation = () => lastPrivateChatPartner ? navigate(`/home/private/${lastPrivateChatPartner}`) : navigate('/home/private');
    const handleRoomNavigation = () => lastRoomChat ? navigate(`/home/rooms/${lastRoomChat}`) : navigate('/home/rooms');
    const handleHangmanNavigation = () => navigate('/home/hangman');
    const handlePokerNavigation = () => navigate('/home/poker'); // Placeholder

    // Componente "Enhanced" pentru a evita navigarea infinita
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
                            // Poti adauga un buton de Logout in Header care apeleaza handleLogout
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

                {/* POKER (Placeholder) */}
                <Route path="poker" element={<PokerLobby availableGames={pokerGames} onCreateGame={() => {}} onJoinGame={() => {}} />} />

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