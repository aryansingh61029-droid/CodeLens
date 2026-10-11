'use strict';

// History contains metadata only. All user-provided filenames are rendered as text.
(() => {
  let page = 0;
  const date = value => new Date(value).toLocaleString();
  const controls = ['refresh-history', 'history-prev', 'history-next'];

  async function get(url) {
    const response = await fetch(url, {headers: {Accept: 'application/json'}, cache: 'no-store'});
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.detail || `Could not load history (${response.status}).`);
    if (!body) throw new Error('The server returned an unreadable response.');
    return body;
  }

  async function detail(id) {
    $('error').hidden = true;
    try {
      const result = await get(`/api/history/${encodeURIComponent(id)}`);
      $('history-detail-title').textContent = `${date(result.summary.createdAt)} · ${result.summary.type} · ${id}`;
      const rows = tableHeader($('history-pairs'), ['Rank', 'First program', 'Second program', 'Similarity', 'Verdict']);
      result.pairs.forEach((pair, index) => {
        const row = element('tr');
        [index + 1, pair.first, pair.second, percentage(pair.similarity)].forEach(value => row.append(element('td', value)));
        const cell = element('td'), badge = element('span', pair.verdict);
        badge.className = `badge ${pair.verdict}`; cell.append(badge); row.append(cell); rows.append(row);
      });
      if (!result.pairs.length) {
        const row = element('tr'), cell = element('td', 'No same-language pairs in this analysis.');
        cell.colSpan = 5; row.append(cell); rows.append(row);
      }
      $('history-detail').hidden = false;
      $('history-detail').scrollIntoView({behavior: 'smooth', block: 'start'});
    } catch (error) {
      showError(error instanceof TypeError ? 'Cannot reach the server. Please try again.' : error.message);
    }
  }

  async function load(target = 0) {
    controls.forEach(id => { $(id).disabled = true; });
    $('error').hidden = true;
    $('status').textContent = 'Loading saved comparisons…';
    $('history-detail').hidden = true;
    $('history-table').setAttribute('aria-busy', 'true');
    try {
      const result = await get(`/api/history?page=${target}&size=20`);
      page = result.page;
      const rows = tableHeader($('history-table'), ['Saved at', 'Type', 'Submissions', 'Pairs', 'Top similarity', 'Details']);
      result.items.forEach(item => {
        const row = element('tr');
        [date(item.createdAt), item.type, item.submissionCount, item.pairCount,
          item.topSimilarity === null ? '—' : percentage(item.topSimilarity)].forEach(value => row.append(element('td', value)));
        const cell = element('td'), button = element('button', 'View pairs');
        button.type = 'button'; button.className = 'copy-button';
        button.setAttribute('aria-label', `View pairs for ${item.type} analysis saved ${date(item.createdAt)}`);
        button.addEventListener('click', () => detail(item.id));
        cell.append(button); row.append(cell); rows.append(row);
      });
      if (!result.items.length) {
        const row = element('tr'), cell = element('td', 'No saved comparisons yet. Run a comparison to get started.');
        cell.colSpan = 6; row.append(cell); rows.append(row);
      }
      $('history-page').textContent = `Page ${page + 1} · ${result.items.length} analyses shown`;
      $('history-prev').disabled = page === 0;
      $('history-next').disabled = !result.hasMore || page === 1000;
      $('status').textContent = 'Comparison history loaded.';
    } catch (error) {
      $('status').textContent = '';
      showError(error instanceof TypeError ? 'Cannot reach the server. Please try again.' : error.message);
    } finally {
      $('refresh-history').disabled = false;
      $('history-table').removeAttribute('aria-busy');
    }
  }
  $('refresh-history').addEventListener('click', () => load(0));
  $('history-prev').addEventListener('click', () => load(page - 1));
  $('history-next').addEventListener('click', () => load(page + 1));
  load();
})();
