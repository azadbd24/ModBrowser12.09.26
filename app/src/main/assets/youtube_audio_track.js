(function() {
  // Best-effort: drives YouTube's own settings menu in the DOM to pick the
  // audio track labeled "original" (i.e. undo YouTube's auto-dub default).
  // This relies on YouTube's current player markup/class names and may need
  // updating if YouTube changes them - it fails silently if it can't find them.

  function clickMenuItemContaining(text) {
    var items = document.querySelectorAll('.ytp-menuitem');
    for (var i = 0; i < items.length; i++) {
      var label = items[i].textContent || '';
      if (label.toLowerCase().indexOf(text) !== -1) {
        items[i].click();
        return true;
      }
    }
    return false;
  }

  function trySelectOriginalTrack(attemptsLeft) {
    if (attemptsLeft <= 0) return;
    var settingsBtn = document.querySelector('.ytp-settings-button');
    if (!settingsBtn) {
      setTimeout(function() { trySelectOriginalTrack(attemptsLeft - 1); }, 1000);
      return;
    }

    settingsBtn.click(); // open settings panel
    setTimeout(function() {
      var openedAudioMenu = clickMenuItemContaining('audio track');
      if (!openedAudioMenu) {
        settingsBtn.click(); // close panel, nothing to do on this video
        return;
      }
      setTimeout(function() {
        var picked = clickMenuItemContaining('original');
        if (!picked) {
          // No explicit "original" entry (single-track video) - just close the menu.
          settingsBtn.click();
        }
      }, 400);
    }, 400);
  }

  var poll = setInterval(function() {
    if (document.querySelector('.ytp-settings-button')) {
      clearInterval(poll);
      trySelectOriginalTrack(3);
    }
  }, 500);

  // Stop polling after ~15s regardless, so it never runs forever on non-video pages.
  setTimeout(function() { clearInterval(poll); }, 15000);
})();
