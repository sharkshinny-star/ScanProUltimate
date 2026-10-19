const http = require('http');

const PORT = Number(process.env.PORT) || 3000;

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
    'Content-Length': Buffer.byteLength(PAGE),
    'Cache-Control': 'no-cache',
  });
  res.end(req.method === 'HEAD' ? undefined : PAGE);
});

server.listen(PORT, () => {
  console.log(`ScanPro server listening on http://localhost:${PORT}`);
});
