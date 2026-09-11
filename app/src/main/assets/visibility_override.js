(function() {
  try {
    Object.defineProperty(document, 'hidden', {
      get: function() { return false; },
      configurable: true
    });
    Object.defineProperty(document, 'visibilityState', {
      get: function() { return 'visible'; },
      configurable: true
    });
    var block = function(e) { e.stopImmediatePropagation(); };
    document.addEventListener('visibilitychange', block, true);
    window.addEventListener('blur', block, true);
    window.addEventListener('pagehide', block, true);
  } catch (e) {
    // Some pages define these as non-configurable; fail silently rather than break the page.
  }
})();
