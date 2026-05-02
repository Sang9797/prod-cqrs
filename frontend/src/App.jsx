import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import Layout from './components/Layout';
import Login from './pages/Login';
import Orders from './pages/Orders';
import Inventory from './pages/Inventory';
import GraphQLPage from './pages/GraphQLPage';
import LoadTest from './pages/LoadTest';
import Metrics from './pages/Metrics';

function ProtectedRoute({ children }) {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  return <Layout>{children}</Layout>;
}

function RootRedirect() {
  const { token } = useAuth();
  return <Navigate to={token ? '/orders' : '/login'} replace />;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Toaster
          position="top-right"
          toastOptions={{
            style: { background: '#1e293b', color: '#f8fafc', border: '1px solid #334155' },
          }}
        />
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          <Route path="/login" element={<Login />} />
          <Route path="/orders" element={<ProtectedRoute><Orders /></ProtectedRoute>} />
          <Route path="/inventory" element={<ProtectedRoute><Inventory /></ProtectedRoute>} />
          <Route path="/graphql" element={<ProtectedRoute><GraphQLPage /></ProtectedRoute>} />
          <Route path="/loadtest" element={<ProtectedRoute><LoadTest /></ProtectedRoute>} />
          <Route path="/metrics" element={<ProtectedRoute><Metrics /></ProtectedRoute>} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
