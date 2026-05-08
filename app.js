const express = require('express');
const mysql = require('mysql2');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));

const DB_CONFIG = {
  host: process.env.DB_HOST || 'db',
  user: process.env.DB_USER || 'taskuser',
  password: process.env.DB_PASS || 'taskpass',
  database: process.env.DB_NAME || 'taskdb',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
};

let pool;

function createPool() {
  pool = mysql.createPool(DB_CONFIG).promise();
  console.log('MySQL connection pool created.');
}

async function waitForDB(retries = 15, delay = 3000) {
  for (let i = 0; i < retries; i++) {
    try {
      await pool.query('SELECT 1');
      console.log('Connected to MySQL.');
      return;
    } catch (err) {
      console.log(`MySQL not ready (attempt ${i + 1}/${retries}). Retrying in ${delay}ms...`);
      await new Promise(r => setTimeout(r, delay));
    }
  }
  throw new Error('MySQL did not become ready in time.');
}

async function ensureSchema() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS tasks (
      id INT AUTO_INCREMENT PRIMARY KEY,
      text VARCHAR(500) NOT NULL,
      done TINYINT(1) NOT NULL DEFAULT 0,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB
  `);
  console.log('Schema ensured.');
}

// ---------- Health ----------
app.get('/health', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    res.status(200).json({ status: 'ok', db: 'up' });
  } catch (e) {
    res.status(500).json({ status: 'error', db: 'down' });
  }
});

// ---------- API ----------
app.get('/api/tasks', async (req, res) => {
  try {
    const [rows] = await pool.query(
      'SELECT id, text, done FROM tasks ORDER BY id DESC'
    );
    const tasks = rows.map(r => ({ id: r.id, text: r.text, done: !!r.done }));
    res.json(tasks);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/tasks', async (req, res) => {
  try {
    const text = (req.body && req.body.text || '').toString().trim();
    if (!text) return res.status(400).json({ error: 'text is required' });
    const [result] = await pool.query(
      'INSERT INTO tasks (text, done) VALUES (?, 0)', [text]
    );
    res.status(201).json({ id: result.insertId, text, done: false });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.put('/api/tasks/:id/toggle', async (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    if (!Number.isFinite(id)) return res.status(400).json({ error: 'invalid id' });
    await pool.query('UPDATE tasks SET done = 1 - done WHERE id = ?', [id]);
    const [rows] = await pool.query('SELECT id, text, done FROM tasks WHERE id = ?', [id]);
    if (rows.length === 0) return res.status(404).json({ error: 'not found' });
    res.json({ id: rows[0].id, text: rows[0].text, done: !!rows[0].done });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.delete('/api/tasks/:id', async (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    if (!Number.isFinite(id)) return res.status(400).json({ error: 'invalid id' });
    await pool.query('DELETE FROM tasks WHERE id = ?', [id]);
    res.status(204).send();
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ---------- Start ----------
async function start() {
  createPool();
  await waitForDB();
  await ensureSchema();
  app.listen(PORT, () => {
    console.log(`Task Manager listening on port ${PORT}`);
  });
}

start().catch(err => {
  console.error('Fatal startup error:', err);
  process.exit(1);
});
