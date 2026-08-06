(function() {
    if (window.__scraperBridgeLoaded) return;
    window.__scraperBridgeLoaded = true;

    window.ScraperBridge = {
        // Extract all text content cleaned
        getText: function() {
            return document.body ? document.body.innerText : "";
        },

        // Extract page title & meta descriptions
        getMetaData: function() {
            var meta = {};
            meta.title = document.title || "";
            meta.url = window.location.href;
            var metas = document.getElementsByTagName('meta');
            for (var i = 0; i < metas.length; i++) {
                var name = metas[i].getAttribute('name') || metas[i].getAttribute('property');
                if (name) {
                    meta[name] = metas[i].getAttribute('content') || "";
                }
            }
            return JSON.stringify(meta, null, 2);
        },

        // Extract HTML source code
        getHTML: function() {
            return document.documentElement.outerHTML;
        },

        // Extract all links (href, text, title)
        getAllLinks: function() {
            var links = [];
            var anchors = document.querySelectorAll('a[href]');
            anchors.forEach(function(a) {
                links.push({
                    text: a.innerText.trim(),
                    href: a.href,
                    title: a.getAttribute('title') || ""
                });
            });
            return JSON.stringify(links, null, 2);
        },

        // Extract all media elements (img, video, audio)
        getAllMedia: function() {
            var media = { images: [], videos: [], audios: [] };
            document.querySelectorAll('img').forEach(function(img) {
                if (img.src) media.images.push({ src: img.src, alt: img.alt || "" });
            });
            document.querySelectorAll('video').forEach(function(v) {
                if (v.src) media.videos.push(v.src);
                v.querySelectorAll('source').forEach(function(s) { if (s.src) media.videos.push(s.src); });
            });
            document.querySelectorAll('audio').forEach(function(a) {
                if (a.src) media.audios.push(a.src);
                a.querySelectorAll('source').forEach(function(s) { if (s.src) media.audios.push(s.src); });
            });
            return JSON.stringify(media, null, 2);
        },

        // Extract tables to JSON format
        getTables: function() {
            var tablesData = [];
            var tables = document.querySelectorAll('table');
            tables.forEach(function(table, tIdx) {
                var rows = [];
                table.querySelectorAll('tr').forEach(function(tr) {
                    var cells = [];
                    tr.querySelectorAll('th, td').forEach(function(td) {
                        cells.push(td.innerText.trim());
                    });
                    if (cells.length > 0) rows.push(cells);
                });
                tablesData.push({ tableIndex: tIdx + 1, rows: rows });
            });
            return JSON.stringify(tablesData, null, 2);
        },

        // Extract localStorage, sessionStorage, and document.cookie
        getStorageAndCookies: function() {
            var data = {
                cookie: document.cookie || "",
                localStorage: {},
                sessionStorage: {}
            };
            try {
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    data.localStorage[k] = localStorage.getItem(k);
                }
            } catch(e){}
            try {
                for (var j = 0; j < sessionStorage.length; j++) {
                    var sk = sessionStorage.key(j);
                    data.sessionStorage[sk] = sessionStorage.getItem(sk);
                }
            } catch(e){}
            return JSON.stringify(data, null, 2);
        },

        // Custom CSS Selector Query Scraper
        querySelectorAllData: function(selector) {
            try {
                var els = document.querySelectorAll(selector);
                var res = [];
                els.forEach(function(el) {
                    res.push({
                        tagName: el.tagName.toLowerCase(),
                        text: el.innerText ? el.innerText.trim() : "",
                        html: el.outerHTML,
                        attributes: Array.from(el.attributes).reduce(function(acc, attr) {
                            acc[attr.name] = attr.value;
                            return acc;
                        }, {})
                    });
                });
                return JSON.stringify(res, null, 2);
            } catch(e) {
                return JSON.stringify({ error: e.message });
            }
        }
    };
})();
