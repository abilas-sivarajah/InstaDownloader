// Läuft in der WebView auf Instagrams Einbettungsseite und liefert nur die nötigen Felder zurück.
(function () {
  function http(u) { return typeof u === 'string' && /^https?:\/\//.test(u) ? u : ''; }

  function item(m) {
    var video = http(m.video_url);
    var image = http(m.display_url);
    if (!image && m.display_resources && m.display_resources.length) {
      image = http(m.display_resources[m.display_resources.length - 1].src);
    }
    if (m.is_video && !video) return null;
    if (!video && !image) return null;
    return { type: video ? 'video' : 'photo', url: video || image, thumb: image };
  }

  function fromContext() {
    var scripts = document.scripts;
    for (var i = 0; i < scripts.length; i++) {
      var text = scripts[i].textContent || '';
      if (text.indexOf('contextJSON') < 0) continue;
      var m = text.match(/"contextJSON":("(?:[^"\\]|\\.)*")/);
      if (!m) continue;
      var ctx = JSON.parse(JSON.parse(m[1])) || {};
      if (ctx.context && ctx.context.copyright_blocked) return { status: 'blocked' };
      var gql = ctx.gql_data || {};
      var media = gql.shortcode_media || gql.xdt_shortcode_media;
      if (!media) return null;
      var nodes = [media];
      var side = media.edge_sidecar_to_children;
      if (side && side.edges && side.edges.length) {
        nodes = side.edges.map(function (e) { return e.node; });
      }
      var items = nodes.map(item).filter(Boolean);
      if (!items.length) return null;
      var cap = media.edge_media_to_caption && media.edge_media_to_caption.edges;
      return {
        status: 'ok',
        owner: (media.owner && media.owner.username) || '',
        caption: (cap && cap[0] && cap[0].node && cap[0].node.text || '').slice(0, 300),
        items: items
      };
    }
    return null;
  }

  // Reserve, falls contextJSON fehlt (z. B. bei reinen Foto-Einbettungen).
  function fromDom() {
    var user = document.querySelector('.UsernameText');
    var owner = user ? user.textContent.trim() : '';
    var img = document.querySelector('.EmbeddedMediaImage');
    var video = document.querySelector('video');
    var src = video ? http(video.currentSrc || video.src) : '';
    if (src) return { status: 'ok', owner: owner, caption: '', items: [{ type: 'video', url: src, thumb: http(video.poster) || (img ? http(img.src) : '') }] };
    // Ein Vorschaubild allein reicht nicht, wenn der Beitrag eigentlich ein Video ist.
    var isVideo = document.querySelector('[data-media-type="GraphVideo"], .EmbedVideo, [class*="PlayButton"]');
    if (img && http(img.src) && !isVideo) return { status: 'ok', owner: owner, caption: '', items: [{ type: 'photo', url: http(img.src), thumb: http(img.src) }] };
    return null;
  }

  try {
    if (/^\/(accounts\/login|challenge)/.test(location.pathname)) return { status: 'login' };
    var res = fromContext();
    if (res) return res;
    if (document.readyState !== 'complete') return { status: 'wait' };
    if (document.querySelector('.EmbedIsBroken, .ebmessage')) return { status: 'unavailable' };
    return fromDom() || { status: 'wait' };
  } catch (e) {
    return { status: 'wait' };
  }
})()
