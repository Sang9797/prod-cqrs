import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';

export default function JsonViewer({ data }) {
  const text = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
  return (
    <SyntaxHighlighter
      language="json"
      style={vscDarkPlus}
      customStyle={{ margin: 0, borderRadius: '0.375rem', fontSize: '0.8rem', background: '#0d1117' }}
    >
      {text}
    </SyntaxHighlighter>
  );
}
