(function() {
  if (document.getElementById('modbrowser-dark-style')) return;
  var style = document.createElement('style');
  style.id = 'modbrowser-dark-style';
  style.innerHTML =
    'html { filter: invert(1) hue-rotate(180deg) !important; background: #fff !important; } ' +
    'img, video, picture, canvas, iframe, svg, [style*="background-image"] { ' +
    '  filter: invert(1) hue-rotate(180deg) !important; ' +
    '}';
  (document.head || document.documentElement).appendChild(style);
})();
