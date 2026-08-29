import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import { createServer as createViteServer } from 'vite';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json({ limit: '10mb' }));

  // API Route: Health Check
  app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', app: 'Ajaz×tiktok' });
  });

  // API Route: Safe subscription URL fetch proxy
  app.post('/api/fetch-subscription', async (req, res) => {
    try {
      const { url } = req.body;
      if (!url || typeof url !== 'string') {
        return res.status(400).json({ error: 'Subscription URL is required' });
      }

      // Validate URL protocol
      const parsedUrl = new URL(url);
      if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
        return res.status(400).json({ error: 'Only HTTP and HTTPS URLs are supported' });
      }

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 15000);

      const response = await fetch(url, {
        signal: controller.signal,
        headers: {
          'User-Agent': 'ClashMeta/1.18.0 ClashForAndroid/2.5.12 AjazTiktok/1.0.0',
          'Accept': 'text/yaml, application/yaml, text/plain, application/x-yaml, */*',
        },
        redirect: 'follow',
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        return res.status(response.status).json({
          error: `Server responded with HTTP ${response.status}: ${response.statusText}`,
          statusCode: response.status,
        });
      }

      const text = await response.text();
      const contentType = response.headers.get('content-type') || 'text/plain';
      const subscriptionUserInfo = response.headers.get('subscription-userinfo') || null;

      return res.json({
        success: true,
        text,
        contentType,
        subscriptionUserInfo,
        size: text.length,
      });
    } catch (err: any) {
      console.error('Fetch subscription error:', err);
      return res.status(500).json({
        error: err.name === 'AbortError' ? 'Connection timed out while downloading configuration' : (err.message || 'Failed to download configuration'),
      });
    }
  });

  // Vite middleware for development vs static build in production
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Ajaz×tiktok server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer();
