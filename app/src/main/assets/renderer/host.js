(function () {
  'use strict';

  var state = {
    view: null,
    ready: false,
    activeChartId: null,
    activeRevision: null,
    selectedRow: -1,
    tooltipEnabled: true,
    units: '',
    exportTitle: '',
    exportSubtitle: '',
    exportCaption: '',
    exportSource: '',
    exportDark: false,
    chartBorder: false,
    transparentExportBackground: false,
    hiddenSeries: [],
    series: [],
    pointerMode: 'replace',
    lasso: null,
    suppressClickUntil: 0
  };

  function post(message) {
    try {
      CloudyBridge.postMessage(JSON.stringify(message));
    } catch (error) {
      // If the native bridge is unavailable there is no safe fallback channel.
      // The host will time out and present an unavailable renderer.
    }
  }

  function fail(code, detail, chartId, revision) {
    var message = { type: 'RendererError', code: code, detail: String(detail || 'renderer failure') };
    if (typeof chartId === 'string' && typeof revision === 'number') {
      message.chartId = chartId;
      message.revision = revision;
    }
    post(message);
  }

  function setupLegendToggle(chartId, revision) {
    var labels = document.querySelectorAll('#view svg .role-legend-label');
    Array.prototype.forEach.call(labels, function (labelNode, index) {
      var label = String(labelNode.textContent || '').trim();
      var series = state.series[index] || label;
      if (!series) return;
      var hidden = state.hiddenSeries.indexOf(series) >= 0;
      labelNode.setAttribute('opacity', hidden ? '0.45' : '1');
      var box;
      try {
        box = labelNode.getBBox();
      } catch (error) {
        box = null;
      }
      if (!box || !isFinite(box.width) || !isFinite(box.height) ||
          box.width <= 0 || box.height <= 0) {
        return;
      }
      var hit = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
      hit.setAttribute('class', 'cloudy-legend-hit');
      hit.setAttribute('x', String(box.x - 3));
      hit.setAttribute('y', String(box.y - 1));
      hit.setAttribute('width', String(box.width + 6));
      hit.setAttribute('height', String(box.height + 2));
      hit.setAttribute('fill', 'transparent');
      hit.style.pointerEvents = 'all';
      hit.style.cursor = 'pointer';
      var toggleSeries = function (event) {
        if (event && event.stopPropagation) event.stopPropagation();
        post({
          type: 'LegendToggle',
          chartId: chartId,
          revision: revision,
          series: series
        });
      };
      hit.onclick = toggleSeries;
      labelNode.parentNode.appendChild(hit);
    });
  }

  function hideTooltip() {
    var tooltip = document.getElementById('tooltip');
    if (tooltip) tooltip.hidden = true;
  }

  function showTooltip(datum) {
    if (!state.tooltipEnabled || !datum || typeof datum.sourceRowIndex !== 'number') return;
    var tooltip = document.getElementById('tooltip');
    if (!tooltip || !state.view) return;
    var exact = datum.exactY !== null && datum.exactY !== undefined
      ? String(datum.exactY)
      : (typeof datum.y === 'number' && isFinite(datum.y) ? String(datum.y) : 'NULL');
    var series = datum.series ? String(datum.series) + ' · ' : '';
    tooltip.textContent = series + String(datum.x || '') + ' = ' + exact +
      (state.units ? ' ' + state.units : '');
    tooltip.hidden = false;
    var x = 20;
    var y = 20;
    try {
      var origin = typeof state.view.origin === 'function' ? state.view.origin() : [0, 0];
      var scaleX = state.view.scale('x');
      var scaleY = state.view.scale('y');
      var sourceY = datum.y;
      if (typeof sourceY !== 'number' || !isFinite(sourceY)) {
        var rows = state.view.data('table') || [];
        for (var i = 0; i < rows.length; i++) {
          if (rows[i] && rows[i].sourceRowIndex === datum.sourceRowIndex) {
            sourceY = rows[i].y;
            break;
          }
        }
      }
      x = origin[0] + scaleX(datum.x) + (scaleX.bandwidth ? scaleX.bandwidth() / 2 : 0) + 10;
      y = typeof sourceY === 'number' && isFinite(sourceY)
        ? origin[1] + scaleY(sourceY) - 28
        : origin[1] + 10;
    } catch (error) {
      x = 20;
      y = 20;
    }
    requestAnimationFrame(function () {
      var svg = document.querySelector('#view svg');
      var ctm = svg && svg.getScreenCTM ? svg.getScreenCTM() : null;
      if (ctm) {
        x = ctm.e + x * ctm.a;
        y = ctm.f + y * ctm.d;
      }
      var width = tooltip.offsetWidth || 0;
      var height = tooltip.offsetHeight || 0;
      var maxX = (document.documentElement.clientWidth || window.innerWidth || 0) - width - 8;
      var maxY = (document.documentElement.clientHeight || window.innerHeight || 0) - height - 8;
      tooltip.style.left = Math.max(8, Math.min(x, maxX)) + 'px';
      tooltip.style.top = Math.max(8, Math.min(y, maxY)) + 'px';
    });
  }

  function selectionMode(event) {
    if (event && event.shiftKey) return 'range';
    if (event && (event.ctrlKey || event.metaKey)) return 'toggle';
    return 'replace';
  }

  function postSelection(chartId, revision, datum, mode, pointer) {
    if (!datum || typeof datum.sourceRowIndex !== 'number') return;
    state.selectedRow = datum.sourceRowIndex;
    showTooltip(datum);
    post({
      type: 'SelectionIntent',
      chartId: chartId,
      revision: revision,
      sourceRowIndex: datum.sourceRowIndex,
      series: String(datum.series || ''),
      x: String(datum.x || ''),
      y: typeof datum.y === 'number' && isFinite(datum.y) ? datum.y : null,
      mode: mode === 'toggle' || mode === 'range' ? mode : 'replace',
      pointer: pointer === true
    });
  }

  function postFocusExit() {
    var message = { type: 'FocusExitIntent' };
    if (state.activeChartId !== null && state.activeRevision !== null) {
      message.chartId = state.activeChartId;
      message.revision = state.activeRevision;
    }
    post(message);
  }

  function moveSelection(delta, mode) {
    if (!state.view) return false;
    var rows = [];
    try {
      rows = state.view.data('table') || [];
    } catch (error) {
      return false;
    }
    if (!rows.length) return false;
    rows = rows.slice().sort(function (a, b) {
      return a.sourceRowIndex - b.sourceRowIndex;
    });
    var current = -1;
    for (var i = 0; i < rows.length; i++) {
      if (rows[i] && rows[i].sourceRowIndex === state.selectedRow) {
        current = i;
        break;
      }
    }
    var next = current < 0
      ? (delta > 0 ? 0 : rows.length - 1)
      : Math.max(0, Math.min(rows.length - 1, current + delta));
    postSelection(state.activeChartId, state.activeRevision, rows[next], mode, false);
    return true;
  }

  function viewClientPoint(event) {
    var rect = viewElement.getBoundingClientRect();
    var svg = document.querySelector('#view svg');
    var ctm = svg && svg.getScreenCTM ? svg.getScreenCTM() : null;
    if (svg && ctm) {
      try {
        var point = svg.createSVGPoint();
        point.x = event.clientX;
        point.y = event.clientY;
        var local = point.matrixTransform(ctm.inverse());
        var origin = typeof state.view.origin === 'function' ? state.view.origin() : [0, 0];
        var padding = typeof state.view.padding === 'function' ? state.view.padding() : null;
        var padLeft = padding && typeof padding.left === 'number' ? padding.left : 0;
        var padTop = padding && typeof padding.top === 'number' ? padding.top : 0;
        return {
          clientX: event.clientX,
          clientY: event.clientY,
          x: local.x - origin[0] - padLeft,
          y: local.y - origin[1] - padTop
        };
      } catch (error) {
        // Fall through to the box approximation if the SVG matrix is unavailable.
      }
    }
    var viewWidth = 1;
    var viewHeight = 1;
    try {
      viewWidth = state.view.width() || rect.width || 1;
      viewHeight = state.view.height() || rect.height || 1;
    } catch (error) {
      viewWidth = rect.width || 1;
      viewHeight = rect.height || 1;
    }
    var fallbackPadding = typeof state.view.padding === 'function' ? state.view.padding() : null;
    var fallbackLeft = fallbackPadding && typeof fallbackPadding.left === 'number' ? fallbackPadding.left : 0;
    var fallbackTop = fallbackPadding && typeof fallbackPadding.top === 'number' ? fallbackPadding.top : 0;
    return {
      clientX: event.clientX,
      clientY: event.clientY,
      x: (event.clientX - rect.left) * (viewWidth / (rect.width || 1)) - fallbackLeft,
      y: (event.clientY - rect.top) * (viewHeight / (rect.height || 1)) - fallbackTop
    };
  }

  function itemBounds(item) {
    var bounds = item && item.bounds;
    if (!bounds) return null;
    if (typeof bounds.x1 === 'number') return [bounds.x1, bounds.y1, bounds.x2, bounds.y2];
    if (bounds.length >= 4) return [bounds[0], bounds[1], bounds[2], bounds[3]];
    return null;
  }

  function itemIntersects(item, left, top, right, bottom) {
    if (!item) return false;
    var bounds = itemBounds(item);
    if (bounds) {
      return bounds[2] >= left && bounds[0] <= right && bounds[3] >= top && bounds[1] <= bottom;
    }
    if (typeof item.x === 'number' && typeof item.y === 'number') {
      var radius = 10;
      if (typeof item.size === 'number' && item.size > 0) radius = Math.max(6, Math.sqrt(item.size) / 2 + 2);
      var width = typeof item.width === 'number' ? item.width : radius * 2;
      var height = typeof item.height === 'number' ? item.height : radius * 2;
      return item.x + width >= left && item.x <= right && item.y + height >= top && item.y <= bottom;
    }
    return false;
  }

  function pointHitsMark(item, x, y) {
    var bounds = itemBounds(item);
    if (bounds) return x >= bounds[0] && x <= bounds[2] && y >= bounds[1] && y <= bounds[3];
    if (typeof item.x === 'number' && typeof item.y === 'number') {
      var radius = 10;
      if (typeof item.size === 'number' && item.size > 0) radius = Math.max(6, Math.sqrt(item.size) / 2 + 2);
      var dx = x - item.x;
      var dy = y - item.y;
      return dx * dx + dy * dy <= radius * radius;
    }
    return false;
  }

  function findHitAt(x, y) {
    var found = null;
    if (!state.view) return null;
    function walk(items) {
      (items || []).forEach(function (item) {
        if (item.mark && (item.mark.name === 'cloudyBar' || item.mark.name === 'cloudyHit') &&
            item.datum && typeof item.datum.sourceRowIndex === 'number' &&
            pointHitsMark(item, x, y)) {
          found = item.datum;
        }
        if (item.items) walk(item.items);
      });
    }
    try {
      walk(state.view.scenegraph().root.items);
    } catch (error) {
      return null;
    }
    return found;
  }

  function collectLassoRows(left, top, right, bottom) {
    var rows = {};
    if (!state.view) return [];
    function walk(items) {
      (items || []).forEach(function (item) {
        if (item.mark && (item.mark.name === 'cloudyBar' || item.mark.name === 'cloudyHit') &&
            item.datum && typeof item.datum.sourceRowIndex === 'number' &&
            itemIntersects(item, left, top, right, bottom)) {
          rows[item.datum.sourceRowIndex] = true;
        }
        if (item.items) walk(item.items);
      });
    }
    try {
      walk(state.view.scenegraph().root.items);
    } catch (error) {
      return [];
    }
    return Object.keys(rows).map(Number).sort(function (a, b) { return a - b; });
  }

  function lassoOverlay() {
    return document.getElementById('lasso');
  }

  function updateLassoOverlay(start, current) {
    var overlay = lassoOverlay();
    if (!overlay) return;
    var viewRect = viewElement.getBoundingClientRect();
    var left = Math.min(start.clientX, current.clientX) - viewRect.left;
    var top = Math.min(start.clientY, current.clientY) - viewRect.top;
    var width = Math.abs(current.clientX - start.clientX);
    var height = Math.abs(current.clientY - start.clientY);
    overlay.style.left = left + 'px';
    overlay.style.top = top + 'px';
    overlay.style.width = width + 'px';
    overlay.style.height = height + 'px';
    overlay.style.borderColor = state.exportDark ? '#9FB3B8' : '#35515A';
    overlay.style.background = state.exportDark ? 'rgba(159,179,184,0.14)' : 'rgba(53,81,90,0.12)';
    overlay.hidden = false;
  }

  function hideLassoOverlay() {
    var overlay = lassoOverlay();
    if (overlay) overlay.hidden = true;
  }

  var viewElement = document.getElementById('view');
  if (viewElement) {
    viewElement.addEventListener('click', function (event) {
      if (Date.now() < state.suppressClickUntil) {
        state.suppressClickUntil = 0;
        event.preventDefault();
        event.stopPropagation();
        return;
      }
      state.pointerMode = selectionMode(event);
    }, true);

    viewElement.addEventListener('pointerdown', function (event) {
      if (event.button !== undefined && event.button !== 0) return;
      // A new physical press starts a new gesture; only the synthetic click
      // produced by the just-finished lasso is suppressed.
      state.suppressClickUntil = 0;
      var point = viewClientPoint(event);
      state.lasso = {
        pointerId: event.pointerId,
        startClientX: event.clientX,
        startClientY: event.clientY,
        startX: point.x,
        startY: point.y,
        additive: event.ctrlKey || event.metaKey,
        moved: false
      };
    }, true);

    viewElement.addEventListener('pointermove', function (event) {
      var lasso = state.lasso;
      if (!lasso || event.pointerId !== lasso.pointerId) return;
      if (!lasso.moved) {
        var dx = event.clientX - lasso.startClientX;
        var dy = event.clientY - lasso.startClientY;
        if (Math.sqrt(dx * dx + dy * dy) < 8) return;
        lasso.moved = true;
        try { viewElement.setPointerCapture(lasso.pointerId); } catch (error) { /* mouse fallback */ }
      }
      event.preventDefault();
      updateLassoOverlay(lasso, event);
    }, { capture: true, passive: false });

    viewElement.addEventListener('pointerup', function (event) {
      var lasso = state.lasso;
      if (!lasso || event.pointerId !== lasso.pointerId) return;
      state.lasso = null;
      var point = viewClientPoint(event);
      if (!lasso.moved) {
        var datum = findHitAt(point.x, point.y);
        if (datum) {
          state.suppressClickUntil = Date.now() + 250;
          postSelection(state.activeChartId, state.activeRevision, datum, selectionMode(event), true);
        }
        return;
      }
      hideLassoOverlay();
      try {
        if (viewElement.hasPointerCapture(event.pointerId)) {
          viewElement.releasePointerCapture(event.pointerId);
        }
      } catch (error) { /* ignore */ }
      var left = Math.min(lasso.startX, point.x);
      var top = Math.min(lasso.startY, point.y);
      var right = Math.max(lasso.startX, point.x);
      var bottom = Math.max(lasso.startY, point.y);
      var rows = collectLassoRows(left, top, right, bottom);
      // A completed non-additive rectangle is an explicit selection command:
      // no intersecting marks clears the set. Additive empty rectangles no-op.
      state.suppressClickUntil = Date.now() + 450;
      post({
        type: 'LassoSelectionIntent',
        chartId: state.activeChartId,
        revision: state.activeRevision,
        rows: rows,
        additive: lasso.additive === true,
        pointer: true
      });
    }, true);

    viewElement.addEventListener('pointercancel', function (event) {
      if (!state.lasso || event.pointerId !== state.lasso.pointerId) return;
      state.lasso = null;
      hideLassoOverlay();
    }, true);
  }

  window.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' || event.key === 'Tab') {
      event.preventDefault();
      postFocusExit();
      return;
    }
    var keyMode = event.shiftKey ? 'range' : ((event.ctrlKey || event.metaKey) ? 'toggle' : 'replace');
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      if (moveSelection(1, keyMode)) event.preventDefault();
      return;
    }
    if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      if (moveSelection(-1, keyMode)) event.preventDefault();
    }
  }, true);

  function ready() {
    if (state.ready) return;
    if (typeof vega === 'undefined' || typeof vega.View !== 'function') {
      fail('vega_missing', 'Vega did not load from the packaged assets');
      return;
    }
    state.ready = true;
    post({
      type: 'Ready',
      protocol: 1,
      vegaVersion: vega.version,
      interpreter: vega.expressionInterpreter !== undefined && vega.expressionInterpreter !== null
    });
  }

  function countMarks(items, out) {
    (items || []).forEach(function (item) {
      if (!item) return;
      var type = item.marktype || (item.items ? 'group' : 'unknown');
      out[type] = (out[type] || 0) + 1;
      if (item.items) countMarks(item.items, out);
    });
    return out;
  }

  function postExportError(detail, chartId, revision) {
    post({
      type: 'ExportError',
      chartId: typeof chartId === 'string' ? chartId : state.activeChartId,
      revision: typeof revision === 'number' ? revision : state.activeRevision,
      detail: String(detail || 'Export failed')
    });
  }

  function fitExportText(node, text, maxWidth, fallbackCharWidth) {
    node.textContent = text;
    var measured = typeof node.getComputedTextLength === 'function'
      ? node.getComputedTextLength()
      : 0;
    if (measured > 0 && measured <= maxWidth) return;
    if (measured > 0) {
      var low = 0;
      var high = text.length;
      while (low < high) {
        var mid = Math.ceil((low + high) / 2);
        node.textContent = text.slice(0, mid) + '\u2026';
        if (node.getComputedTextLength() <= maxWidth) low = mid;
        else high = mid - 1;
      }
      node.textContent = text.slice(0, low) + '\u2026';
      return;
    }
    var chars = Math.max(1, Math.floor(maxWidth / fallbackCharWidth));
    node.textContent = text.length > chars ? text.slice(0, chars - 1) + '\u2026' : text;
  }

  function wrapExportCaption(text, maxChars) {
    var remaining = String(text || '').trim();
    if (!remaining) return [];
    if (remaining.length <= maxChars) return [remaining];
    var cut = remaining.lastIndexOf(' ', maxChars);
    if (cut < Math.floor(maxChars * 0.4)) cut = maxChars;
    var first = remaining.slice(0, cut).trim();
    var second = remaining.slice(cut).trim();
    if (remaining.length > maxChars * 2) {
      var mid = remaining.lastIndexOf(' ', Math.floor(remaining.length / 2));
      if (mid > 0) {
        first = remaining.slice(0, mid).trim();
        second = remaining.slice(mid).trim();
      }
    }
    return [first, second].filter(function (line) { return line.length > 0; }).slice(0, 2);
  }

  function cleanExportInteractivity(node) {
    node.querySelectorAll('.cloudy-legend-hit').forEach(function (hit) {
      hit.parentNode.removeChild(hit);
    });
    return node;
  }

  function currentSvgMarkup() {
    if (!state.view) return null;
    var svg = document.querySelector('#view svg');
    if (!svg) return null;
    var width = Number(svg.getAttribute('width'));
    var height = Number(svg.getAttribute('height'));
    var viewBox = svg.getAttribute('viewBox');
    if ((!isFinite(width) || width <= 0 || !isFinite(height) || height <= 0) && viewBox) {
      var parts = viewBox.trim().split(/\s+/).map(Number);
      if (parts.length === 4) {
        width = parts[2];
        height = parts[3];
      }
    }
    var title = String(state.exportTitle || '').trim();
    var subtitle = String(state.exportSubtitle || '').trim();
    var caption = String(state.exportCaption || '').trim();
    var source = String(state.exportSource || '').trim();
    if (!isFinite(width) || width <= 0 || !isFinite(height) || height <= 0) {
      var raw = cleanExportInteractivity(svg.cloneNode(true));
      raw.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
      raw.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
      return new XMLSerializer().serializeToString(raw);
    }
    var hasHeader = title || subtitle || caption || source;
    var header = hasHeader ? 8 : 0;
    var cursor = header;
    var nodes = [];
    if (title) {
      nodes.push({ key: 'title', y: cursor + 18, size: 16, weight: '600', color: state.exportDark ? '#E1EBED' : '#182427', text: title, min: 9 });
      cursor += 24;
    }
    if (subtitle) {
      nodes.push({ key: 'subtitle', y: cursor + 12, size: 11, weight: '400', color: state.exportDark ? '#A9BDC2' : '#4C6066', text: subtitle, min: 6 });
      cursor += 16;
    }
    if (caption) {
      var captionChars = Math.max(20, Math.floor((width - 32) / 6.2));
      wrapExportCaption(caption, captionChars).forEach(function (line) {
        nodes.push({ key: 'caption', y: cursor + 12, size: 10, weight: '400', color: state.exportDark ? '#A9BDC2' : '#4C6066', text: line, min: 6 });
        cursor += 16;
      });
    }
    if (source) {
      nodes.push({ key: 'source', y: cursor + 12, size: 11, weight: '400', color: state.exportDark ? '#A9BDC2' : '#4C6066', text: source, min: 6 });
      cursor += 16;
    }
    if (hasHeader) header = cursor + 8;
    var ns = 'http://www.w3.org/2000/svg';
    var outer = document.createElementNS(ns, 'svg');
    outer.setAttribute('xmlns', ns);
    outer.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
    outer.setAttribute('width', String(width));
    outer.setAttribute('height', String(height + header));
    outer.setAttribute('viewBox', '0 0 ' + width + ' ' + (height + header));
    var background = document.createElementNS(ns, 'rect');
    background.setAttribute('x', '0');
    background.setAttribute('y', '0');
    background.setAttribute('width', String(width));
    background.setAttribute('height', String(height + header));
    background.setAttribute('fill', state.transparentExportBackground
      ? 'none'
      : (state.exportDark ? '#182427' : '#FFFFFF'));
    outer.appendChild(background);
    if (state.chartBorder) {
      var border = document.createElementNS(ns, 'rect');
      border.setAttribute('x', '0.5');
      border.setAttribute('y', '0.5');
      border.setAttribute('width', String(Math.max(0, width - 1)));
      border.setAttribute('height', String(Math.max(0, height + header - 1)));
      border.setAttribute('fill', 'none');
      border.setAttribute('stroke', state.exportDark ? '#35515A' : '#D3DFE2');
      border.setAttribute('stroke-width', '1');
      outer.appendChild(border);
    }
    var textNodes = [];
    nodes.forEach(function (item) {
      var node = document.createElementNS(ns, 'text');
      node.setAttribute('x', '16');
      node.setAttribute('y', String(item.y));
      node.setAttribute('font-family', 'system-ui, sans-serif');
      node.setAttribute('font-size', String(item.size));
      node.setAttribute('font-weight', item.weight);
      node.setAttribute('fill', item.color);
      outer.appendChild(node);
      textNodes.push({ node: node, item: item });
    });
    if (textNodes.length) {
      var measureHost = document.createElement('div');
      measureHost.style.position = 'fixed';
      measureHost.style.left = '-100000px';
      measureHost.style.top = '0';
      measureHost.style.visibility = 'hidden';
      measureHost.style.pointerEvents = 'none';
      measureHost.appendChild(outer);
      document.body.appendChild(measureHost);
      try {
        textNodes.forEach(function (entry) {
          fitExportText(entry.node, entry.item.text, Math.max(1, width - 32), entry.item.min);
        });
      } finally {
        measureHost.removeChild(outer);
        measureHost.remove();
      }
    }
    var nested = cleanExportInteractivity(svg.cloneNode(true));
    nested.setAttribute('x', '0');
    nested.setAttribute('y', String(header));
    nested.setAttribute('width', String(width));
    nested.setAttribute('height', String(height));
    if (!nested.getAttribute('viewBox')) nested.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
    outer.appendChild(nested);
    return new XMLSerializer().serializeToString(outer);
  }

  function applySelection(message) {
    if (!state.ready || !state.view) return;
    if (!message || message.chartId !== state.activeChartId || message.revision !== state.activeRevision) return;
    var rows = [];
    if (Array.isArray(message.rows)) {
      message.rows.forEach(function (row) {
        if (typeof row === 'number' && row >= 0 && isFinite(row) && rows.indexOf(row) < 0) {
          rows.push(row);
        }
      });
    }
    var table = [];
    try {
      table = state.view.data('table') || [];
    } catch (error) {
      table = [];
    }
    var selected = table.filter(function (datum) {
      return datum && rows.indexOf(datum.sourceRowIndex) >= 0;
    });
    try {
      var changeset = vega.changeset()
        .remove(function () { return true; })
        .insert(selected);
      state.view.change('selected', changeset).runAsync().catch(function (error) {
        console.log('CloudySelectionError ' + (error && error.message ? error.message : error));
      });
    } catch (error) {
      console.log('CloudySelectionError ' + (error && error.message ? error.message : error));
    }
  }

  function exportSvg() {
    var text = currentSvgMarkup();
    if (text === null) {
      postExportError('The chart SVG is not available');
      return;
    }
    post({
      type: 'SvgExport',
      chartId: state.activeChartId,
      revision: state.activeRevision,
      svg: text
    });
  }

  function rasterizeSvg(text, chartId, revision, onSuccess) {
    try {
      var encoded = btoa(unescape(encodeURIComponent(text)));
      var image = new Image();
      image.onload = function () {
        try {
          var width = image.naturalWidth || image.width;
          var height = image.naturalHeight || image.height;
          if (!width || !height || width > 4096 || height > 4096) {
            postExportError('The PNG dimensions are outside the allowed range', chartId, revision);
            return;
          }
          var canvas = document.createElement('canvas');
          canvas.width = width;
          canvas.height = height;
          var context = canvas.getContext('2d');
          if (!context) {
            postExportError('A PNG canvas is unavailable', chartId, revision);
            return;
          }
          context.drawImage(image, 0, 0, width, height);
          var dataUrl = canvas.toDataURL('image/png');
          var comma = dataUrl.indexOf(',');
          if (comma < 0) {
            postExportError('PNG encoding failed', chartId, revision);
            return;
          }
          onSuccess(width, height, dataUrl.substring(comma + 1));
        } catch (error) {
          postExportError('PNG rendering failed: ' + (error && error.message ? error.message : error), chartId, revision);
        }
      };
      image.onerror = function () {
        postExportError('The SVG could not be rasterized', chartId, revision);
      };
      image.src = 'data:image/svg+xml;base64,' + encoded;
    } catch (error) {
      postExportError('PNG encoding failed: ' + (error && error.message ? error.message : error), chartId, revision);
    }
  }

  function exportPng(purpose) {
    // Snapshot the revision before asynchronous rasterization; a later spec must
    // not relabel this image as current.
    var chartId = state.activeChartId;
    var revision = state.activeRevision;
    var text = currentSvgMarkup();
    if (text === null) {
      postExportError('The chart SVG is not available for PNG rendering', chartId, revision);
      return;
    }
    rasterizeSvg(text, chartId, revision, function (width, height, pngBase64) {
      post({
        type: 'PngExport',
        chartId: chartId,
        revision: revision,
        purpose: purpose === 'pdf' ? 'pdf' : 'png',
        width: width,
        height: height,
        pngBase64: pngBase64
      });
    });
  }

  function exportBundle() {
    var chartId = state.activeChartId;
    var revision = state.activeRevision;
    var text = currentSvgMarkup();
    if (text === null) {
      postExportError('The chart SVG is not available for the export bundle', chartId, revision);
      return;
    }
    rasterizeSvg(text, chartId, revision, function (width, height, pngBase64) {
      post({
        type: 'ExportBundle',
        chartId: chartId,
        revision: revision,
        width: width,
        height: height,
        svg: text,
        pngBase64: pngBase64
      });
    });
  }

  function disposeView() {
    hideTooltip();
    if (state.view) {
      try {
        state.view.finalize();
      } catch (error) {
        // Disposal is best effort; a dead renderer cannot be repaired here.
      }
      state.view = null;
      state.lasso = null;
      hideLassoOverlay();
    }
  }

  function applyChartSpec(message) {
    if (!state.ready) {
      fail('not_ready', 'ApplyChartSpec arrived before Ready');
      return;
    }
    if (!message || typeof message !== 'object' || typeof message.chartId !== 'string' ||
        typeof message.revision !== 'number' || typeof message.spec !== 'object' ||
        message.spec === null) {
      fail('invalid_message', 'ApplyChartSpec requires chartId, revision and a spec object');
      return;
    }
    try {
      disposeView();
      var chartId = message.chartId;
      var revision = message.revision;
      state.activeChartId = chartId;
      state.activeRevision = revision;
      state.selectedRow = -1;
      state.tooltipEnabled = !(message.spec.usermeta && message.spec.usermeta.tooltip === false);
      state.units = message.spec.usermeta && typeof message.spec.usermeta.units === 'string'
        ? message.spec.usermeta.units
        : '';
      state.chartBorder = Boolean(message.spec.usermeta && message.spec.usermeta.chartBorder);
      state.transparentExportBackground = Boolean(
        message.spec.usermeta && message.spec.usermeta.transparentExportBackground
      );
      state.hiddenSeries = message.spec.usermeta && Array.isArray(message.spec.usermeta.hiddenSeries)
        ? message.spec.usermeta.hiddenSeries.map(String)
        : [];
      state.series = message.spec.usermeta && Array.isArray(message.spec.usermeta.series)
        ? message.spec.usermeta.series.map(String)
        : [];
      state.exportTitle = typeof message.title === 'string' ? message.title : '';
      state.exportSubtitle = typeof message.subtitle === 'string' ? message.subtitle : '';
      state.exportCaption = typeof message.caption === 'string' ? message.caption : '';
      state.exportSource = typeof message.sourceNote === 'string' ? message.sourceNote : '';
      state.exportDark = message.dark === true;
      hideTooltip();
      var runtime = vega.parse(message.spec, null, { ast: true });
      var view = new vega.View(runtime, {
        expr: vega.expressionInterpreter,
        renderer: 'svg',
        container: '#view',
        hover: false
      });
      state.view = view;
      view.addSignalListener('cloudySelection', function (name, value) {
        if (Array.isArray(value) && typeof value[0] === 'number' && value[0] >= 0) {
          var mode = state.pointerMode;
          state.pointerMode = 'replace';
          postSelection(chartId, revision, {
            sourceRowIndex: value[0],
            series: value[1],
            x: value[2],
            exactY: value[3],
            y: value[4]
          }, mode, true);
        }
      });
      view.addSignalListener('cloudyHover', function (name, value) {
        if (Array.isArray(value) && typeof value[0] === 'number' && value[0] >= 0) {
          showTooltip({
            sourceRowIndex: value[0],
            series: value[1],
            x: value[2],
            exactY: value[3],
            y: value[4]
          });
        } else {
          hideTooltip();
        }
      });
      view.runAsync().then(function () {
        setupLegendToggle(chartId, revision);
        var rows = [];
        try {
          rows = view.data('table') || [];
        } catch (error) {
          rows = [];
        }
        var sceneMarks = {};
        countMarks(view.scenegraph().root.items, sceneMarks);
        var svgPaths = document.querySelectorAll('#view svg path').length;
        var svgNodes = document.querySelectorAll('#view svg *').length;
        var svgTexts = [];
        document.querySelectorAll('#view svg text').forEach(function (node) {
          if (svgTexts.length < 200) svgTexts.push(node.textContent || '');
        });
        var surfaceWidth = document.documentElement.clientWidth || window.innerWidth || 0;
        var surfaceHeight = document.documentElement.clientHeight || window.innerHeight || 0;
        var viewWidth = 0;
        var viewHeight = 0;
        try {
          viewWidth = view.width();
          viewHeight = view.height();
        } catch (error) {
          viewWidth = 0;
          viewHeight = 0;
        }
        post({
          type: 'RenderComplete',
          chartId: chartId,
          revision: revision,
          markCount: rows.length,
          sceneMarks: sceneMarks,
          svgPaths: svgPaths,
          svgNodes: svgNodes,
          svgTexts: svgTexts,
          vegaVersion: vega.version,
          surfaceWidth: surfaceWidth,
          surfaceHeight: surfaceHeight,
          viewWidth: viewWidth,
          viewHeight: viewHeight
        });
      }).catch(function (error) {
        fail('render_failed', error && error.message ? error.message : error, chartId, revision);
      });
    } catch (error) {
      fail('spec_rejected', error && error.message ? error.message : error, chartId, revision);
    }
  }

  window.cloudyReceive = function (raw) {
    var message;
    try {
      message = typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch (error) {
      fail('invalid_json', 'Renderer message was not valid JSON');
      return;
    }
    if (!message || typeof message.type !== 'string') {
      fail('invalid_message', 'Renderer message has no type');
      return;
    }
    switch (message.type) {
      case 'Initialize':
        ready();
        break;
      case 'ApplyChartSpec':
        applyChartSpec(message);
        break;
      case 'ExportSvg':
        exportSvg();
        break;
      case 'ExportPng':
        exportPng(message.purpose);
        break;
      case 'ExportBundle':
        exportBundle();
        break;
      case 'ApplySelection':
        applySelection(message);
        break;
      case 'Dispose':
        disposeView();
        break;
      default:
        fail('unsupported_message', 'Unsupported renderer message: ' + message.type);
    }
  };

  window.addEventListener('error', function (event) {
    fail('window_error', event.message || 'uncaught renderer error');
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', ready);
  } else {
    ready();
  }
})();
