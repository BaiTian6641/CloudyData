(function () {
  'use strict';

  var state = {
    view: null,
    ready: false
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
      var runtime = vega.parse(message.spec, null, { ast: true });
      var view = new vega.View(runtime, {
        expr: vega.expressionInterpreter,
        renderer: 'svg',
        container: '#view',
        hover: false
      });
      state.view = view;
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
