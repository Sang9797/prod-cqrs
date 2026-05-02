import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import {
  ShoppingCartIcon,
  CubeIcon,
  CodeBracketIcon,
  BoltIcon,
  ChartBarIcon,
  ArrowRightOnRectangleIcon,
} from '@heroicons/react/24/outline';

const navItems = [
  { to: '/orders', label: 'Orders', Icon: ShoppingCartIcon },
  { to: '/inventory', label: 'Inventory', Icon: CubeIcon },
  { to: '/graphql', label: 'GraphQL', Icon: CodeBracketIcon },
  { to: '/loadtest', label: 'Load Test', Icon: BoltIcon },
  { to: '/metrics', label: 'Metrics', Icon: ChartBarIcon },
];

export default function Layout({ children }) {
  const { username, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="flex h-screen overflow-hidden">
      <aside className="w-52 min-w-[208px] bg-slate-800 border-r border-slate-700 flex flex-col">
        <div className="px-4 py-5 border-b border-slate-700">
          <span className="text-white font-bold text-lg tracking-tight">CQRS</span>
          <span className="text-indigo-400 font-bold text-lg"> Dashboard</span>
        </div>
        <nav className="flex-1 py-4 space-y-1 px-2">
          {navItems.map(({ to, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-400 hover:bg-slate-700 hover:text-white'
                }`
              }
            >
              <Icon className="w-5 h-5 flex-shrink-0" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="px-4 py-4 border-t border-slate-700">
          <p className="text-xs text-slate-400 mb-2 truncate">{username}</p>
          <button onClick={handleLogout} className="flex items-center gap-2 text-slate-400 hover:text-white text-sm transition-colors">
            <ArrowRightOnRectangleIcon className="w-4 h-4" />
            Logout
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-y-auto bg-slate-900 p-6">
        {children}
      </main>
    </div>
  );
}
