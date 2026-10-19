const http = require('http');

const PORT = Number(process.env.PORT) || 3000;
// Loopback only by default so the server isn't reachable from the network; set HOST to override.
const HOST = process.env.HOST || '127.0.0.1';

const PAGE = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <title>ScanPro Ultimate</title>
  </head>
  <body>
    <h1>ScanPro Ultimate</h1>
    <p>Server is running.</p>
  </body>
</html>
`;

const server = http.createServer((req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { Allow: 'GET, HEAD' });
    res.end();
    return;
  }

  res.writeHead(200, {
    'Content-Type': 'text/html; charset=UTF-8',
    'X-Content-Type-Options': 'nosniff',
    'Content-Security-Policy': "default-src 'none'",
    'Referrer-Policy': 'no-referrer',
    'Content-Length': Buffer.byteLength(PAGE),
    'Cache-Control': 'no-cache',
  });
  res.end(req.method === 'HEAD' ? undefined : PAGE);
});

server.listen(PORT, HOST, () => {
  console.log(`ScanPro server listening on http://${HOST}:${PORT}`);
});
