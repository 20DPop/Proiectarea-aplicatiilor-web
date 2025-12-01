import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

const Register = () => {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [successMessage, setSuccessMessage] = useState("");
    const navigate = useNavigate();

    const handleSubmit = (e) => {
        e.preventDefault();
        setError("");
        setSuccessMessage("");

        fetch("/api/auth/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password }),
        })
            .then(response => {
                if (!response.ok) {
                    // Java trimite eroarea in campul "message"
                    return response.json().then(err => {
                        throw new Error(err.message || "Înregistrarea a eșuat.");
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    setSuccessMessage("Cont creat! Vei fi redirecționat la login...");
                    setTimeout(() => {
                        navigate('/login');
                    }, 2000);
                }
            })
            .catch((err) => {
                setError(err.message || "A apărut o eroare.");
            });
    };

    return (
        <div className="vh-100 d-flex justify-content-center align-items-center bg-light">
            <div className="card shadow p-4" style={{ width: '100%', maxWidth: '400px' }}>
                <div className="card-body">
                    <h2 className="card-title text-center mb-4">Înregistrare Cont Nou</h2>

                    {error && <div className="alert alert-danger">{error}</div>}
                    {successMessage && <div className="alert alert-success">{successMessage}</div>}

                    <form onSubmit={handleSubmit}>
                        <div className="mb-3">
                            <label htmlFor="username-register" className="form-label">Alege un utilizator</label>
                            <input
                                type="text"
                                className="form-control"
                                id="username-register"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                required
                            />
                        </div>
                        <div className="mb-3">
                            <label htmlFor="password-register" className="form-label">Alege o parolă</label>
                            <input
                                type="password"
                                className="form-control"
                                id="password-register"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                            />
                        </div>
                        <div className="d-grid">
                            <button type="submit" className="btn btn-primary">
                                Creează Cont
                            </button>
                        </div>
                    </form>
                    <div className="text-center mt-3">
                        <Link to="/login">Ai deja cont? Autentifică-te</Link>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Register;