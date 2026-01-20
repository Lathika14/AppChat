const App = {
    username: null,
    eventSource: null,
    isTabActive: true,

    init() {
        // Bind UI Elements
        this.loginScreen = document.getElementById('login-screen');
        this.chatScreen = document.getElementById('chat-screen');
        this.joinBtn = document.getElementById('join-btn');
        this.usernameInput = document.getElementById('username-input');
        this.messageInput = document.getElementById('message-input');
        this.sendBtn = document.getElementById('send-btn');
        this.chatHistory = document.getElementById('chat-history');

        // Event Listeners
        this.joinBtn.addEventListener('click', () => this.join());
        this.usernameInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.join();
        });

        this.sendBtn.addEventListener('click', () => this.sendMessage());
        this.messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.sendMessage();
        });

        document.addEventListener('visibilitychange', () => {
            this.isTabActive = !document.hidden;
        });
    },

    join() {
        const name = this.usernameInput.value.trim();
        if (!name) return;

        // Validation for allowed names
        const allowedNames = ["Vicky", "Lathika"];
        if (!allowedNames.includes(name)) {
            alert("Access Denied: You are not authorized to enter.");
            return;
        }

        this.username = name;
        this.loginScreen.classList.remove('active-screen');
        this.chatScreen.classList.add('active-screen');

        // Setup Header
        document.getElementById('header-avatar').textContent = name.charAt(0).toUpperCase();
        // document.getElementById('header-username').textContent = `Logged in as ${name}`;

        // Add Date Divider
        this.addDateDivider();

        this.connect();
    },

    addDateDivider() {
        const dateContainer = document.createElement('div');
        dateContainer.className = 'date-divider';
        const dateSpan = document.createElement('span');

        const options = { year: 'numeric', month: 'long', day: 'numeric' };
        dateSpan.textContent = new Date().toLocaleDateString(undefined, options);

        dateContainer.appendChild(dateSpan);
        this.chatHistory.appendChild(dateContainer);
    },

    connect() {
        this.eventSource = new EventSource('/api/stream');

        this.eventSource.onopen = () => {
            document.querySelector('.connection-status').textContent = 'Online';
            document.querySelector('.connection-status').style.color = '#4ade80';
        };

        this.eventSource.onerror = () => {
            document.querySelector('.connection-status').textContent = 'Connecting...';
            document.querySelector('.connection-status').style.color = '#facc15';
        };

        // Listen for standard messages (though we use named events)
        this.eventSource.onmessage = (e) => {
            // usually heartbeat
        };

        this.eventSource.addEventListener('message', (e) => {
            try {
                console.log("Received message event:", e.data);
                const data = JSON.parse(e.data);
                if (data && data.content) {
                    this.addMessageToUI(data);
                } else {
                    console.warn("Received incomplete message data:", data);
                }

                // If it's not my message and I'm active, mark as seen
                if (data.user !== this.username && this.isTabActive) {
                    this.markAsSeen(data.id);
                }
            } catch (err) {
                console.error("Error parsing message event:", err);
            }
        });

        this.eventSource.addEventListener('status', (e) => {
            const data = JSON.parse(e.data);
            this.updateMessageStatus(data);
        });
    },

    async sendMessage() {
        const content = this.messageInput.value.trim();
        if (!content) return;

        this.messageInput.value = '';

        try {
            await fetch('/api/send', {
                method: 'POST',
                body: JSON.stringify({
                    user: this.username,
                    content: content
                })
            });
            // We don't add message here, we wait for SSE broadcast to avoid duplicate logic
            // But for better UX we might want optimistic UI.
            // For now, let's rely on the fast local SSE broadcast.
        } catch (err) {
            console.error('Failed to send', err);
        }
    },

    async markAsSeen(msgId) {
        try {
            await fetch('/api/status', {
                method: 'POST',
                body: JSON.stringify({
                    id: msgId,
                    status: 'Seen',
                    byUser: this.username
                })
            });
        } catch (err) {
            console.error(err);
        }
    },

    addMessageToUI(msg) {
        const isMe = msg.user === this.username;
        const row = document.createElement('div');
        row.className = `message-row ${isMe ? 'sent' : 'received'}`;
        row.id = `msg-${msg.id}`;

        const time = new Date(msg.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        row.innerHTML = `
            <div class="message-bubble">${this.escapeHtml(msg.content)}</div>
            <div class="message-meta">
                <span class="msg-time">${time}</span>
                ${isMe ? `<span class="status-icon">${msg.status}</span>` : ''}
            </div>
        `;

        this.chatHistory.appendChild(row);
        this.scrollToBottom();
    },

    updateMessageStatus(update) {
        // update.id, update.status
        const row = document.getElementById(`msg-${update.id}`);
        if (row && row.classList.contains('sent')) {
            const statusEl = row.querySelector('.status-icon');
            if (statusEl) {
                statusEl.textContent = update.status;
            }
        }
    },

    scrollToBottom() {
        this.chatHistory.scrollTop = this.chatHistory.scrollHeight;
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

App.init();
