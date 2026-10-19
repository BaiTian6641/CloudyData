(function () {
  'use strict';

  var state = {
    view: null,
    ready: false,
    activeChartId: null,
    activeRevision: null,
    selectedRow: -1,
    tooltipEnabled: true,
    exportTitle: '',
    exportSource: '',
    exportDark: false
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
    tooltip.textContent = series + String(datum.x || '') + ' = ' + exact;
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

  function postSelection(chartId, revision, datum) {
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
      y: typeof datum.y === 'number' && isFinite(datum.y) ? datum.y : null
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

  function moveSelection(delta) {
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
    postSelection(state.activeChartId, state.activeRevision, rows[next]);
    return true;
  }

  window.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' || event.key === 'Tab') {
      event.preventDefault();
      postFocusExit();
      return;
    }
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      if (moveSelection(1)) event.preventDefault();
      return;
    }
    if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      if (moveSelection(-1)) event.preventDefault();
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
    var source = String(state.exportSource || '').trim();
    if (!isFinite(width) || width <= 0 || !isFinite(height) || height <= 0) {
      var raw = svg.cloneNode(true);
      raw.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
      raw.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
      return new XMLSerializer().serializeToString(raw);
    }
    var header = (title || source) ? 56 : 0;
    var ns = 'http://www.w3.org/2000/svg';
    var outer = document.createElementNS(ns, 'svg');
    outer.setAttribute('xmlns', ns);
    outer.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
    outer.setAttribute('width', String(width));
    outer.setAttribute('height', String(height + header));
    outer.setAttribute('viewBox', '0 0 ' + width + ' ' + (height + header));
    var titleNode = null;
    var sourceNode = null;
    var background = document.createElementNS(ns, 'rect');
    background.setAttribute('x', '0');
    background.setAttribute('y', '0');
    background.setAttribute('width', String(width));
    background.setAttribute('height', String(height + header));
    background.setAttribute('fill', state.exportDark ? '#182427' : '#FFFFFF');
    outer.appendChild(background);
    if (title) {
      titleNode = document.createElementNS(ns, 'text');
      titleNode.setAttribute('x', '16');
      titleNode.setAttribute('y', '28');
      titleNode.setAttribute('font-family', 'system-ui, sans-serif');
      titleNode.setAttribute('font-size', '16');
      titleNode.setAttribute('font-weight', '600');
      titleNode.setAttribute('fill', state.exportDark ? '#E1EBED' : '#182427');
      outer.appendChild(titleNode);
    }
    if (source) {
      sourceNode = document.createElementNS(ns, 'text');
      sourceNode.setAttribute('x', '16');
      sourceNode.setAttribute('y', '46');
      sourceNode.setAttribute('font-family', 'system-ui, sans-serif');
      sourceNode.setAttribute('font-size', '11');
      sourceNode.setAttribute('fill', state.exportDark ? '#A9BDC2' : '#4C6066');
      outer.appendChild(sourceNode);
    }
    if (titleNode || sourceNode) {
      var measureHost = document.createElement('div');
      measureHost.style.position = 'fixed';
      measureHost.style.left = '-100000px';
      measureHost.style.top = '0';
      measureHost.style.visibility = 'hidden';
      measureHost.style.pointerEvents = 'none';
      measureHost.appendChild(outer);
      document.body.appendChild(measureHost);
      try {
        if (titleNode) fitExportText(titleNode, title, Math.max(1, width - 32), 9);
        if (sourceNode) fitExportText(sourceNode, source, Math.max(1, width - 32), 6);
      } finally {
        measureHost.removeChild(outer);
        measureHost.remove();
      }
    }
    var nested = svg.cloneNode(true);
    nested.setAttribute('x', '0');
    nested.setAttribute('y', String(header));
    nested.setAttribute('width', String(width));
    nested.setAttribute('height', String(height));
    if (!nested.getAttribute('viewBox')) nested.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
    outer.appendChild(nested);
    return new XMLSerializer().serializeToString(outer);
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
          post({
            type: 'PngExport',
            chartId: chartId,
            revision: revision,
            purpose: purpose === 'pdf' ? 'pdf' : 'png',
            width: width,
            height: height,
            pngBase64: dataUrl.substring(comma + 1)
          });
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

  function disposeView() {
    hideTooltip();
    if (state.view) {
      try {
        state.view.finalize();
      } catch (error) {
        // Disposal is best effort; a dead renderer cannot be repaired here.
      }
      state.view = null;
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
      state.exportTitle = typeof message.title === 'string' ? message.title : '';
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
          postSelection(chartId, revision, {
            sourceRowIndex: value[0],
            series: value[1],
            x: value[2],
            exactY: value[3],
            y: value[4]
          });
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
