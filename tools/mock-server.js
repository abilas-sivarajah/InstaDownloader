// Testserver für die Build-Variante "mock". Liefert nachgebaute Einbettungsseiten und Testdateien.
// Start: adb reverse tcp:8099 tcp:8099 && node tools/mock-server.js
const http = require('http');
const zlib = require('zlib');

const BASE = 'http://127.0.0.1:8099';
const VIDEO = Buffer.alloc(3 * 1024 * 1024, 'clipfang-test-');

function png() {
  const w = 64, h = 64, row = Buffer.alloc(1 + w * 3);
  const raw = Buffer.concat(Array.from({ length: h }, (_, y) => { for (let x = 0; x < w; x++) row.set([x * 4, y * 4, 120], 1 + x * 3); return Buffer.from(row); }));
  const chunk = (type, data) => {
    const body = Buffer.concat([Buffer.from(type), data]);
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(zlib.crc32(body));
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr.set([8, 2, 0, 0, 0], 8);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

const video = { is_video: true, video_url: BASE + '/media/test.mp4', display_url: BASE + '/media/test.png' };
const photo = { is_video: false, display_url: BASE + '/media/test.png' };
const meta = { owner: { username: 'test.user' }, edge_media_to_caption: { edges: [{ node: { text: 'Zeile mit "Anführung" und \\ Backslash 🙂' } }] } };
const page = (ctx) => `<!doctype html><title>mock</title><body><script>requireLazy([],{"isSidecar":false,"contextJSON":${JSON.stringify(JSON.stringify(ctx))},"x":1})</script></body>`.replace('requireLazy', 'void');

const pages = {
  VIDEO00001: page({ context: { copyright_blocked: false }, gql_data: { shortcode_media: { ...video, ...meta } } }),
  SIDECAR001: page({ context: {}, gql_data: { shortcode_media: { ...meta, edge_sidecar_to_children: { edges: [{ node: photo }, { node: video }] } } } }),
  BLOCKED001: page({ context: { copyright_blocked: true }, gql_data: { shortcode_media: { ...video, ...meta } } }),
  FOREIGN001: page({ context: {}, gql_data: { shortcode_media: { ...meta, is_video: true, video_url: 'https://evil.example/a.mp4', display_url: 'https://evil.example/a.jpg' } } }),
  PRIVATE001: '<!doctype html><body><div class="EmbedIsBroken">nicht verfügbar</div></body>',
};

module.exports = { pages };
if (require.main === module) {
  http.createServer((req, res) => {
    console.log(req.method, req.url);
    if (req.url === '/media/test.mp4') { res.writeHead(200, { 'Content-Type': 'video/mp4', 'Content-Length': VIDEO.length }); return res.end(VIDEO); }
    if (req.url === '/media/test.png') { res.writeHead(200, { 'Content-Type': 'image/png' }); return res.end(png()); }
    const code = (req.url.match(/^\/(?:p|reel|tv)\/([^/]+)\/embed/) || [])[1];
    if (code === 'LOGIN00001') { res.writeHead(302, { Location: '/accounts/login/' }); return res.end(); }
    if (req.url.startsWith('/accounts/login')) { res.writeHead(200, { 'Content-Type': 'text/html' }); return res.end('<body>login</body>'); }
    res.writeHead(pages[code] ? 200 : 404, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(pages[code] || 'not found');
  }).listen(8099, '127.0.0.1', () => console.log('mock on 8099'));
}
