(function () {
    const ROOT_ID = "jobemailer-linkedin-chat-root";
    const WS_URL = "ws://localhost:8080/chat";

    if (document.getElementById(ROOT_ID)) {
        return;
    }

    let socket = null;
    let reconnectTimer = null;
    let minimized = false;

    const root = document.createElement("section");
    root.id = ROOT_ID;
    root.setAttribute("aria-label", "JobEmailer LinkedIn chat");
    root.innerHTML = [
        '<div class="je-header">',
        '  <div>',
        '    <strong>JobEmailer</strong>',
        '    <span id="je-status"><span class="je-dot"></span>Connecting</span>',
        "  </div>",
        '  <button id="je-toggle" type="button" aria-label="Minimize JobEmailer chat">-</button>',
        "</div>",
        '<div id="je-body" class="je-body">',
        '  <div id="je-messages" class="je-messages"></div>',
        '  <form id="je-form" class="je-form">',
        '    <textarea id="je-input" rows="3" placeholder="Paste a LinkedIn post URL"></textarea>',
        '    <button id="je-send" type="submit" disabled>Send</button>',
        "  </form>",
        "</div>",
        '<div class="je-minimized-icon" aria-label="Open JobEmailer chat"></div>'
    ].join("");

    document.documentElement.appendChild(root);

    const body = root.querySelector("#je-body");
    const messages = root.querySelector("#je-messages");
    const form = root.querySelector("#je-form");
    const input = root.querySelector("#je-input");
    const send = root.querySelector("#je-send");
    const status = root.querySelector("#je-status");
    const dot = root.querySelector(".je-dot");
    const toggle = root.querySelector("#je-toggle");
    const minimizedIcon = root.querySelector(".je-minimized-icon");

    function addMessage(kind, text) {
        const message = document.createElement("div");
        message.className = `je-message ${kind}`;
        message.textContent = text;
        messages.appendChild(message);
        messages.scrollTop = messages.scrollHeight;
    }

    function setConnected(connected) {
        status.lastChild.textContent = connected ? "Connected" : "Disconnected";
        dot.classList.toggle("online", connected);
        send.disabled = !connected;
    }

    function connect() {
        clearTimeout(reconnectTimer);
        socket = new WebSocket(WS_URL);

        socket.addEventListener("open", () => setConnected(true));
        socket.addEventListener("message", event => addMessage("bot", event.data));
        socket.addEventListener("close", () => {
            setConnected(false);
            reconnectTimer = setTimeout(connect, 2000);
        });
        socket.addEventListener("error", () => setConnected(false));
    }

    function sendText(text) {
        if (!text || !socket || socket.readyState !== WebSocket.OPEN) {
            return;
        }
        addMessage("user", text);
        socket.send(text);
        input.value = "";
        input.focus();
    }

    function toggleMinimize() {
        minimized = !minimized;
        if (minimized) {
            root.classList.add("minimized");
        } else {
            root.classList.remove("minimized");
        }
        toggle.textContent = minimized ? "+" : "-";
        toggle.setAttribute(
            "aria-label",
            minimized ? "Open JobEmailer chat" : "Minimize JobEmailer chat"
        );

        // Update icon aria-label
        minimizedIcon.setAttribute(
            "aria-label",
            minimized ? "Open JobEmailer chat" : "Minimize JobEmailer chat"
        );
    }

    form.addEventListener("submit", event => {
        event.preventDefault();
        sendText(input.value.trim());
    });

    input.addEventListener("keydown", event => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            form.requestSubmit();
        }
    });

    toggle.addEventListener("click", toggleMinimize);

    // Add click handler for the minimized icon
    minimizedIcon.addEventListener("click", toggleMinimize);

    addMessage("bot", "Paste a LinkedIn post URL here. I will send it to your local JobEmailer app.");
    connect();
})();