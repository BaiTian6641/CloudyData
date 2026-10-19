(function () {
  'use strict';

  var state = {
    view: null,
    ready: false,
    activeChartId: null,
    activeRevision: null,
    selectedRow: -1,
    tooltipEnabled: true
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
