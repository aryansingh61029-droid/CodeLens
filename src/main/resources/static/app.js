'use strict';
const $ = id => document.getElementById(id);
const selectedFiles = [null, null, null];
const sourceLimit = 256 * 1024;
const encoder = new TextEncoder();
const element = (tag, text) => { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; return node; };
const percentage = value => `${Number(value).toFixed(1)}%`;
const color = score => score >= 70 ? '#be533b' : score >= 35 ? '#be8c21' : '#147d69';
document.querySelectorAll('[name="mode"]').forEach(input => input.addEventListener('change', () => {
  const paste = input.value === 'paste'; $('paste-options').hidden = !paste;
  document.querySelectorAll('#compare-form .dropzone').forEach(node => { node.hidden = paste; });
  document.querySelectorAll('.paste-field').forEach(node => { node.hidden = !paste; });
}));
function pickFile(slot, files) {
  if (files.length !== 1) { showError('Choose exactly one file for this area.'); return; }
  selectedFiles[slot] = files[0]; $('filename' + (slot + 1)).textContent = `${files[0].name} · ${(files[0].size / 1024).toFixed(1)} KiB`;
  $('error').hidden = true;
}
document.querySelectorAll('.dropzone').forEach(zone => {
  const slot = Number(zone.dataset.slot);
  zone.querySelector('input').addEventListener('change', event => { if (event.target.files.length) pickFile(slot, event.target.files); });
  zone.addEventListener('dragover', event => { event.preventDefault(); zone.classList.add('dragging'); });
  zone.addEventListener('dragleave', () => zone.classList.remove('dragging'));
  zone.addEventListener('drop', event => { event.preventDefault(); zone.classList.remove('dragging'); pickFile(slot, event.dataTransfer.files); });
});
window.addEventListener('dragover', event => event.preventDefault());
window.addEventListener('drop', event => event.preventDefault());
function showError(message) { $('error').textContent = message; $('error').hidden = false; }
async function request(url, options) {
  const response = await fetch(url, { method: 'POST', ...options });
  const payload = await response.json().catch(() => null);
  if (!response.ok) throw new Error(payload?.detail || payload?.title || `Request failed (${response.status}).`);
  if (!payload) throw new Error('The server returned an unreadable response.');
  return payload;
}
async function submit(form, action) {
  const button = form.querySelector('[type="submit"]'); button.disabled = true;
  form.setAttribute('aria-busy', 'true'); $('error').hidden = true; $('status').textContent = 'Analyzing source code…';
  try { await action(); $('status').textContent = 'Analysis complete. Saved to comparison history.'; }
  catch (error) { $('status').textContent = ''; showError(error instanceof TypeError ? 'Cannot reach the server. Open this page through the running Spring Boot app and try again.' : error.message); }
  finally { button.disabled = false; form.removeAttribute('aria-busy'); }
}
$('compare-form')?.addEventListener('submit', event => {
  event.preventDefault(); submit(event.currentTarget, async () => {
    $('result').hidden = true; let options;
    if (document.querySelector('[name="mode"]:checked').value === 'paste') {
      const code1 = $('code1').value, code2 = $('code2').value;
      if (!code1.trim() || !code2.trim()) throw new Error('Paste both programs before comparing.');
      if ([code1, code2].some(code => encoder.encode(code).length > sourceLimit)) throw new Error('Each program must be 256 KiB or smaller.');
      options = { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ language: $('language').value, code1, code2 }) };
    } else {
      if (!selectedFiles[0] || !selectedFiles[1]) throw new Error('Select both source files before comparing.');
      if (selectedFiles.slice(0, 2).some(file => !file.size || file.size > sourceLimit)) throw new Error('Source files must be nonempty and 256 KiB or smaller.');
      const body = new FormData(); body.append('file1', selectedFiles[0]); body.append('file2', selectedFiles[1]); options = { body };
    }
    renderComparison(await request('/api/compare', options));
  });
});
function renderSource(prefix, source) {
  $(prefix + '-title').textContent = `${source.name} · ${source.tokenCount} tokens · ${source.fingerprintCount} fingerprints`;
  const container = $(prefix + '-code'); container.replaceChildren(); let cursor = 0;
  // DOM text nodes preserve source literally, including HTML-like code and UTF-16 offsets.
  for (const range of source.matches) {
    const start = Math.max(cursor, Math.min(source.code.length, range.start));
    const end = Math.max(start, Math.min(source.code.length, range.end));
    container.append(document.createTextNode(source.code.slice(cursor, start)));
    if (end > start) container.append(element('mark', source.code.slice(start, end)));
    cursor = end;
  }
  container.append(document.createTextNode(source.code.slice(cursor)));
}
function renderComparison(result) {
  animateGauge(result.similarity);
  $('verdict').textContent = `${result.verdict} similarity`;
  $('verdict').className = `badge ${result.verdict}`;
  $('metrics').textContent = `${result.sharedFingerprints} shared fingerprints / ${result.totalFingerprints} distinct fingerprints in the union`;
  renderSource('first', result.first); renderSource('second', result.second); $('result').hidden = false;
  $('result').scrollIntoView({ behavior: 'smooth', block: 'start' });
}
$('batch-form')?.addEventListener('submit', event => {
  event.preventDefault(); submit(event.currentTarget, async () => {
    $('batch-result').hidden = true; const file = selectedFiles[2];
    if (!file || !file.size || file.size > 2 * 1024 * 1024) throw new Error('Select a nonempty ZIP of 2 MiB or smaller.');
    const body = new FormData(); body.append('file', file); renderBatch(await request('/api/compare/batch', { body }));
  });
});
function tableHeader(table, names) {
  table.replaceChildren(); const head = element('thead'), row = element('tr');
  names.forEach(name => { const cell = element('th', name); cell.scope = 'col'; row.append(cell); }); head.append(row); table.append(head);
  const body = element('tbody'); table.append(body); return body;
}
function renderBatch(result) {
  const matrix = tableHeader($('matrix'), ['File', ...result.files.map((_, index) => String(index + 1))]);
  result.matrix.forEach((values, index) => {
    const row = element('tr'), label = element('th', String(index + 1)); label.scope = 'row'; row.append(label);
    values.forEach((score, other) => { const cell = element('td', score === null ? '—' : percentage(score));
      cell.title = `${result.files[index]} / ${result.files[other]}`;
      if (score !== null) cell.style.backgroundColor = score >= 70 ? '#ffe4e6' : score >= 35 ? '#fef3c7' : '#ecfdf5'; row.append(cell); }); matrix.append(row);
  });
  $('file-key').replaceChildren(...result.files.map(file => element('li', file)));
  const pairs = tableHeader($('pairs'), ['Rank', 'First program', 'Second program', 'Similarity', 'Verdict']);
  result.pairs.forEach((pair, index) => {
    const row = element('tr');
    [index + 1, pair.first, pair.second, percentage(pair.similarity)].forEach(value => row.append(element('td', value)));
    const cell = element('td'), badge = element('span', pair.verdict);
    badge.className = `badge ${pair.verdict}`; cell.append(badge); row.append(cell); pairs.append(row);
  });
  if (!result.pairs.length) { const row = element('tr'), cell = element('td', 'No same-language pairs to compare.'); cell.colSpan = 5; row.append(cell); pairs.append(row); }
  $('batch-summary').textContent = `${result.files.length} submissions · ${result.pairs.length} pairs`;
  $('batch-result').hidden = false; $('batch-result').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

let gaugeFrame;
function animateGauge(target) {
  cancelAnimationFrame(gaugeFrame);
  const started = performance.now(), duration = matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 850;
  $('gauge').setAttribute('aria-valuenow', target);
  $('gauge-arc').style.stroke = color(target);
  function tick(now) {
    const progress = duration === 0 ? 1 : Math.min(1, (now - started) / duration);
    const value = target * (1 - Math.pow(1 - progress, 3));
    $('score').textContent = percentage(value);
    $('gauge-arc').style.strokeDashoffset = 2 * Math.PI * 52 * (1 - value / 100);
    if (progress < 1) gaugeFrame = requestAnimationFrame(tick);
  }
  gaugeFrame = requestAnimationFrame(tick);
}

function renderSamples() {
  const language = $('sample-language').value;
  const titles = ['Baseline', 'Renamed & reformatted', 'Different logic'];
  const descriptions = ['Use this as your first program.', 'Names and comments change; normalized tokens match.', 'A different structure for a low-similarity check.'];
  $('sample-cards').replaceChildren(...window.CODE_SAMPLES[language].map((code, index) => {
    const card = element('article'); card.className = 'panel';
    const header = element('div'); header.className = 'mb-3 flex items-center justify-between gap-2';
    const title = element('h2', titles[index]); title.className = 'font-semibold';
    const copy = element('button', 'Copy code'); copy.type = 'button'; copy.className = 'copy-button';
    copy.setAttribute('aria-label', `Copy ${titles[index]} code`);
    const area = element('textarea'); area.readOnly = true; area.value = code;
    area.className = 'code-input sample-code'; area.spellcheck = false;
    area.setAttribute('aria-label', `${titles[index]} sample code`);
    copy.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(code);
        $('status').textContent = `${titles[index]} code copied.`;
      } catch {
        area.focus(); area.select();
        $('status').textContent = 'Code selected. Press Ctrl+C (or Command+C) to copy.';
      }
    });
    header.append(title, copy); card.append(header);
    const description = element('p', descriptions[index]); description.className = 'mb-4 text-xs leading-relaxed text-slate-500';
    card.append(description, area); return card;
  }));
  document.querySelectorAll('[data-preset]').forEach(link => {
    link.href = `/?sample=${link.dataset.preset}&language=${language}`;
  });
}
if ($('sample-language')) {
  $('sample-language').addEventListener('change', renderSamples);
  renderSamples();
}
if ($('compare-form')) {
  const params = new URLSearchParams(location.search);
  const language = params.get('language'), preset = params.get('sample');
  if (Object.hasOwn(window.CODE_SAMPLES, language) && ['identical', 'renamed', 'different'].includes(preset)) {
    const input = document.querySelector('[name="mode"][value="paste"]');
    input.checked = true; input.dispatchEvent(new Event('change'));
    $('language').value = language;
    $('code1').value = window.CODE_SAMPLES[language][0];
    $('code2').value = window.CODE_SAMPLES[language][preset === 'identical' ? 0 : preset === 'renamed' ? 1 : 2];
    $('status').textContent = 'Sample pair loaded. Select Compare programs to see the result.';
  }
}
