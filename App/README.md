# Antigravity Secure Chat

A premium, private chat application built with Java (Backend) and HTML/CSS/JS (Frontend).

## Features
- Real-time messaging (Server-Sent Events)
- Message Status (Sent/Seen)
- Beautiful Glassmorphism UI
- No external dependencies (Pure Java 17+)

## How to Run

1. **Compile Backend**:
   ```powershell
   mkdir bin
   javac -d bin src/ChatServer.java
   ```

2. **Start Server**:
   ```powershell
   java -cp bin ChatServer
   ```

3. **Access App**:
   Open [http://localhost:8080](http://localhost:8080) in your browser.
   To simulate two users, open the URL in two different browser windows (or Incognito mode).
