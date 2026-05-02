import axios from 'axios';

export async function gqlRequest(query, variables = {}) {
  const token = localStorage.getItem('token');
  const response = await axios.post(
    '/graphql',
    { query, variables },
    {
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    }
  );
  return response;
}
