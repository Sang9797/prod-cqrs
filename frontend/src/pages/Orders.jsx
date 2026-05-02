import React, { useState } from 'react';
import toast from 'react-hot-toast';
import client from '../api/client';
import Modal from '../components/Modal';
import { PlusIcon, MagnifyingGlassIcon } from '@heroicons/react/24/outline';

const STATUS_COLORS = {
  PENDING: 'bg-yellow-900/50 text-yellow-300 border border-yellow-700',
  CONFIRMED: 'bg-green-900/50 text-green-300 border border-green-700',
  SHIPPED: 'bg-blue-900/50 text-blue-300 border border-blue-700',
  DELIVERED: 'bg-teal-900/50 text-teal-300 border border-teal-700',
  CANCELLED: 'bg-red-900/50 text-red-300 border border-red-700',
};

function StatusBadge({ status }) {
  return (
    <span className={`px-2 py-0.5 rounded text-xs font-medium ${STATUS_COLORS[status] || 'bg-slate-700 text-slate-300'}`}>
      {status}
    </span>
  );
}

const emptyItem = () => ({ productId: '', productName: '', quantity: 1, unitPrice: 0, currency: 'USD' });

export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [searchCustomerId, setSearchCustomerId] = useState('');
  const [getByIdInput, setGetByIdInput] = useState('');
  const [loading, setLoading] = useState(false);

  const [showNewModal, setShowNewModal] = useState(false);
  const [newOrder, setNewOrder] = useState({ customerId: '', items: [emptyItem()] });
  const [placing, setPlacing] = useState(false);

  const [cancelModal, setCancelModal] = useState(null);
  const [cancelReason, setCancelReason] = useState('');

  async function searchByCustomer() {
    if (!searchCustomerId.trim()) return;
    setLoading(true);
    try {
      const res = await client.get(`/orders?customerId=${encodeURIComponent(searchCustomerId)}`);
      setOrders(Array.isArray(res.data) ? res.data : [res.data]);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to fetch orders');
    } finally {
      setLoading(false);
    }
  }

  async function getById() {
    if (!getByIdInput.trim()) return;
    setLoading(true);
    try {
      const res = await client.get(`/orders/${getByIdInput.trim()}`);
      setOrders([res.data]);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Order not found');
    } finally {
      setLoading(false);
    }
  }

  async function confirmOrder(orderId) {
    try {
      await client.post(`/orders/${orderId}/confirm`);
      toast.success('Order confirmed');
      setOrders((prev) => prev.map((o) => o.orderId === orderId ? { ...o, status: 'CONFIRMED' } : o));
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to confirm order');
    }
  }

  async function cancelOrder() {
    try {
      await client.delete(`/orders/${cancelModal}`, { data: { reason: cancelReason } });
      toast.success('Order cancelled');
      setOrders((prev) => prev.map((o) => o.orderId === cancelModal ? { ...o, status: 'CANCELLED' } : o));
      setCancelModal(null);
      setCancelReason('');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to cancel order');
    }
  }

  function updateItem(index, field, value) {
    setNewOrder((prev) => {
      const items = [...prev.items];
      items[index] = { ...items[index], [field]: value };
      return { ...prev, items };
    });
  }

  function addItem() {
    setNewOrder((prev) => ({ ...prev, items: [...prev.items, emptyItem()] }));
  }

  function removeItem(index) {
    setNewOrder((prev) => ({ ...prev, items: prev.items.filter((_, i) => i !== index) }));
  }

  async function placeOrder(e) {
    e.preventDefault();
    setPlacing(true);
    try {
      const payload = {
        customerId: newOrder.customerId,
        items: newOrder.items.map((it) => ({
          ...it,
          quantity: Number(it.quantity),
          unitPrice: Number(it.unitPrice),
        })),
      };
      const res = await client.post('/orders', payload);
      toast.success('Order placed');
      setOrders((prev) => [res.data, ...prev]);
      setShowNewModal(false);
      setNewOrder({ customerId: '', items: [emptyItem()] });
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to place order');
    } finally {
      setPlacing(false);
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-white">Orders</h1>
        <button onClick={() => setShowNewModal(true)} className="btn-primary flex items-center gap-2">
          <PlusIcon className="w-4 h-4" /> New Order
        </button>
      </div>

      <div className="card mb-6 flex flex-wrap gap-4">
        <div className="flex gap-2 items-center">
          <input
            className="input"
            placeholder="Customer ID"
            value={searchCustomerId}
            onChange={(e) => setSearchCustomerId(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && searchByCustomer()}
          />
          <button onClick={searchByCustomer} className="btn-primary flex items-center gap-1">
            <MagnifyingGlassIcon className="w-4 h-4" /> Search
          </button>
        </div>
        <div className="flex gap-2 items-center">
          <input
            className="input"
            placeholder="Order ID"
            value={getByIdInput}
            onChange={(e) => setGetByIdInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && getById()}
          />
          <button onClick={getById} className="btn-secondary">Get by ID</button>
        </div>
      </div>

      {loading && <p className="text-slate-400 text-sm">Loading…</p>}

      {orders.length > 0 && (
        <div className="card overflow-x-auto">
          <table className="table-base">
            <thead>
              <tr>
                <th className="th">Order ID</th>
                <th className="th">Customer</th>
                <th className="th">Status</th>
                <th className="th">Total</th>
                <th className="th">Created</th>
                <th className="th">Actions</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.orderId} className="tr-hover">
                  <td className="td font-mono text-xs text-slate-400">{order.orderId?.slice(0, 8)}…</td>
                  <td className="td">{order.customerId}</td>
                  <td className="td"><StatusBadge status={order.status} /></td>
                  <td className="td">{order.totalAmount} {order.currency}</td>
                  <td className="td text-slate-400 text-xs">{order.createdAt ? new Date(order.createdAt).toLocaleString() : '—'}</td>
                  <td className="td">
                    <div className="flex gap-2">
                      {order.status === 'PENDING' && (
                        <button onClick={() => confirmOrder(order.orderId)} className="btn-success text-xs px-2 py-1">Confirm</button>
                      )}
                      {(order.status === 'PENDING' || order.status === 'CONFIRMED') && (
                        <button onClick={() => { setCancelModal(order.orderId); setCancelReason(''); }} className="btn-danger text-xs px-2 py-1">Cancel</button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showNewModal && (
        <Modal title="Place New Order" onClose={() => setShowNewModal(false)}>
          <form onSubmit={placeOrder} className="space-y-4">
            <div>
              <label className="block text-sm text-slate-400 mb-1">Customer ID</label>
              <input
                className="input w-full"
                value={newOrder.customerId}
                onChange={(e) => setNewOrder((p) => ({ ...p, customerId: e.target.value }))}
                required
              />
            </div>
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="text-sm text-slate-400">Items</label>
                <button type="button" onClick={addItem} className="btn-secondary text-xs px-2 py-1">+ Add Item</button>
              </div>
              <div className="space-y-2">
                {newOrder.items.map((item, i) => (
                  <div key={i} className="bg-slate-700/50 rounded p-3 space-y-2">
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <label className="text-xs text-slate-400">Product ID</label>
                        <input className="input w-full mt-0.5" value={item.productId} onChange={(e) => updateItem(i, 'productId', e.target.value)} required />
                      </div>
                      <div>
                        <label className="text-xs text-slate-400">Product Name</label>
                        <input className="input w-full mt-0.5" value={item.productName} onChange={(e) => updateItem(i, 'productName', e.target.value)} required />
                      </div>
                      <div>
                        <label className="text-xs text-slate-400">Quantity</label>
                        <input type="number" min="1" className="input w-full mt-0.5" value={item.quantity} onChange={(e) => updateItem(i, 'quantity', e.target.value)} required />
                      </div>
                      <div>
                        <label className="text-xs text-slate-400">Unit Price</label>
                        <input type="number" min="0" step="0.01" className="input w-full mt-0.5" value={item.unitPrice} onChange={(e) => updateItem(i, 'unitPrice', e.target.value)} required />
                      </div>
                      <div>
                        <label className="text-xs text-slate-400">Currency</label>
                        <input className="input w-full mt-0.5" value={item.currency} onChange={(e) => updateItem(i, 'currency', e.target.value)} />
                      </div>
                    </div>
                    {newOrder.items.length > 1 && (
                      <button type="button" onClick={() => removeItem(i)} className="text-red-400 text-xs hover:text-red-300">Remove</button>
                    )}
                  </div>
                ))}
              </div>
            </div>
            <button type="submit" disabled={placing} className="btn-primary w-full">
              {placing ? 'Placing…' : 'Place Order'}
            </button>
          </form>
        </Modal>
      )}

      {cancelModal && (
        <Modal title="Cancel Order" onClose={() => setCancelModal(null)}>
          <div className="space-y-4">
            <p className="text-slate-300 text-sm">Provide a reason for cancellation:</p>
            <input
              className="input w-full"
              placeholder="Reason"
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
            />
            <div className="flex gap-3 justify-end">
              <button onClick={() => setCancelModal(null)} className="btn-secondary">Cancel</button>
              <button onClick={cancelOrder} className="btn-danger">Confirm Cancel</button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
