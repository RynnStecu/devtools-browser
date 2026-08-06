/**
 * Native Chrome DevTools Core for Next-Gen Chrome DevTools Browser
 * Embedded Native DevTools Engine (No Eruda dependence)
 */
(function() {
    if (window.__NATIVE_DEVTOOLS_INSTALLED__) return;
    window.__NATIVE_DEVTOOLS_INSTALLED__ = true;

    const DevTools = {
        activeTab: 'elements',
        networkLogs: [],
        consoleLogs: [],
        DOMSelectedElement: null,

        init() {
            this.setupConsoleInterceptor();
            this.setupNetworkInterceptor();
            this.createUI();
            this.bindEvents();
        },

        setupConsoleInterceptor() {
            const self = this;
            const originalConsole = {
                log: console.log,
                warn: console.warn,
                error: console.error,
                info: console.info,
                debug: console.debug
            };

            ['log', 'warn', 'error', 'info', 'debug'].forEach(method => {
                console[method] = function(...args) {
                    originalConsole[method].apply(console, args);
                    const formatted = args.map(arg => {
                        if (typeof arg === 'object') {
                            try { return JSON.stringify(arg, null, 2); } catch(e) { return String(arg); }
                        }
                        return String(arg);
                    }).join(' ');
                    
                    self.consoleLogs.push({
                        type: method,
                        time: new Date().toLocaleTimeString(),
                        message: formatted
                    });
                    if (self.activeTab === 'console') self.renderConsole();
                };
            });

            window.addEventListener('error', (e) => {
                console.error(`Uncaught Error: ${e.message} at ${e.filename}:${e.lineno}:${e.colno}`);
            });
        },

        setupNetworkInterceptor() {
            const self = this;
            const origOpen = XMLHttpRequest.prototype.open;
            const origSend = XMLHttpRequest.prototype.send;

            XMLHttpRequest.prototype.open = function(method, url) {
                this._method = method;
                this._url = url;
                this._startTime = Date.now();
                return origOpen.apply(this, arguments);
            };

            XMLHttpRequest.prototype.send = function(body) {
                const xhr = this;
                const logEntry = {
                    id: Math.random().toString(36).substr(2, 9),
                    method: xhr._method,
                    url: xhr._url,
                    type: 'xhr',
                    status: 'Pending...',
                    duration: '0ms',
                    reqBody: body,
                    resBody: ''
                };
                self.networkLogs.push(logEntry);

                xhr.addEventListener('load', function() {
                    logEntry.status = xhr.status;
                    logEntry.duration = (Date.now() - xhr._startTime) + 'ms';
                    logEntry.resBody = xhr.responseText;
                    if (self.activeTab === 'network') self.renderNetwork();
                });

                return origSend.apply(this, arguments);
            };

            if (window.fetch) {
                const origFetch = window.fetch;
                window.fetch = async function(...args) {
                    const startTime = Date.now();
                    const url = typeof args[0] === 'string' ? args[0] : args[0].url;
                    const method = (args[1] && args[1].method) || 'GET';
                    
                    const logEntry = {
                        id: Math.random().toString(36).substr(2, 9),
                        method: method,
                        url: url,
                        type: 'fetch',
                        status: 'Pending...',
                        duration: '0ms',
                        reqBody: args[1] ? args[1].body : null,
                        resBody: ''
                    };
                    self.networkLogs.push(logEntry);

                    try {
                        const response = await origFetch.apply(this, args);
                        const clone = response.clone();
                        logEntry.status = response.status;
                        logEntry.duration = (Date.now() - startTime) + 'ms';
                        clone.text().then(text => {
                            logEntry.resBody = text;
                            if (self.activeTab === 'network') self.renderNetwork();
                        }).catch(() => {});
                        return response;
                    } catch(err) {
                        logEntry.status = 'Failed';
                        logEntry.duration = (Date.now() - startTime) + 'ms';
                        if (self.activeTab === 'network') self.renderNetwork();
                        throw err;
                    }
                };
            }
        },

        createUI() {
            if (document.getElementById('native-devtools-root')) return;
            const container = document.createElement('div');
            container.id = 'native-devtools-root';
            container.innerHTML = `
                <style>
                    #native-devtools-root {
                        position: fixed;
                        bottom: 0;
                        left: 0;
                        right: 0;
                        height: 55vh;
                        background: #0d1117;
                        color: #c9d1d9;
                        font-family: 'Consolas', 'Courier New', monospace;
                        font-size: 12px;
                        z-index: 999999;
                        display: flex;
                        flex-direction: column;
                        border-top: 2px solid #00f2fe;
                        box-shadow: 0 -4px 20px rgba(0, 242, 254, 0.2);
                        transition: height 0.3s ease;
                    }
                    .ndt-header {
                        display: flex;
                        background: #161b22;
                        border-bottom: 1px solid #30363d;
                        overflow-x: auto;
                    }
                    .ndt-tab {
                        padding: 8px 14px;
                        cursor: pointer;
                        border-bottom: 2px solid transparent;
                        color: #8b949e;
                        font-weight: bold;
                        white-space: nowrap;
                    }
                    .ndt-tab.active {
                        color: #00f2fe;
                        border-bottom-color: #00f2fe;
                        background: #21262d;
                    }
                    .ndt-body {
                        flex: 1;
                        overflow: auto;
                        padding: 10px;
                    }
                    .ndt-actions {
                        display: flex;
                        margin-left: auto;
                        align-items: center;
                        padding-right: 8px;
                    }
                    .ndt-btn {
                        background: #21262d;
                        border: 1px solid #30363d;
                        color: #c9d1d9;
                        padding: 3px 8px;
                        border-radius: 4px;
                        margin-left: 4px;
                        cursor: pointer;
                    }
                    .ndt-log { padding: 4px; border-bottom: 1px solid #21262d; word-break: break-all; }
                    .ndt-log.error { color: #ff7b72; background: rgba(255,123,114,0.1); }
                    .ndt-log.warn { color: #d29922; background: rgba(210,153,34,0.1); }
                    .ndt-log.info { color: #58a6ff; }
                    .ndt-input-bar { display: flex; border-top: 1px solid #30363d; }
                    .ndt-input { flex: 1; background: #0d1117; border: none; color: #4af626; padding: 8px; outline: none; font-family: monospace; }
                </style>
                <div class="ndt-header">
                    <div class="ndt-tab active" data-tab="elements">Elements</div>
                    <div class="ndt-tab" data-tab="console">Console</div>
                    <div class="ndt-tab" data-tab="network">Network</div>
                    <div class="ndt-tab" data-tab="storage">Storage</div>
                    <div class="ndt-tab" data-tab="scraper">Scraper</div>
                    <div class="ndt-actions">
                        <button class="ndt-btn" id="ndt-btn-min">_</button>
                        <button class="ndt-btn" id="ndt-btn-close">✕</button>
                    </div>
                </div>
                <div class="ndt-body" id="ndt-body-content"></div>
                <div class="ndt-input-bar" id="ndt-console-bar" style="display:none;">
                    <span style="color:#00f2fe; padding:8px 0 8px 8px;">&gt;</span>
                    <input class="ndt-input" id="ndt-eval-input" placeholder="Execute JavaScript..." />
                </div>
            `;
            document.body.appendChild(container);
            this.renderElements();
        },

        bindEvents() {
            const self = this;
            document.querySelectorAll('.ndt-tab').forEach(tab => {
                tab.addEventListener('click', (e) => {
                    document.querySelectorAll('.ndt-tab').forEach(t => t.classList.remove('active'));
                    e.target.classList.add('active');
                    self.activeTab = e.target.getAttribute('data-tab');
                    document.getElementById('ndt-console-bar').style.display = self.activeTab === 'console' ? 'flex' : 'none';
                    self.render();
                });
            });

            document.getElementById('ndt-btn-close').addEventListener('click', () => {
                document.getElementById('native-devtools-root').remove();
                window.__NATIVE_DEVTOOLS_INSTALLED__ = false;
            });

            document.getElementById('ndt-btn-min').addEventListener('click', () => {
                const root = document.getElementById('native-devtools-root');
                root.style.height = root.style.height === '35px' ? '55vh' : '35px';
            });

            document.getElementById('ndt-eval-input').addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    const val = e.target.value;
                    if (!val) return;
                    self.consoleLogs.push({ type: 'info', time: new Date().toLocaleTimeString(), message: '> ' + val });
                    try {
                        const res = eval(val);
                        self.consoleLogs.push({ type: 'log', time: new Date().toLocaleTimeString(), message: String(res) });
                    } catch(err) {
                        self.consoleLogs.push({ type: 'error', time: new Date().toLocaleTimeString(), message: err.message });
                    }
                    e.target.value = '';
                    self.renderConsole();
                }
            });
        },

        render() {
            if (this.activeTab === 'elements') this.renderElements();
            else if (this.activeTab === 'console') this.renderConsole();
            else if (this.activeTab === 'network') this.renderNetwork();
            else if (this.activeTab === 'storage') this.renderStorage();
            else if (this.activeTab === 'scraper') this.renderScraper();
        },

        renderElements() {
            const body = document.getElementById('ndt-body-content');
            body.innerHTML = `
                <div style="margin-bottom:100px;">
                    <h4 style="color:#00f2fe; margin-top:0;">🌳 Live DOM Explorer</h4>
                    <pre style="white-space:pre-wrap; word-break:break-all; color:#7ee787;">${this.escapeHTML(document.documentElement.outerHTML.substring(0, 8000))}</pre>
                </div>
            `;
        },

        renderConsole() {
            const body = document.getElementById('ndt-body-content');
            body.innerHTML = this.consoleLogs.map(l => `
                <div class="ndt-log ${l.type}">
                    <span style="color:#8b949e;">[${l.time}]</span> <b>${l.type.toUpperCase()}:</b> ${this.escapeHTML(l.message)}
                </div>
            `).join('') || '<div style="color:#8b949e;">Console is empty.</div>';
            body.scrollTop = body.scrollHeight;
        },

        renderNetwork() {
            const body = document.getElementById('ndt-body-content');
            body.innerHTML = `
                <table style="width:100%; border-collapse:collapse;">
                    <tr style="color:#00f2fe; text-align:left; border-bottom:1px solid #30363d;">
                        <th>Method</th><th>URL</th><th>Status</th><th>Time</th>
                    </tr>
                    ${this.networkLogs.map(n => `
                        <tr style="border-bottom:1px solid #21262d;">
                            <td style="color:#79c0ff;">${n.method}</td>
                            <td style="max-width:150px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${n.url}</td>
                            <td style="color:${n.status == 200 ? '#56d364' : '#ff7b72'}">${n.status}</td>
                            <td style="color:#8b949e;">${n.duration}</td>
                        </tr>
                    `).join('')}
                </table>
            `;
        },

        renderStorage() {
            const body = document.getElementById('ndt-body-content');
            let cookies = document.cookie;
            let local = JSON.stringify(localStorage, null, 2);
            let session = JSON.stringify(sessionStorage, null, 2);
            body.innerHTML = `
                <h4 style="color:#00f2fe;">🍪 Document Cookies</h4>
                <div class="ndt-log">${cookies || 'No cookies'}</div>
                <h4 style="color:#00f2fe;">💾 Local Storage</h4>
                <pre class="ndt-log">${local}</pre>
                <h4 style="color:#00f2fe;">⏱️ Session Storage</h4>
                <pre class="ndt-log">${session}</pre>
            `;
        },

        renderScraper() {
            const body = document.getElementById('ndt-body-content');
            const links = Array.from(document.querySelectorAll('a')).map(a => a.href).filter(Boolean);
            const imgs = Array.from(document.querySelectorAll('img')).map(i => i.src).filter(Boolean);
            body.innerHTML = `
                <h4 style="color:#00f2fe;">🕷️ Quick Scrape Overview</h4>
                <div><b>Extracted Links (${links.length}):</b></div>
                <div style="max-height:100px; overflow:auto; background:#161b22; padding:5px;">
                    ${links.slice(0, 10).join('<br>')}
                </div>
                <br>
                <div><b>Extracted Media Images (${imgs.length}):</b></div>
                <div style="max-height:100px; overflow:auto; background:#161b22; padding:5px;">
                    ${imgs.slice(0, 10).join('<br>')}
                </div>
            `;
        },

        escapeHTML(str) {
            return str.replace(/[&<>'"]/g, 
                tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)
            );
        }
    };

    DevTools.init();
})();
