import React, { useState } from 'react';
import toast from 'react-hot-toast';
import client from '../api/client';

const TABS = ['Report', 'Product Stock / Low Stock', 'Operations'];

export default function Inventory() {
  const [tab, setTab] = useState(0);

  return (
    <div>
      <h1 className="text-2xl font-bold text-white mb-6">Inventory</h1>
      <div className="flex gap-1 mb-6 border-b border-slate-700">
        {TABS.map((t, i) => (
          <button
            key={t}
            onClick={() => setTab(i)}
            className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px ${
              tab === i ? 'border-indigo-500 text-white' : 'border-transparent text-slate-400 hover:text-white'
            }`}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === 0 && <ReportTab />}
      {tab === 1 && <StockTab />}
      {tab === 2 && <OperationsTab />}
    </div>
  );
}

function ReportTab() {
  const [filters, setFilters] = useState({ categoryId: '', warehouseId: '', minStock: 0, page: 0, pageSize: 20 });
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);

  function set(field, value) {
    setFilters((p) => ({ ...p, [field]: value }));
  }

  async function load() {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      Object.entries(filters).forEach(([k, v]) => { if (v !== '') params.set(k, v); });
      const res = await client.get(`/inventory/report?${params}`);
      setRows(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to load report');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="card flex flex-wrap gap-3 items-end">
        {[
          ['categoryId', 'Category ID', 'text'],
          ['warehouseId', 'Warehouse ID', 'text'],
          ['minStock', 'Min Stock', 'number'],
          ['page', 'Page', 'number'],
          ['pageSize', 'Page Size', 'number'],
        ].map(([field, label, type]) => (
          <div key={field}>
            <label className="block text-xs text-slate-400 mb-1">{label}</label>
            <input
              type={type}
              className="input w-32"
              value={filters[field]}
              onChange={(e) => set(field, e.target.value)}
            />
          </div>
        ))}
        <button onClick={load} disabled={loading} className="btn-primary">
          {loading ? 'Loading…' : 'Load Report'}
        </button>
      </div>

      {rows.length > 0 && (
        <div className="card overflow-x-auto">
          <table className="table-base">
            <thead>
              <tr>
                {['SKU', 'Product Name', 'Warehouse', 'Region', 'Available', 'Reserved', 'Free', 'Unit Price'].map((h) => (
                  <th key={h} className="th">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={i} className="tr-hover">
                  <td className="td font-mono text-xs">{r.sku}</td>
                  <td className="td">{r.productName}</td>
                  <td className="td">{r.warehouseName}</td>
                  <td className="td">{r.region}</td>
                  <td className="td">{r.quantityAvailable}</td>
                  <td className="td">{r.quantityReserved}</td>
                  <td className="td">{r.quantityFree}</td>
                  <td className="td">{r.unitPrice != null ? r.unitPrice : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {rows.length > 0 && (
        <div className="flex gap-3 items-center">
          <button
            disabled={filters.page <= 0}
            onClick={() => { set('page', filters.page - 1); load(); }}
            className="btn-secondary text-xs"
          >Prev</button>
          <span className="text-slate-400 text-sm">Page {filters.page}</span>
          <button
            disabled={rows.length < filters.pageSize}
            onClick={() => { set('page', filters.page + 1); load(); }}
            className="btn-secondary text-xs"
          >Next</button>
        </div>
      )}
    </div>
  );
}

function StockTab() {
  const [productId, setProductId] = useState('');
  const [stockRows, setStockRows] = useState(null);
  const [threshold, setThreshold] = useState(10);
  const [limit, setLimit] = useState(100);
  const [lowRows, setLowRows] = useState(null);

  async function getStock() {
    try {
      const res = await client.get(`/inventory/products/${encodeURIComponent(productId)}/stock`);
      setStockRows(Array.isArray(res.data) ? res.data : [res.data]);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Not found');
    }
  }

  async function getLowStock() {
    try {
      const res = await client.get(`/inventory/low-stock?threshold=${threshold}&limit=${limit}`);
      setLowRows(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed');
    }
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div className="card space-y-3">
        <h3 className="font-semibold text-white">Product Stock</h3>
        <div className="flex gap-2">
          <input className="input flex-1" placeholder="Product ID" value={productId} onChange={(e) => setProductId(e.target.value)} />
          <button onClick={getStock} className="btn-primary">Get Stock</button>
        </div>
        {stockRows && (
          <div className="overflow-x-auto">
            <table className="table-base">
              <thead>
                <tr>
                  {['Warehouse', 'Available', 'Reserved', 'Free'].map((h) => <th key={h} className="th">{h}</th>)}
                </tr>
              </thead>
              <tbody>
                {stockRows.map((r, i) => (
                  <tr key={i} className="tr-hover">
                    <td className="td">{r.warehouseId || r.warehouseName}</td>
                    <td className="td">{r.quantityAvailable}</td>
                    <td className="td">{r.quantityReserved}</td>
                    <td className="td">{r.quantityFree}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card space-y-3">
        <h3 className="font-semibold text-white">Low Stock</h3>
        <div className="flex gap-2 flex-wrap">
          <div>
            <label className="text-xs text-slate-400">Threshold</label>
            <input type="number" className="input w-24 block mt-0.5" value={threshold} onChange={(e) => setThreshold(e.target.value)} />
          </div>
          <div>
            <label className="text-xs text-slate-400">Limit</label>
            <input type="number" className="input w-24 block mt-0.5" value={limit} onChange={(e) => setLimit(e.target.value)} />
          </div>
          <div className="flex items-end">
            <button onClick={getLowStock} className="btn-primary">Get Low Stock</button>
          </div>
        </div>
        {lowRows && (
          <div className="overflow-x-auto">
            <table className="table-base">
              <thead>
                <tr>
                  {['Product', 'Warehouse', 'Available', 'Reserved'].map((h) => <th key={h} className="th">{h}</th>)}
                </tr>
              </thead>
              <tbody>
                {lowRows.map((r, i) => (
                  <tr key={i} className="tr-hover">
                    <td className="td">{r.productName || r.productId}</td>
                    <td className="td">{r.warehouseName || r.warehouseId}</td>
                    <td className="td text-red-300">{r.quantityAvailable}</td>
                    <td className="td">{r.quantityReserved}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function OpForm({ title, fields, endpoint, method = 'post' }) {
  const [values, setValues] = useState(() => Object.fromEntries(fields.map((f) => [f.name, ''])));
  const [loading, setLoading] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setLoading(true);
    try {
      const body = { ...values };
      fields.forEach((f) => { if (f.type === 'number') body[f.name] = Number(body[f.name]); });
      await client[method](endpoint, body);
      toast.success(`${title} successful`);
    } catch (err) {
      toast.error(err.response?.data?.message || `${title} failed`);
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={submit} className="card space-y-3">
      <h3 className="font-semibold text-white">{title}</h3>
      {fields.map((f) => (
        <div key={f.name}>
          <label className="text-xs text-slate-400">{f.label}</label>
          <input
            type={f.type || 'text'}
            className="input w-full mt-0.5"
            value={values[f.name]}
            onChange={(e) => setValues((p) => ({ ...p, [f.name]: e.target.value }))}
            required
          />
        </div>
      ))}
      <button type="submit" disabled={loading} className="btn-primary w-full">
        {loading ? 'Submitting…' : title}
      </button>
    </form>
  );
}

function OperationsTab() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
      <OpForm
        title="Reserve"
        endpoint="/inventory/reserve"
        fields={[
          { name: 'productId', label: 'Product ID' },
          { name: 'warehouseId', label: 'Warehouse ID' },
          { name: 'quantity', label: 'Quantity', type: 'number' },
          { name: 'orderId', label: 'Order ID' },
        ]}
      />
      <OpForm
        title="Release"
        endpoint="/inventory/release"
        fields={[
          { name: 'productId', label: 'Product ID' },
          { name: 'warehouseId', label: 'Warehouse ID' },
          { name: 'quantity', label: 'Quantity', type: 'number' },
          { name: 'orderId', label: 'Order ID' },
        ]}
      />
      <OpForm
        title="Adjust"
        endpoint="/inventory/adjust"
        fields={[
          { name: 'productId', label: 'Product ID' },
          { name: 'warehouseId', label: 'Warehouse ID' },
          { name: 'delta', label: 'Delta', type: 'number' },
          { name: 'reason', label: 'Reason' },
        ]}
      />
    </div>
  );
}
